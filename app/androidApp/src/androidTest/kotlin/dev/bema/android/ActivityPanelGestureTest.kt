package dev.bema.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityPanelGestureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<UiCatalogActivity>()

    @Test
    fun shortSwipeOpensAndClosesCompletely() {
        // Much shorter than half the panel: this must still reveal every column.
        swipeBy(80f)
        assertFullyOpen()

        swipeBy(-80f)
        assertFullyClosed()
    }

    @Test
    fun smallDragsDoNotLeaveThePanelPartiallyExposed() {
        swipeBy(24f)
        assertFullyClosed()

        swipeBy(80f)
        assertFullyOpen()
        swipeBy(-24f)
        assertFullyOpen()

        swipeBy(-80f)
        assertFullyClosed()
        swipeBy(24f)
        assertFullyClosed()
    }

    @Test
    fun quickFlickDoesNotRequireALongDrag() {
        swipeBy(30f, durationMillis = 40)
        assertFullyOpen()

        swipeBy(-30f, durationMillis = 40)
        assertFullyClosed()
    }

    @Test
    fun tappingOutsideStillClosesThePanel() {
        swipeBy(80f)
        assertFullyOpen()

        composeRule.onRoot().performTouchInput {
            click(Offset(width * 0.96f, height * 0.5f))
        }
        assertFullyClosed()
    }

    private fun swipeBy(distanceDp: Float, durationMillis: Long = 600) {
        val distancePx = distanceDp * composeRule.activity.resources.displayMetrics.density
        composeRule.onRoot().performTouchInput {
            // Keep the gesture away from the memo media rail and system edges.
            val start = Offset(width * 0.45f, height * 0.12f)
            swipe(start, start + Offset(distancePx, 0f), durationMillis)
        }
        composeRule.waitForIdle()
    }

    private fun assertFullyOpen() {
        composeRule.waitForIdle()
        val bounds = composeRule.onNodeWithContentDescription("Activity panel")
            .getUnclippedBoundsInRoot()
        assertEquals("Panel must settle at the left edge", 0f, bounds.left.value, 0.5f)
        composeRule.onNodeWithContentDescription("Previous month").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next month").assertIsDisplayed()
    }

    private fun assertFullyClosed() {
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Previous month").assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription("Next month").assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription("Timeline").assertIsDisplayed()
    }
}
