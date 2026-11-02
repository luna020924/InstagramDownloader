package com.example.instasharedownloader

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.CookieManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in visual verification. Account names, URLs and cookies remain inside the app process. */
@RunWith(AndroidJUnit4::class)
class LiveShareUiTest {
    @Test fun realStoryOpensInTheNativeShareSheet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveInstagram") == "true")
        val cookie = CookieManager.getInstance().getCookie("https://www.instagram.com/")
        val url = runBlocking(Dispatchers.IO) {
            val tray = JSONObject(InstagramStoryApi.fetch("https://www.instagram.com/api/v1/feed/reels_tray/",
                "https://www.instagram.com/", cookie)).getJSONArray("tray")
            (0 until minOf(tray.length(), 12)).firstNotNullOfOrNull { index ->
                val entry = tray.getJSONObject(index)
                val user = entry.optJSONObject("user")
                if (user != null && !user.optBoolean("is_private") && entry.optLong("latest_reel_media") > 0) {
                    val id = user.optString("pk", user.optString("id"))
                    val response = InstagramStoryApi.fetch("https://www.instagram.com/api/v1/feed/user/$id/story/",
                        "https://www.instagram.com/", cookie)
                    val items = InstagramExtractor.parseStoryMedia(response, null)
                    if (items.size > 1) "https://www.instagram.com/stories/${user.getString("username")}/" else null
                } else null
            }
        }
        assumeTrue("No active public story is available", url != null)
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync { DownloadSession.reset() }
        val intent = Intent(context, ShareActivity::class.java).setAction(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<ShareActivity>(intent).use { scenario ->
            val deadline = System.currentTimeMillis() + 45000
            while (System.currentTimeMillis() < deadline && DownloadSession.state.value.busy) Thread.sleep(100)
            instrumentation.waitForIdleSync()
            if (DownloadSession.state.value.phase == DownloadPhase.STORY_SCOPE)
                instrumentation.runOnMainSync { DownloadSession.chooseAllStories() }
            assertTrue("Story picker did not become available: phase=${DownloadSession.state.value.phase}, count=${DownloadSession.state.value.items.size}",
                DownloadSession.state.value.phase == DownloadPhase.SELECTING)
            instrumentation.runOnMainSync { DownloadSession.toggle(0) }
            val selected = DownloadSession.state.value.selected
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertTrue("Selection did not survive activity recreation", DownloadSession.state.value.selected == selected)
            Thread.sleep(2500)
            capture("share-live.png")
            val foldStates = args.getString("foldStates")?.split(',')?.map { it.toInt() }.orEmpty()
            var created: Uri? = null
            try {
                for (state in foldStates) {
                    shell("cmd device_state state $state")
                    Thread.sleep(1800)
                    assertTrue("Selection changed during a display transition", DownloadSession.state.value.selected == selected)
                    assertTrue("Picker closed during a display transition", DownloadSession.state.value.phase == DownloadPhase.SELECTING)
                    capture("share-state-$state.png")
                }
                if (args.getString("downloadMedia") == "true") {
                    instrumentation.runOnMainSync {
                        DownloadSession.state.value.selected.filter { it != 0 }.forEach(DownloadSession::toggle)
                        if (0 !in DownloadSession.state.value.selected) DownloadSession.toggle(0)
                        DownloadSession.save()
                    }
                    val saveDeadline = System.currentTimeMillis() + 70000
                    while (DownloadSession.state.value.busy && System.currentTimeMillis() < saveDeadline) Thread.sleep(100)
                    created = DownloadSession.state.value.lastUri
                    assertTrue("Native save flow did not complete", DownloadSession.state.value.phase == DownloadPhase.DONE)
                    assertTrue("Native save flow did not publish one item", DownloadSession.state.value.savedCount == 1 && created != null)
                    context.contentResolver.openAssetFileDescriptor(created!!, "r").use { file ->
                        assertTrue("Native save flow published an empty file", file != null && file.length > 0)
                    }
                    Thread.sleep(500)
                    capture("save-done.png")
                }
            } finally {
                // This exact URI was newly inserted by this test; existing downloads are never touched.
                created?.let { context.contentResolver.delete(it, null, null) }
                if (foldStates.isNotEmpty()) shell("cmd device_state state reset")
            }
        }
        instrumentation.runOnMainSync { DownloadSession.reset() }
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir, "ui-verification").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
