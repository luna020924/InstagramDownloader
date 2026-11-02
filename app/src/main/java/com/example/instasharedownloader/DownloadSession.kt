package com.example.instasharedownloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal enum class DownloadPhase { IDLE, RESOLVING, RENDERING, STORY_SCOPE, SELECTING, SAVING, DONE, ERROR }

internal data class DownloadState(
    val phase: DownloadPhase = DownloadPhase.IDLE,
    val sourceUrl: String = "",
    val sourceName: String = "",
    val items: List<MediaItem> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val savedCount: Int = 0,
    val totalCount: Int = 0,
    val lastUri: Uri? = null,
    val lastMime: String? = null,
    val message: String = "",
    val needsLogin: Boolean = false,
    val retrySaving: Boolean = false,
    val requestId: Long = 0,
) {
    val busy: Boolean get() = phase in setOf(DownloadPhase.RESOLVING, DownloadPhase.RENDERING, DownloadPhase.SAVING)
}

/** Process-owned work survives activity recreation, cover/main transitions, and a dismissed sheet. */
internal object DownloadSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(DownloadState())
    val state = mutableState.asStateFlow()
    private var appContext: Context? = null
    private var sequence = 0L

    fun resolve(context: Context, raw: String) {
        if (mutableState.value.busy) return
        val url = MediaExtractor.extractSupportedUrl(raw)
        if (url == null) {
            mutableState.value = DownloadState(phase = DownloadPhase.ERROR,
                message = "Instagram 또는 X의 게시물 링크를 확인해 주세요.")
            return
        }
        appContext = context.applicationContext
        val requestId = ++sequence
        mutableState.value = DownloadState(phase = DownloadPhase.RESOLVING, sourceUrl = url,
            sourceName = if (MediaExtractor.isTwitterUrl(url)) "X" else "Instagram", requestId = requestId)
        keepAlive()
        InstagramExtractor.updateSessionCookie(CookieManager.getInstance().getCookie("https://www.instagram.com/"))
        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) { MediaExtractor.extract(url) }
                acceptItems(items, requestId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error is IOException && MediaExtractor.shouldRenderWithInstagramWebView(error, url)) {
                    mutableState.value = mutableState.value.copy(phase = DownloadPhase.RENDERING)
                    scope.launch {
                        delay(26000)
                        acceptRendered(emptyList(), requestId)
                    }
                } else {
                    fail(error)
                }
            }
        }
    }

    fun acceptRendered(items: List<MediaItem>, requestId: Long) {
        if (mutableState.value.phase != DownloadPhase.RENDERING || mutableState.value.requestId != requestId) return
        if (items.isEmpty()) {
            mutableState.value = mutableState.value.copy(phase = DownloadPhase.ERROR,
                message = "항목을 불러오지 못했어요. 공개 범위와 Instagram 로그인 상태를 확인해 주세요.",
                needsLogin = !hasInstagramSession())
        } else acceptItems(items, requestId)
    }

    private fun acceptItems(items: List<MediaItem>, requestId: Long) {
        if (mutableState.value.requestId != requestId) return
        if (items.isEmpty()) {
            mutableState.value = mutableState.value.copy(phase = DownloadPhase.ERROR,
                message = "저장할 사진이나 동영상이 없어요. 링크가 만료되지 않았는지 확인해 주세요.")
            return
        }
        val before = mutableState.value
        mutableState.value = before.copy(items = items, selected = items.indices.toSet(),
            phase = if (MediaExtractor.shouldAskStoryScope(before.sourceUrl, items)) DownloadPhase.STORY_SCOPE
                else DownloadPhase.SELECTING)
        if (!MediaExtractor.shouldAskStoryScope(before.sourceUrl, items) && items.size == 1) save()
    }

    fun chooseSharedStory() {
        val current = mutableState.value
        val shared = current.items.indices.filter { current.items[it].sharedStory }.toSet()
        mutableState.value = current.copy(phase = DownloadPhase.SELECTING,
            selected = shared.ifEmpty { current.items.indices.toSet() })
        save()
    }

    fun chooseAllStories() {
        mutableState.value = mutableState.value.copy(phase = DownloadPhase.SELECTING)
    }

    fun toggle(index: Int) {
        val current = mutableState.value
        if (current.phase != DownloadPhase.SELECTING || index !in current.items.indices) return
        mutableState.value = current.copy(selected = current.selected.toMutableSet().apply {
            if (!add(index)) remove(index)
        })
    }

    fun selectAll() {
        val current = mutableState.value
        if (current.phase != DownloadPhase.SELECTING) return
        mutableState.value = current.copy(selected = if (current.selected.size == current.items.size) emptySet()
            else current.items.indices.toSet())
    }

    fun save() {
        val current = mutableState.value
        if (current.busy || current.selected.isEmpty()) return
        val context = appContext ?: return
        val plan = current.selected.sorted()
        mutableState.value = current.copy(phase = DownloadPhase.SAVING,
            totalCount = current.savedCount + plan.size, retrySaving = true, message = "")
        keepAlive()
        scope.launch {
            try {
                for (index in plan) {
                    val item = current.items[index]
                    val saved = withContext(Dispatchers.IO) {
                        MediaStoreSaver.downloadResult(context, item,
                            mutableState.value.savedCount + 1, mutableState.value.totalCount) { }
                    }
                    mutableState.value = mutableState.value.copy(savedCount = mutableState.value.savedCount + 1,
                        selected = mutableState.value.selected - index, lastUri = saved.uri,
                        lastMime = if (item.kind == MediaItem.Kind.VIDEO) "video/*" else "image/*")
                }
                mutableState.value = mutableState.value.copy(phase = DownloadPhase.DONE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) { fail(error) }
        }
    }

    fun retry(context: Context) {
        val current = mutableState.value
        if (current.retrySaving && current.selected.isNotEmpty()) save()
        else resolve(context, current.sourceUrl)
    }

    fun reset() {
        if (!mutableState.value.busy) mutableState.value = DownloadState()
    }

    fun hasInstagramSession(): Boolean = CookieManager.getInstance()
        .getCookie("https://www.instagram.com/")?.contains("sessionid=") == true

    private fun fail(error: Exception) {
        val raw = error.message.orEmpty()
        val login = raw.contains("login is required", true) || raw.contains("HTTP 401")
        val message = when {
            login -> "Instagram에 로그인한 뒤 다시 시도해 주세요."
            raw.contains("HTTP 429") -> "잠시 요청이 많았어요. 조금 뒤에 다시 시도해 주세요."
            raw.contains("HTTP 404") -> "게시물이 만료되었거나 더 이상 볼 수 없어요."
            error is SocketTimeoutException -> "연결이 지연되고 있어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
            raw.contains("HTTP 403") -> "이 항목에 접근할 수 없어요. 공개 범위와 로그인 상태를 확인해 주세요."
            else -> "저장하지 못했어요. 인터넷 연결과 게시물 링크를 확인해 주세요."
        }
        mutableState.value = mutableState.value.copy(phase = DownloadPhase.ERROR, message = message, needsLogin = login)
    }

    private fun keepAlive() {
        val context = appContext ?: return
        ContextCompat.startForegroundService(context, Intent(context, OverlayDownloadService::class.java))
    }
}
