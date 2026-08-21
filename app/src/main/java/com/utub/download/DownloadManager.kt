package com.utub.download

import android.os.StatFs
import com.utub.data.db.DownloadDao
import com.utub.data.db.DownloadEntity
import com.utub.data.repository.PlayerRepository
import com.utub.extractor.ExtractException
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * 오프라인 저장 (3차). 순차 큐(동시 1개 — 유튜브 요청 제한 고려) + 진행률 StateFlow.
 * 스트림 해석은 기존 PlayerRepository 공개 API만 사용 (재생 코어 무수정).
 * 실행은 DownloadService(Foreground)가 [processNext]를 큐 소진까지 반복 호출한다.
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
    )

    data class Progress(
        val request: Request,
        val bytesRead: Long,
        val totalBytes: Long, // -1 = 크기 미상
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val lock = Any()
    private val queue = ArrayDeque<Request>()

    /** 대기 중 목록 (진행 중 제외) — 보관함 표시용 */
    private val _pending = MutableStateFlow<List<Request>>(emptyList())
    val pending: StateFlow<List<Request>> = _pending.asStateFlow()

    /** 현재 진행 중 작업 (null = 유휴) */
    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    /** 마지막 실패 메시지 (UI 스낵바/보관함 표시용, 새 작업 시작 시 초기화) */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** @return null = 큐에 추가됨, 아니면 거부 사유 */
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

    /**
     * 큐에서 하나 꺼내 다운로드. 취소(코루틴 cancel)·실패 시 .part 잔재를 정리한다.
     * @return 처리했으면 true, 큐가 비어 있으면 false
     */
    suspend fun processNext(dir: File): Boolean {
        val request = synchronized(lock) {
            queue.removeFirstOrNull()?.also { _pending.value = queue.toList() }
        } ?: return false

        _lastError.value = null
        _progress.value = Progress(request, 0, -1)
        val part = File(dir, "${request.videoId}.part")
        try {
            downloadInternal(request, dir, part)
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete()
            throw e
        } catch (e: Throwable) {
            part.delete()
            _lastError.value = failMessage(request, e)
            android.util.Log.w("DownloadManager", "download failed: ${request.videoId}", e)
        } finally {
            _progress.value = null
        }
        return true
    }

    private suspend fun downloadInternal(request: Request, dir: File, part: File) {
        val streams = repository.resolve(request.videoId)
        if (streams.isLive) throw IOException("live") // 라이브는 저장 불가

        // 트랙 선택: 오디오 = 최고 비트레이트 / 영상 = muxed(영상+소리 단일 파일) 최고 화질.
        // 고화질(video-only+audio 병합)은 결함 위험이 커 1단계 제외 — 사용자 합의.
        val (url, mime) = if (request.audioOnly) {
            val a = streams.audioStreams.maxByOrNull { it.averageBitrate }
                ?: throw IOException("no audio stream")
            a.url to a.mimeType
        } else {
            val v = streams.muxedStreams.filter { it.heightPx > 0 }.maxByOrNull { it.heightPx }
                ?: throw IOException("no muxed stream")
            v.url to v.mimeType
        }

        // 시작 전 여유 공간 검사 — 부족한 채 받다가 실패해 잔재만 남는 상황 방지
        val estimated = estimateBytes(streams.durationMs.takeIf { it > 0 } ?: request.durationMs, request.audioOnly)
        if (StatFs(dir.path).availableBytes < estimated + SPACE_MARGIN_BYTES) {
            throw IOException("no space")
        }

        dir.mkdirs()
        val dest = File(dir, "${request.videoId}.${extensionFor(mime, request.audioOnly)}")
        val httpRequest = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", OkHttpDownloader.USER_AGENT)
            .build()
        withContext(Dispatchers.IO) {
            client.newCall(httpRequest).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("empty body")
                val total = body.contentLength()
                if (total > 0 && StatFs(dir.path).availableBytes < total + SPACE_MARGIN_BYTES) {
                    throw IOException("no space")
                }
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var sum = 0L
                        var lastEmit = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buf)
                            if (read == -1) break
                            out.write(buf, 0, read)
                            sum += read
                            // 진행률 갱신은 512KB 단위 — StateFlow 과도 발행 방지
                            if (sum - lastEmit >= 512 * 1024) {
                                lastEmit = sum
                                _progress.value = Progress(request, sum, total)
                            }
                        }
                    }
                }
            }
        }

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
                sizeBytes = dest.length(),
                completedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 완료본 삭제 — 파일 + DB 기록 동시 (용량 관리) */
    suspend fun delete(videoId: String) {
        downloadDao.get(videoId)?.let { File(it.filePath).delete() }
        downloadDao.delete(videoId)
    }

    suspend fun deleteAll() {
        downloadDao.getAll().forEach { File(it.filePath).delete() }
        downloadDao.clearAll()
    }

    /**
     * 자동 위생: .part 잔재 삭제 + 파일이 사라진 DB 기록 정리 (유령 항목 방지).
     * DownloadService 시작 시 호출.
     */
    suspend fun cleanUp(dir: File) {
        withContext(Dispatchers.IO) {
            dir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
        }
        downloadDao.getAll().forEach {
            if (!File(it.filePath).exists()) downloadDao.delete(it.videoId)
        }
    }

    private fun failMessage(request: Request, e: Throwable): String {
        val what = if (request.audioOnly) "오디오" else "영상"
        return when {
            e.message == "no space" -> "저장 공간이 부족해요 — \"${request.title}\" 저장 취소됨"
            e.message == "no muxed stream" -> "\"${request.title}\"은 단일 영상 파일이 제공되지 않아요 — 오디오로 저장해 보세요"
            e.message == "live" -> "라이브는 저장할 수 없어요"
            e is ExtractException || e is IOException -> "\"${request.title}\" $what 저장 실패 — 네트워크를 확인해 주세요"
            else -> "\"${request.title}\" $what 저장 실패"
        }
    }

    companion object {
        /** 저장 후에도 시스템 여유로 남겨둘 최소 공간 */
        private const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024

        /** 예상 크기 (사전 공간 검사·UI 표시 공용): 오디오 128kbps ≈ 17KB/s, 360p muxed ≈ 100KB/s */
        fun estimateBytes(durationMs: Long, audioOnly: Boolean): Long =
            (durationMs / 1000) * if (audioOnly) 17_000L else 100_000L

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
