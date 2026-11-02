package com.example.instasharedownloader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectionControlsTheSaveActionAndNeverSubmitsAnEmptySet() {
        val items = List(12) { MediaItem("https://example.invalid/$it.jpg", MediaItem.Kind.IMAGE) }
        var submitted = emptySet<Int>()
        rule.setContent {
            DownloaderTheme {
                var selected by remember { mutableStateOf(items.indices.toSet()) }
                WorkPanel(DownloadState(phase = DownloadPhase.SELECTING, items = items, selected = selected),
                    modifier = Modifier.fillMaxSize(),
                    onToggle = { selected = if (it in selected) selected - it else selected + it },
                    onSelectAll = { selected = if (selected.size == items.size) emptySet() else items.indices.toSet() },
                    onSave = { submitted = selected }, onSharedStory = {}, onAllStories = {},
                    onRetry = {}, onLogin = {}, onOpenSaved = {}, onClose = {})
            }
        }
        rule.onNodeWithTag("select-all").performClick()
        rule.onNodeWithTag("save-selected").assertIsNotEnabled()
        rule.onNodeWithTag("media-0").performClick().assertIsSelected()
        rule.onNodeWithTag("save-selected").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(setOf(0), submitted) }
        rule.onNodeWithTag("media-0").performClick().assertIsNotSelected()
        rule.onNodeWithTag("save-selected").assertIsNotEnabled()
    }

    @Test fun failedBatchOffersOnlyTheRemainingItemsForRetry() {
        var retries = 0
        rule.setContent {
            DownloaderTheme {
                WorkPanel(DownloadState(phase = DownloadPhase.ERROR, sourceUrl = "https://x.com/example/status/1",
                    selected = setOf(2, 3), savedCount = 2, totalCount = 4, retrySaving = true,
                    message = "인터넷 연결을 확인해 주세요."), modifier = Modifier.fillMaxSize(),
                    onToggle = {}, onSelectAll = {}, onSave = {}, onSharedStory = {}, onAllStories = {},
                    onRetry = { retries++ }, onLogin = {}, onOpenSaved = {}, onClose = {})
            }
        }
        rule.onNodeWithText("2개 저장 후 중단됐어요").assertIsDisplayed()
        rule.onNodeWithText("남은 2개 다시 저장").performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }
}

@RunWith(AndroidJUnit4::class)
class HomeInputUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun invalidInputRemainsEditableAfterActivityRecreation() {
        rule.onNodeWithTag("link-input").performTextReplacement("잘못된 링크")
        rule.onNodeWithTag("resolve-link").performClick()
        rule.onNodeWithText("Instagram 또는 X의 게시물 링크를 입력해 주세요.").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag("link-input").assertTextContains("잘못된 링크")
            .performTextReplacement("다른 링크")
        rule.onNodeWithTag("link-input").assertTextContains("다른 링크")
    }
}
