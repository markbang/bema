package dev.bema.android

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<UiCatalogActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val outputDirectory: File
        get() = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-preview")

    @Before
    fun resetOutputDirectory() {
        outputDirectory.deleteRecursively()
        check(outputDirectory.mkdirs()) { "Could not create ${outputDirectory.absolutePath}" }
    }

    @Test
    fun captureCatalogStates() {
        composeRule.onNodeWithText("For you").assertIsDisplayed()
        capture("01-timeline")

        composeRule.onNodeWithText("修了一晚上", substring = true).performClick()
        composeRule.onNodeWithText("Replies").assertIsDisplayed()
        capture("02-detail")

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("New memo").performClick()
        composeRule.onNodeWithText("New memo").assertIsDisplayed()
        capture("03-composer")

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithContentDescription("Switch account").performClick()
        composeRule.onNodeWithText("Accounts").assertIsDisplayed()
        capture("04-accounts")
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(600)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "UiAutomation returned no screenshot for $name"
        }
        FileOutputStream(File(outputDirectory, "$name.png")).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not write screenshot $name"
            }
        }
        bitmap.recycle()
    }
}
