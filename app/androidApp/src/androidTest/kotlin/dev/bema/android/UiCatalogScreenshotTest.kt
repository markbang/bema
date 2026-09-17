package dev.bema.android

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<UiCatalogActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun captureCatalogStates() {
        Thread.sleep(1000)
        composeRule.waitForIdle()
        
        // Timeline
        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithText("Bema Notes", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        capture("01-timeline")

        // Detail
        composeRule.onNodeWithText("修了一晚上", substring = true).performClick()
        composeRule.waitForIdle()
        Thread.sleep(800)
        capture("02-detail")

        // Composer
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)
        composeRule.onNodeWithContentDescription("New memo").performClick()
        composeRule.waitForIdle()
        Thread.sleep(800)
        capture("03-composer")

        // Accounts
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)
        composeRule.onNodeWithText("Lin", substring = true).performClick()
        composeRule.waitForIdle()
        Thread.sleep(800)
        capture("04-accounts")
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(600)
        val bitmap = checkNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        ) { "UiAutomation returned no screenshot for $name" }
        val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not encode screenshot $name"
            }
            output.toByteArray()
        }
        bitmap.recycle()
        writeToDownloads(name, bytes)
    }

    private fun writeToDownloads(name: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/bema-ui-preview"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ) { "Could not create MediaStore row for $name" }
        try {
            resolver.openOutputStream(uri).use { output ->
                checkNotNull(output) { "Could not open MediaStore row for $name" }.write(bytes)
            }
            val published = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            check(resolver.update(uri, published, null, null) == 1) {
                "Could not publish screenshot $name"
            }
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
