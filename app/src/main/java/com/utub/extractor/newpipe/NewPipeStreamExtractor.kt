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
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
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

    @Volatile
    private var overrideCountry: String? = null

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) {
            applyLocalization()
        }
    }

    /** 설정의 콘텐츠 국가 반영 (AUTO/null = 기기 로캘) — 통합테스트 피드백 반영 */
    override fun setContentCountry(countryCode: String?) {
        val normalized = countryCode?.takeIf { it.isNotBlank() && it != "AUTO" }
        if (normalized == overrideCountry && initialized.get()) return
        overrideCountry = normalized
        if (initialized.get()) {
            applyLocalization()
            cache.clear() // 국가가 바뀌면 연관 영상 등도 달라지므로 초기화
        }
    }

    private fun applyLocalization() {
        val locale = java.util.Locale.getDefault()
        val country = overrideCountry ?: locale.country.ifEmpty { "KR" }
        NewPipe.init(
            downloader,
            Localization(locale.language.ifEmpty { "ko" }, country),
            ContentCountry(country),
        )
    }

    override suspend fun resolveStreams(videoId: String): ResolvedStreams {
        cache.get(videoId)?.let { return it }
        return withContext(Dispatchers.IO) {
            ensureInit()
            val info = rateLimiter.execute {
                mapErrors { StreamInfo.getInfo(ServiceList.YouTube, watchUrl(videoId)) }
            }
            // 라이브 스트림: HLS 매니페스트로 재생 (2차 — 사용자 최우선 요청으로 지원 전환).
            // 매니페스트 URL이 없을 때만 기존처럼 미지원 안내.
            val isLive = info.streamType == StreamType.LIVE_STREAM ||
                info.streamType == StreamType.AUDIO_LIVE_STREAM
            val liveUrl = if (isLive) {
                info.hlsUrl?.takeIf { it.isNotBlank() } ?: info.dashMpdUrl?.takeIf { it.isNotBlank() }
                    ?: throw ExtractException.Unavailable(ExtractException.Unavailable.Reason.LIVE_UNSUPPORTED)
            } else null
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
                isLive = isLive,
                liveUrl = liveUrl,
                subtitles = extractSubtitles(info),
                uploaderAvatarUrl = runCatching { info.uploaderAvatars.maxByOrNull { it.width }?.url }.getOrNull(),
                uploaderUrl = info.uploaderUrl?.takeIf { it.isNotBlank() },
                subscriberCount = runCatching { info.uploaderSubscriberCount }.getOrDefault(-1),
                viewCount = info.viewCount,
                likeCount = info.likeCount,
                description = runCatching { info.description?.content?.takeIf { it.isNotBlank() } }.getOrNull(),
            )
            cache.put(videoId, resolved)
            resolved
        }
    }

    /** 댓글 (5차-C) — CommentsInfo 경유, 보기 전용. 비활성 영상은 disabled 플래그 */
    override suspend fun comments(videoId: String, page: Any?): com.utub.extractor.CommentsPage =
        withContext(Dispatchers.IO) {
            ensureInit()
            rateLimiter.execute {
                mapErrors {
                    val url = watchUrl(videoId)
                    val items: List<org.schabi.newpipe.extractor.comments.CommentsInfoItem>
                    val next: org.schabi.newpipe.extractor.Page?
                    if (page == null) {
                        val ci = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(ServiceList.YouTube, url)
                        if (ci.isCommentsDisabled) {
                            return@mapErrors com.utub.extractor.CommentsPage(emptyList(), null, disabled = true)
                        }
                        items = ci.relatedItems
                        next = ci.nextPage
                    } else {
                        val more = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(
                            ServiceList.YouTube, url, page as org.schabi.newpipe.extractor.Page,
                        )
                        items = more.items
                        next = more.nextPage
                    }
                    com.utub.extractor.CommentsPage(
                        comments = items.map { c ->
                            com.utub.extractor.CommentData(
                                author = c.uploaderName.orEmpty(),
                                avatarUrl = runCatching { c.uploaderAvatars.maxByOrNull { it.width }?.url }.getOrNull(),
                                text = cleanHtml(c.commentText?.content.orEmpty()),
                                likeCount = c.likeCount,
                                publishedText = c.textualUploadDate,
                                isPinned = c.isPinned,
                            )
                        },
                        nextPage = next,
                    )
                }
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

    override suspend fun trending(): List<VideoSummary> = withContext(Dispatchers.IO) {
        ensureInit()
        rateLimiter.execute {
            mapErrors {
                val kiosk = ServiceList.YouTube.kioskList.defaultKioskExtractor
                kiosk.fetchPage()
                kiosk.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toSummary() }
            }
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

    /** 댓글 텍스트의 HTML 잔재 정리 — 태그 제거 + 엔티티 디코드 (5차-C) */
    private fun cleanHtml(raw: String): String = raw
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()

    /** 자막: 언어당 1개 — 일반 자막 우선, 없으면 자동 생성 (CC, 2차 이관분) */
    private fun extractSubtitles(info: StreamInfo): List<com.utub.extractor.SubtitleTrack> =
        info.subtitles.orEmpty()
            .mapNotNull { s ->
                val url = s.content?.takeIf { s.isUrl && it.isNotBlank() } ?: return@mapNotNull null
                val tag = s.languageTag?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                com.utub.extractor.SubtitleTrack(
                    url = url,
                    mimeType = s.format?.mimeType,
                    languageTag = tag,
                    displayName = s.displayLanguageName ?: tag,
                    isAutoGenerated = s.isAutoGenerated,
                )
            }
            .groupBy { it.languageTag }
            .map { (_, tracks) -> tracks.firstOrNull { !it.isAutoGenerated } ?: tracks.first() }
            .sortedBy { it.languageTag != "ko" } // 한국어 우선 정렬
    // 참고: "한국어 (자동 번역)"(timedtext &tlang=ko)을 시도했으나 유튜브가 tlang 요청을
    // 봇 차단(429 Sorry 페이지)으로 거부함을 실측 확인(2026-08-21, 원본 자막 URL은 동시점 200).
    // 오동작 항목을 목록에 남기지 않기 위해 제거 — 서버 정책 변화 시 재시도 여지.

    private fun StreamInfoItem.toSummary() = VideoSummary(
        videoId = extractVideoId(url),
        title = name.orEmpty(),
        channelName = uploaderName.orEmpty(),
        thumbnailUrl = thumbnails.maxByOrNull { it.width }?.url,
        durationMs = duration * 1000,
        viewCount = viewCount.takeIf { it >= 0 },
        uploadedText = textualUploadDate,
        uploaderAvatarUrl = runCatching { uploaderAvatars.maxByOrNull { it.width }?.url }.getOrNull(),
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
