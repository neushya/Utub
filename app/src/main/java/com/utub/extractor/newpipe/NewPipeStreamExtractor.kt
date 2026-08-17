package com.utub.extractor.newpipe

import com.utub.extractor.AudioTrack
import com.utub.extractor.ExtractCache
import com.utub.extractor.ExtractException
import com.utub.extractor.RateLimiter
import com.utub.extractor.ResolvedStreams
import com.utub.extractor.StreamExtractor
import com.utub.extractor.VideoSummary
import com.utub.extractor.VideoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NewPipeExtractor 기반 구현체. StreamExtractor 인터페이스 뒤에 격리 (docs/04 N-03).
 * 유튜브 규격 변경 시 이 패키지(newpipe/)만 교체·업데이트하면 된다.
 */
class NewPipeStreamExtractor(
    private val downloader: OkHttpDownloader = OkHttpDownloader(),
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val cache: ExtractCache = ExtractCache(),
) : StreamExtractor {

    private val initialized = AtomicBoolean(false)

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(downloader)
        }
    }

    override suspend fun resolveStreams(videoId: String): ResolvedStreams {
        cache.get(videoId)?.let { return it }
        return withContext(Dispatchers.IO) {
            ensureInit()
            val info = rateLimiter.execute {
                mapErrors { StreamInfo.getInfo(ServiceList.YouTube, watchUrl(videoId)) }
            }
            if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
                throw ExtractException.Unavailable(ExtractException.Unavailable.Reason.LIVE_UNSUPPORTED)
            }
            val resolved = ResolvedStreams(
                videoId = videoId,
                title = info.name.orEmpty(),
                channelName = info.uploaderName.orEmpty(),
                thumbnailUrl = info.thumbnails.maxByOrNull { it.width }?.url,
                durationMs = info.duration * 1000,
                muxedStreams = info.videoStreams.orEmpty().mapNotNull { vs ->
                    vs.content?.let {
                        VideoTrack(it, vs.format?.mimeType, parseHeight(vs.resolution), isVideoOnly = false)
                    }
                },
                videoOnlyStreams = info.videoOnlyStreams.orEmpty().mapNotNull { vs ->
                    vs.content?.let {
                        VideoTrack(it, vs.format?.mimeType, parseHeight(vs.resolution), isVideoOnly = true)
                    }
                },
                audioStreams = info.audioStreams.orEmpty().mapNotNull { asr ->
                    asr.content?.let { AudioTrack(it, asr.format?.mimeType, asr.averageBitrate) }
                },
                related = info.relatedItems.orEmpty().filterIsInstance<StreamInfoItem>().map { it.toSummary() },
            )
            cache.put(videoId, resolved)
            resolved
        }
    }

    override suspend fun search(query: String): List<VideoSummary> = withContext(Dispatchers.IO) {
        ensureInit()
        val info = rateLimiter.execute {
            mapErrors {
                SearchInfo.getInfo(
                    ServiceList.YouTube,
                    ServiceList.YouTube.searchQHFactory.fromQuery(query),
                )
            }
        }
        info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toSummary() }
    }

    override suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        ensureInit()
        rateLimiter.execute {
            mapErrors { ServiceList.YouTube.suggestionExtractor.suggestionList(query) }
        }
    }

    private inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: ReCaptchaException) {
        throw ExtractException.RateLimited(e)
    } catch (e: PaidContentException) {
        throw ExtractException.Unavailable(ExtractException.Unavailable.Reason.PAID, e)
    } catch (e: PrivateContentException) {
        throw ExtractException.Unavailable(ExtractException.Unavailable.Reason.PRIVATE, e)
    } catch (e: AgeRestrictedContentException) {
        throw ExtractException.Unavailable(ExtractException.Unavailable.Reason.AGE_RESTRICTED, e)
    } catch (e: GeographicRestrictionException) {
        throw ExtractException.Unavailable(ExtractException.Unavailable.Reason.GEO_BLOCKED, e)
    } catch (e: ContentNotAvailableException) {
        throw ExtractException.Unavailable(ExtractException.Unavailable.Reason.DELETED, e)
    } catch (e: ExtractionException) {
        throw ExtractException.Parse(e)
    } catch (e: IOException) {
        throw ExtractException.Network(e)
    }

    private fun StreamInfoItem.toSummary() = VideoSummary(
        videoId = extractVideoId(url),
        title = name.orEmpty(),
        channelName = uploaderName.orEmpty(),
        thumbnailUrl = thumbnails.maxByOrNull { it.width }?.url,
        durationMs = duration * 1000,
        viewCount = viewCount.takeIf { it >= 0 },
        uploadedText = textualUploadDate,
    )

    companion object {
        fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

        fun extractVideoId(url: String?): String =
            url?.substringAfter("v=", "")?.substringBefore('&')
                ?.ifEmpty { url.substringAfterLast('/') } ?: ""

        fun parseHeight(resolution: String?): Int =
            resolution?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    }
}
