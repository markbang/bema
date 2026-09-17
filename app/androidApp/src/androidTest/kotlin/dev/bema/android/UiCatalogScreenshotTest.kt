package dev.bema.android

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
        settle(1500)
        capture("01-timeline")

        composeRule.onNodeWithText("修了一晚上", substring = true).performClick()
        settle(1000)
        capture("02-detail")

        composeRule.onNodeWithContentDescription("Write a reply").performClick()
        settle(800)
        capture("03-comment-composer")
        composeRule.onNodeWithText("Cancel").performClick()
        settle()

        composeRule.onNodeWithContentDescription("Back").performClick()
        settle()

        composeRule.onNodeWithContentDescription("Open search").performClick()
        settle(800)
        capture("04-search-empty")
        textField().performClick()
        textField().performTextInput("时间线")
        settle(1600)
        capture("05-search-results")
        composeRule.onNodeWithContentDescription("Timeline").performClick()
        settle()

        composeRule.onNodeWithContentDescription("New memo").performClick()
        settle(900)
        capture("06-composer")
        textField().performClick()
        textField().performTextInput("Hello **Bema**\n\n- 新的一条备忘\n\n`inline code`")
        settle()
        composeRule.onNodeWithText("Preview").performClick()
        settle(800)
        capture("07-composer-preview")
        composeRule.onNodeWithText("Cancel").performClick()
        settle()

        composeRule.onNodeWithContentDescription("Switch account").performTouchInput { longClick() }
        settle(1000)
        capture("08-accounts")
        composeRule.onNodeWithText("Daily Memos").performTouchInput { longClick() }
        settle(800)
        capture("09-account-actions")
        composeRule.onNodeWithContentDescription("Back to accounts").performClick()
        settle()
        composeRule.onNodeWithContentDescription("Add account").performClick()
        settle(700)
        capture("10-add-account")
        composeRule.onNodeWithContentDescription("Back to accounts").performClick()
        settle()
        composeRule.onNodeWithText("Done").performClick()
        settle()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        settle(1400)
        capture("11-settings")
    }

    private fun settle(millis: Long = 600) {
        composeRule.waitForIdle()
        Thread.sleep(millis)
    }

    private fun textField() =
        composeRule.onAllNodes(hasSetTextAction()).onFirst()

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
