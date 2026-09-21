package dev.bema.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bema.shared.data.model.CustomProfile
import dev.bema.shared.data.model.GeneralSetting
import dev.bema.shared.data.model.InstanceSetting
import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.StorageSetting
import dev.bema.shared.data.model.Visibility
import dev.bema.shared.data.session.MemosAccount
import dev.bema.shared.data.session.MemosAppState
import dev.bema.shared.data.session.MemosTimelineController
import dev.bema.shared.data.session.MemosUiController
import dev.bema.shared.data.session.PendingAttachment
import dev.bema.shared.data.storage.KeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiRegressionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptySignInCannotBeSubmitted() {
        val controller = UiTestController(MemosAppState())
        composeRule.setContent { BemaMemosApp(controller) }
        composeRule.onNodeWithText("Sign in").assertIsNotEnabled()
    }

    @Test
    fun accountSwitcherOpensOnAnAccessibleSingleClick() {
        val controller = UiTestController()
        composeRule.setContent { BemaMemosApp(controller) }
        composeRule.onNodeWithContentDescription("Switch account").performClick()
        composeRule.onNodeWithContentDescription("Add account").assertIsDisplayed()
    }

    @Test
    fun publishingFailureKeepsDraftAndPostIsReachableWithKeyboard() {
        val controller = UiTestController()
        composeRule.setContent { BemaMemosApp(controller) }
        composeRule.onNodeWithContentDescription("New memo").performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Keep my draft")
        composeRule.onNodeWithText("Post").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Publish unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Keep my draft").assertIsDisplayed()
        composeRule.onNodeWithText("Post").assertIsEnabled()
    }

    @Test
    fun failedReplyKeepsItsEditorOpen() {
        val controller = UiTestController()
        composeRule.setContent { BemaMemosApp(controller) }
        composeRule.onNodeWithText("A memo").performClick()
        composeRule.onNodeWithContentDescription("Write a reply").performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Keep my reply")
        composeRule.onNodeWithText("Reply").performClick()
        composeRule.onNodeWithText("Reply unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Keep my reply").assertIsDisplayed()
    }

    @Test
    fun settingsSaveSurvivesDismissingItsConfirmation() {
        val controller = UiTestController()
        composeRule.setContent { BemaMemosApp(controller) }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        composeRule.onNodeWithText("Apply").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(controller.saveStarted)
            assertFalse(controller.saveCancelled)
            controller.finishSave.complete(Unit)
        }
        composeRule.waitUntil(5_000) { controller.saveFinished }
    }

    @Test
    fun localThemeRemainsAvailableWhenInstanceSettingsFail() {
        val controller = UiTestController().apply { failSettings = true }
        composeRule.setContent { BemaMemosApp(controller) }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Settings unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("App theme").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test
    fun paginationUsesTheCursorLoadedAfterInitialComposition() {
        val controller = UiTestController(initialState().copy(timeline = emptyList())).apply { paginate = true }
        composeRule.setContent { BemaMemosApp(controller) }
        composeRule.waitUntil(5_000) { controller.pageLoaded }
        composeRule.onNodeWithText("Second page").assertIsDisplayed()
    }
}

private val testMemo = Memo(name = "memos/test", content = "A memo")
private fun initialState(): MemosAppState = MemosAppState(
    accounts = listOf(MemosAccount("test", "https://example.invalid", "alice")),
    activeAccountId = "test",
    timeline = listOf(testMemo)
)

private class UiTestController(initial: MemosAppState = initialState()) : MemosUiController by MemosTimelineController(UiTestStore()) {
    override val state = MutableStateFlow(initial)
    var failSettings = false
    var paginate = false
    var pageLoaded = false
    var saveStarted = false
    var saveFinished = false
    var saveCancelled = false
    val finishSave = CompletableDeferred<Unit>()

    override suspend fun checkForUpdate() = Unit
    override suspend fun revalidateTimeline() = Unit
    override suspend fun refreshTimeline(filter: String) {
        state.update { it.copy(timeline = listOf(testMemo), nextPageToken = if (paginate) "next" else "") }
    }
    override suspend fun loadMore() {
        state.update { it.copy(timeline = it.timeline + Memo(name = "memos/next", content = "Second page"), nextPageToken = "") }
        pageLoaded = true
    }
    override suspend fun openMemo(name: String) {
        state.update { it.copy(selectedMemo = testMemo) }
    }
    override suspend fun loadInstanceSettings(): InstanceSetting {
        check(!failSettings) { "Settings unavailable" }
        return InstanceSetting(
            generalSetting = GeneralSetting(customProfile = CustomProfile(title = "Test")),
            storageSetting = StorageSetting(uploadSizeLimitMb = 32)
        )
    }
    override suspend fun saveInstanceSettings(general: GeneralSetting, storage: StorageSetting) {
        saveStarted = true
        try {
            finishSave.await()
            saveFinished = true
        } finally {
            saveCancelled = !saveFinished
        }
    }
    override suspend fun publish(content: String, visibility: Visibility, pendingAttachments: List<PendingAttachment>) {
        error("Publish unavailable")
    }
    override suspend fun comment(content: String) {
        error("Reply unavailable")
    }
    override suspend fun accountAvatarBytes(account: MemosAccount): ByteArray? = null
}

private class UiTestStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
