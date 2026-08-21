package com.utub.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.StatFs
import com.utub.data.db.DownloadDao
import com.utub.data.db.DownloadEntity
import com.utub.data.repository.PlayerRepository
import com.utub.extractor.ExtractException
import com.utub.extractor.ResolvedStreams
import com.utub.extractor.newpipe.OkHttpDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * 오프라인 저장 (3차) + 고화질 병합 (4차). 순차 큐(동시 1개) + 진행률 StateFlow.
 * 스트림 해석은 기존 PlayerRepository 공개 API만 사용 (재생 코어 무수정).
 *
 * 고화질(720p+): 유튜브는 고화질을 영상·소리 분리 스트림으로만 제공 →
 * 각각 받아 MediaMuxer로 재인코딩 없이 컨테이너 병합(초 단위, 배터리 부담 없음).
 * 병합 불가 코덱(AV1 등 기기 미지원)이면 안내 후 실패 — 기본 화질 권유 (안전한 실패).
 */
@Singleton
class DownloadManager @Inject constructor(
    private val repository: PlayerRepository,
    private val downloadDao: DownloadDao,
) {
    data class Request(
        val videoId: String,
        val title: String,
        val channelName: String,
        val thumbnailUrl: String?,
        val durationMs: Long,
        val audioOnly: Boolean,
        /** 0 = 기본(muxed 최고, 보통 360p) / 720·1080 = 고화질 병합 */
        val targetHeight: Int = 0,
    )

    data class Progress(
        val request: Request,
        val bytesRead: Long,
        val totalBytes: Long, // -1 = 크기 미상
        /** 고화질 병합 단계 표시용 */
        val merging: Boolean = false,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val lock = Any()
    private val queue = ArrayDeque<Request>()

    private val _pending = MutableStateFlow<List<Request>>(emptyList())
    val pending: StateFlow<List<Request>> = _pending.asStateFlow()

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun enqueue(request: Request): String? {
        if (downloadDao.get(request.videoId) != null) return "이미 저장된 영상이에요"
        synchronized(lock) {
            val active = _progress.value?.request?.videoId
            if (active == request.videoId || queue.any { it.videoId == request.videoId }) {
                return "이미 다운로드 중이에요"
            }
            queue.addLast(request)
            _pending.value = queue.toList()
        }
        return null
    }

    fun clearQueue() {
        synchronized(lock) {
            queue.clear()
            _pending.value = emptyList()
        }
    }

    fun hasWork(): Boolean = synchronized(lock) { queue.isNotEmpty() }

    suspend fun processNext(dir: File): Boolean {
        val request = synchronized(lock) {
            queue.removeFirstOrNull()?.also { _pending.value = queue.toList() }
        } ?: return false

        _lastError.value = null
        _progress.value = Progress(request, 0, -1)
        try {
            downloadInternal(request, dir)
        } catch (e: kotlinx.coroutines.CancellationException) {
            cleanParts(dir, request.videoId)
            throw e
        } catch (e: Throwable) {
            cleanParts(dir, request.videoId)
            _lastError.value = failMessage(request, e)
            android.util.Log.w("DownloadManager", "download failed: ${request.videoId}", e)
        } finally {
            _progress.value = null
        }
        return true
    }

    private suspend fun downloadInternal(request: Request, dir: File) {
        val streams = repository.resolve(request.videoId)
        if (streams.isLive) throw IOException("live")
        dir.mkdirs()
        checkSpace(dir, estimateBytes(streams.durationMs.takeIf { it > 0 } ?: request.durationMs, request.audioOnly, request.targetHeight))

        when {
            request.audioOnly -> downloadAudio(request, streams, dir)
            request.targetHeight > 480 -> downloadMerged(request, streams, dir)
            else -> downloadMuxed(request, streams, dir)
        }
    }

    /** 오디오 전용 (기존 경로) */
    private suspend fun downloadAudio(request: Request, streams: ResolvedStreams, dir: File) {
        val a = streams.audioStreams.maxByOrNull { it.averageBitrate } ?: throw IOException("no audio stream")
        val part = File(dir, "${request.videoId}.part")
        val dest = File(dir, "${request.videoId}.${extensionFor(a.mimeType, audioOnly = true)}")
        fetch(a.url, part) { read, total -> _progress.value = Progress(request, read, total) }
        finish(request, part, dest, streams, heightPx = 0)
    }

    /** 기본 화질 — muxed 단일 파일 (기존 경로) */
    private suspend fun downloadMuxed(request: Request, streams: ResolvedStreams, dir: File) {
        val v = streams.muxedStreams.filter { it.heightPx > 0 }.maxByOrNull { it.heightPx }
            ?: throw IOException("no muxed stream")
        val part = File(dir, "${request.videoId}.part")
        val dest = File(dir, "${request.videoId}.${extensionFor(v.mimeType, audioOnly = false)}")
        fetch(v.url, part) { read, total -> _progress.value = Progress(request, read, total) }
        finish(request, part, dest, streams, heightPx = v.heightPx)
    }

    /** 고화질 — 영상·소리 분리 수신 후 MediaMuxer 병합 (4차) */
    private suspend fun downloadMerged(request: Request, streams: ResolvedStreams, dir: File) {
        // mp4 컨테이너(H.264 계열)만 후보 — MediaMuxer mp4 출력과 호환. webm(VP9)은 제외
        val v = streams.videoOnlyStreams
            .filter { it.heightPx in 1..request.targetHeight && it.mimeType?.contains("mp4") == true }
            .maxByOrNull { it.heightPx }
            ?: throw IOException("no hq stream")
        val a = streams.audioStreams
            .filter { it.mimeType?.contains("mp4") == true || it.mimeType?.contains("m4a") == true }
            .maxByOrNull { it.averageBitrate }
            ?: streams.audioStreams.maxByOrNull { it.averageBitrate }
            ?: throw IOException("no audio stream")

        val videoPart = File(dir, "${request.videoId}.video.part")
        val audioPart = File(dir, "${request.videoId}.audio.part")
        val mergedPart = File(dir, "${request.videoId}.part")
        val dest = File(dir, "${request.videoId}.mp4")
        try {
            var videoTotal = -1L
            fetch(v.url, videoPart) { read, total ->
                videoTotal = total
                _progress.value = Progress(request, read, if (total > 0) total * 5 / 4 else -1) // 오디오 몫 대략 가산
            }
            val videoBytes = videoPart.length()
            fetch(a.url, audioPart) { read, total ->
                val grand = if (videoTotal > 0 && total > 0) videoTotal + total else -1
                _progress.value = Progress(request, videoBytes + read, grand)
            }
            _progress.value = Progress(request, videoBytes + audioPart.length(), -1, merging = true)
            withContext(Dispatchers.IO) { muxToMp4(videoPart, audioPart, mergedPart) }
            finish(request, mergedPart, dest, streams, heightPx = v.heightPx)
        } finally {
            videoPart.delete()
            audioPart.delete()
        }
    }

    /** .part → 최종 파일 + DB 기록 (공통 마무리) */
    private suspend fun finish(request: Request, part: File, dest: File, streams: ResolvedStreams, heightPx: Int) {
        if (!part.renameTo(dest)) throw IOException("rename failed")
        downloadDao.upsert(
            DownloadEntity(
                videoId = request.videoId,
                title = streams.title.ifBlank { request.title },
                channelName = streams.channelName.ifBlank { request.channelName },
                thumbnailUrl = streams.thumbnailUrl ?: request.thumbnailUrl,
                durationMs = streams.durationMs.takeIf { it > 0 } ?: request.durationMs,
                filePath = dest.absolutePath,
                isAudioOnly = request.audioOnly,
                heightPx = heightPx,
                sizeBytes = dest.length(),
                completedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 단일 URL → 파일 (취소 가능, 512KB 단위 진행률) */
    private suspend fun fetch(url: String, dest: File, onBytes: (Long, Long) -> Unit) {
        withContext(Dispatchers.IO) {
            val httpRequest = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", OkHttpDownloader.USER_AGENT)
                .build()
            client.newCall(httpRequest).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("empty body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var sum = 0L
                        var lastEmit = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buf)
                            if (read == -1) break
                            out.write(buf, 0, read)
                            sum += read
                            if (sum - lastEmit >= 512 * 1024) {
                                lastEmit = sum
                                onBytes(sum, total)
                            }
                        }
                        onBytes(sum, total)
                    }
                }
            }
        }
    }

    /**
     * 영상·소리 파일 → mp4 컨테이너 병합 (재인코딩 없음 — 샘플 복사).
     * 기기 미지원 코덱이면 addTrack에서 예외 → 상위에서 "기본 화질 권유" 안내.
     */
    private fun muxToMp4(videoFile: File, audioFile: File, dest: File) {
        val muxer = MediaMuxer(dest.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vx = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
        val ax = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
        try {
            fun firstTrack(x: MediaExtractor, prefix: String): Pair<Int, MediaFormat> {
                for (i in 0 until x.trackCount) {
                    val f = x.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith(prefix)) return i to f
                }
                throw IOException("no $prefix track")
            }

            val (vIdx, vFmt) = firstTrack(vx, "video/")
            val (aIdx, aFmt) = firstTrack(ax, "audio/")
            vx.selectTrack(vIdx)
            ax.selectTrack(aIdx)
            val vOut = try {
                muxer.addTrack(vFmt)
            } catch (e: Exception) {
                throw IOException("unsupported codec", e)
            }
            val aOut = muxer.addTrack(aFmt)
            muxer.start()

            val buf = ByteBuffer.allocateDirect(1 shl 20)
            val info = MediaCodec.BufferInfo()
            fun copy(x: MediaExtractor, outIdx: Int) {
                while (true) {
                    val size = x.readSampleData(buf, 0)
                    if (size < 0) break
                    info.set(
                        0, size, x.sampleTime,
                        if (x.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
                    )
                    muxer.writeSampleData(outIdx, buf, info)
                    x.advance()
                }
            }
            copy(vx, vOut)
            copy(ax, aOut)
            muxer.stop()
        } finally {
            vx.release()
            ax.release()
            muxer.release()
        }
    }

    private fun checkSpace(dir: File, estimated: Long) {
        if (StatFs(dir.path).availableBytes < estimated + SPACE_MARGIN_BYTES) {
            throw IOException("no space")
        }
    }

    suspend fun delete(videoId: String) {
        downloadDao.get(videoId)?.let { File(it.filePath).delete() }
        downloadDao.delete(videoId)
    }

    suspend fun deleteAll() {
        downloadDao.getAll().forEach { File(it.filePath).delete() }
        downloadDao.clearAll()
    }

    /** 자동 위생: .part 잔재 삭제 + 파일이 사라진 DB 기록 정리 */
    suspend fun cleanUp(dir: File) {
        withContext(Dispatchers.IO) {
            dir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
        }
        downloadDao.getAll().forEach {
            if (!File(it.filePath).exists()) downloadDao.delete(it.videoId)
        }
    }

    private fun cleanParts(dir: File, videoId: String) {
        File(dir, "$videoId.part").delete()
        File(dir, "$videoId.video.part").delete()
        File(dir, "$videoId.audio.part").delete()
    }

    private fun failMessage(request: Request, e: Throwable): String {
        val what = if (request.audioOnly) "오디오" else "영상"
        return when {
            e.message == "no space" -> "저장 공간이 부족해요 — \"${request.title}\" 저장 취소됨"
            e.message == "no muxed stream" -> "\"${request.title}\"은 단일 영상 파일이 제공되지 않아요 — 오디오로 저장해 보세요"
            e.message == "no hq stream" -> "\"${request.title}\"은 ${request.targetHeight}p 저장이 지원되지 않아요 — 기본 화질로 저장해 보세요"
            e.message == "unsupported codec" || e.cause?.message == "unsupported codec" ->
                "\"${request.title}\"의 고화질은 이 기기에서 병합할 수 없는 형식이에요 — 기본 화질로 저장해 보세요"
            e.message == "live" -> "라이브는 저장할 수 없어요"
            e is ExtractException || e is IOException -> "\"${request.title}\" $what 저장 실패 — 네트워크를 확인해 주세요"
            else -> "\"${request.title}\" $what 저장 실패"
        }
    }

    companion object {
        private const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024

        /** 예상 크기: 오디오 17KB/s · 360p 100KB/s · 720p 220KB/s · 1080p 380KB/s */
        fun estimateBytes(durationMs: Long, audioOnly: Boolean, targetHeight: Int = 0): Long {
            val perSec = when {
                audioOnly -> 17_000L
                targetHeight >= 1080 -> 380_000L
                targetHeight >= 720 -> 220_000L
                else -> 100_000L
            }
            return (durationMs / 1000) * perSec
        }

        fun extensionFor(mimeType: String?, audioOnly: Boolean): String = when {
            mimeType?.contains("webm") == true -> "webm"
            audioOnly -> "m4a"
            else -> "mp4"
        }

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1_073_741_824 -> "%.1fGB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.0fMB".format(bytes / 1_048_576.0)
            else -> "%.0fKB".format(bytes / 1024.0)
        }
    }
}
