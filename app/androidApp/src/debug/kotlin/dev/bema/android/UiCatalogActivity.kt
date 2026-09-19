package dev.bema.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.bema.shared.data.model.Attachment
import dev.bema.shared.data.model.CustomProfile
import dev.bema.shared.data.model.GeneralSetting
import dev.bema.shared.data.model.InstanceSetting
import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.MemoRelatedSetting
import dev.bema.shared.data.model.MemoState
import dev.bema.shared.data.model.Reaction
import dev.bema.shared.data.model.User
import dev.bema.shared.data.model.UserRole
import dev.bema.shared.data.model.UserState
import dev.bema.shared.data.model.Visibility
import dev.bema.shared.data.model.WorkspaceSetting
import dev.bema.shared.data.session.ActivityStats
import dev.bema.shared.data.session.MemosAccount
import dev.bema.shared.data.session.TagCount
import dev.bema.shared.data.session.MemosAppState
import dev.bema.shared.data.session.MemosUiController
import dev.bema.shared.data.session.PendingAttachment
import dev.bema.shared.data.session.ThemeMode
import dev.bema.shared.data.session.localDayFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant

class UiCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previewController = PreviewMemosController(this)
        setContent { BemaMemosApp(controller = previewController) }
    }
}

private class PreviewMemosController(private val context: Context) : MemosUiController {
    private val lin = user("lin", "Lin", R.drawable.preview_avatar_lin)
    private val max = user("maxfast", "拾一 · Max Fast", R.drawable.preview_avatar_mika)
    private val mika = user("mika", "Mika", R.drawable.preview_avatar_rin)

    private val avatarResources = mapOf(
        "lin" to R.drawable.preview_avatar_lin,
        "maxfast" to R.drawable.preview_avatar_mika,
        "mika" to R.drawable.preview_avatar_rin
    )

    private val accounts = listOf(
        account("bema", "Bema Notes", "Lin", R.drawable.ic_launcher_foreground),
        account("daily", "Daily Memos", "Mika", R.drawable.preview_avatar_mika),
        account("team", "Team Notes", "Rin", R.drawable.preview_avatar_rin)
    )

    private val phoneAttachment = attachment("phone", "native-preview.jpg")
    private val codeAttachment = attachment("code", "code-preview.jpg")

    private val firstMemo = Memo(
        name = "memos/preview-native",
        state = MemoState.NORMAL,
        creator = "users/maxfast",
        createTime = Instant.parse("2026-09-16T12:10:00Z"),
        content = "修了一晚上，终于把该死的 RN 兼容问题修了。Bema 现在已经支持完整的原生返回动画。\n\n欢迎使用，也欢迎继续挑 UI 的毛病。",
        visibility = Visibility.PUBLIC,
        tags = listOf("android", "native"),
        attachments = listOf(phoneAttachment, codeAttachment),
        reactions = reactions(25)
    )

    private val secondMemo = Memo(
        name = "memos/preview-design",
        state = MemoState.NORMAL,
        creator = "users/mika",
        createTime = Instant.parse("2026-09-16T11:42:00Z"),
        content = "把时间线重新做成了真正适合阅读的样子：头像是信息锚点，正文不再塞进卡片，图片可以横向滑动。",
        visibility = Visibility.PROTECTED,
        tags = listOf("design", "miuix", "memos"),
        attachments = listOf(codeAttachment),
        reactions = reactions(18)
    )

    private val thirdMemo = Memo(
        name = "memos/preview-private",
        state = MemoState.NORMAL,
        creator = "users/lin",
        createTime = Instant.parse("2026-09-16T10:58:00Z"),
        content = "自己的数据，自己的实例，打开客户端就是一条干净的时间线。",
        visibility = Visibility.PRIVATE,
        reactions = reactions(9)
    )

    private val comments = listOf(
        Memo(
            name = "memos/comment-1",
            creator = "users/mika",
            createTime = Instant.parse("2026-09-16T12:18:00Z"),
            content = "图片横滑和头像层级都舒服多了。",
            visibility = Visibility.PUBLIC,
            parent = firstMemo.name
        ),
        Memo(
            name = "memos/comment-2",
            creator = "users/lin",
            createTime = Instant.parse("2026-09-16T12:22:00Z"),
            content = "下一步可以继续调正文行高和操作栏间距。",
            visibility = Visibility.PUBLIC,
            parent = firstMemo.name
        )
    )

    private val _state = MutableStateFlow(
        MemosAppState(
            accounts = accounts,
            activeAccountId = accounts.first().id,
            timeline = listOf(firstMemo, secondMemo, thirdMemo),
            userProfiles = listOf(lin, max, mika).associateBy { it.username },
            // Fixed screens for the catalog: pin the theme instead of inheriting
            // whatever the emulator happens to be set to.
            themeMode = ThemeMode.DARK
        )
    )
    override val state: StateFlow<MemosAppState> = _state

    override suspend fun addAccount(instanceUrl: String, username: String, password: String) = Unit

    override suspend fun selectAccount(accountId: String) {
        _state.update { it.copy(activeAccountId = accountId) }
    }

    override suspend fun renameAccount(accountId: String, displayName: String) {
        _state.update { current ->
            current.copy(accounts = current.accounts.map { if (it.id == accountId) it.copy(displayName = displayName) else it })
        }
    }

    override suspend fun setAccountPinned(accountId: String, pinned: Boolean) {
        _state.update { current ->
            current.copy(accounts = current.accounts.map { if (it.id == accountId) it.copy(pinned = pinned) else it })
        }
    }

    override suspend fun removeAccount(accountId: String) {
        _state.update { current ->
            val accounts = current.accounts.filterNot { it.id == accountId }
            current.copy(
                accounts = accounts,
                activeAccountId = if (current.activeAccountId == accountId) accounts.firstOrNull()?.id.orEmpty() else current.activeAccountId
            )
        }
    }

    override suspend fun refreshTimeline(filter: String) = Unit

    override suspend fun revalidateTimeline() = Unit

    override suspend fun loadMore() = Unit

    override suspend fun search(query: String): List<Memo> =
        _state.value.timeline.filter { it.content.contains(query, ignoreCase = true) }

    override suspend fun loadInstanceSettings(): InstanceSetting = InstanceSetting(
        name = "instanceSettings/GENERAL",
        generalSetting = GeneralSetting(
            customProfile = CustomProfile(
                title = "Bema Notes",
                description = "自己的数据，自己的实例",
                locale = "zh-CN",
                appearance = "dark"
            ),
            disallowUserRegistration = true
        ),
        memoRelatedSetting = MemoRelatedSetting(enableLinkMetadata = true),
        workspaceSetting = WorkspaceSetting(
            announcement = "欢迎来到 Bema Notes。",
            maxUploadSizeMiB = 32
        )
    )

    override suspend fun saveInstanceSettings(
        general: GeneralSetting,
        memoRelated: MemoRelatedSetting,
        workspace: WorkspaceSetting
    ) = Unit

    override suspend fun publish(content: String, visibility: Visibility, pendingAttachments: List<PendingAttachment>) {
        val memo = Memo(
            name = "memos/preview-new",
            creator = "users/lin",
            createTime = Instant.parse("2026-09-16T12:30:00Z"),
            content = content,
            visibility = visibility
        )
        _state.update { it.copy(timeline = listOf(memo) + it.timeline) }
    }

    override suspend fun openMemo(name: String) {
        val memo = _state.value.timeline.firstOrNull { it.name == name } ?: return
        _state.update { it.copy(selectedMemo = memo, selectedComments = comments) }
    }

    override fun closeMemo() {
        _state.update { it.copy(selectedMemo = null, selectedComments = emptyList()) }
    }

    override fun setThemeMode(mode: ThemeMode) {
        _state.update { it.copy(themeMode = mode) }
    }

    override suspend fun comment(content: String) {
        val parent = _state.value.selectedMemo ?: return
        val comment = Memo(
            name = "memos/comment-new",
            creator = "users/lin",
            createTime = Instant.parse("2026-09-16T12:32:00Z"),
            content = content,
            visibility = parent.visibility,
            parent = parent.name
        )
        _state.update { it.copy(selectedComments = it.selectedComments + comment) }
    }

    override suspend fun react(memo: Memo, reactionType: String) {
        val mine = "users/lin"
        _state.update { current ->
            fun Memo.toggled(): Memo {
                val existing = reactions.firstOrNull { it.creator == mine && it.reactionType == reactionType }
                val next = if (existing != null) reactions.filterNot { it.name == existing.name }
                else reactions + Reaction(name = "${name}/reactions/preview", creator = mine, reactionType = reactionType)
                return copy(reactions = next)
            }
            current.copy(
                timeline = current.timeline.map { if (it.name == memo.name) it.toggled() else it },
                selectedMemo = current.selectedMemo?.let { if (it.name == memo.name) it.toggled() else it },
                selectedComments = current.selectedComments.map { if (it.name == memo.name) it.toggled() else it }
            )
        }
    }

    override fun memoUrl(memo: Memo): String = "https://preview.invalid/m/${memo.uid}"

    override fun siteLogoUrl(): String = resourceUri(R.drawable.ic_launcher_foreground)

    override fun accountLogoUrl(account: MemosAccount): String = account.siteLogoUrl

    override suspend fun avatarBytes(user: User): ByteArray? =
        avatarResources[user.username]?.let { resource ->
            runCatching { context.resources.openRawResource(resource).use { it.readBytes() } }.getOrNull()
        }

    override suspend fun accountAvatarBytes(account: MemosAccount): ByteArray? =
        account.avatarUrl.substringAfterLast('/').toIntOrNull()?.let { id ->
            runCatching { context.resources.openRawResource(id).use { it.readBytes() } }.getOrNull()
        }

    override suspend fun attachmentBytes(attachment: Attachment, thumbnail: Boolean): ByteArray? {
        val resource = when (attachment.uid) {
            "phone" -> R.drawable.preview_media_phone
            "code" -> R.drawable.preview_media_code
            else -> return null
        }
        return context.resources.openRawResource(resource).use { it.readBytes() }
    }

    // The catalog renders fixed screens and reaches no network: an update prompt
    // would be noise in the screenshots.
    override suspend fun checkForUpdate() = Unit

    override fun skipUpdate(version: String) = Unit

    override suspend fun downloadUpdate(): ByteArray? = null

    /** Reflecting a tapped day keeps the filter chip visible in the catalog. */
    override suspend fun showDay(epochDay: Long?) {
        _state.update { it.copy(timelineFilter = epochDay?.let { day -> localDayFilter(day, 0) }) }
    }

    /** Stands in for the instance call, so the account sheet shows a version. */
    override suspend fun refreshInstanceVersion(accountId: String) {
        _state.update { current ->
            current.copy(
                accounts = current.accounts.map {
                    if (it.id == accountId) it.copy(instanceVersion = "0.30.0") else it
                }
            )
        }
    }

    /**
     * Stands in for the instance call so the activity panel has a plausible year to
     * draw: a deterministic scatter rather than real data, which is what the catalog
     * screenshots need.
     */
    override suspend fun refreshActivityStats() {
        val today = Clock.System.now().epochSeconds.floorDiv(86_400L)
        val dayCounts = (0L until 371L)
            .map { back -> (today - back) to ((back * 37 + 11) % 13).toInt().let { if (it < 7) 0 else it - 6 } }
            .filter { it.second > 0 }
            .toMap()
        _state.update {
            it.copy(
                activity = ActivityStats(
                    tagCounts = listOf(
                        TagCount("android", 12), TagCount("native", 9), TagCount("compose", 7),
                        TagCount("memos", 5), TagCount("ui", 4), TagCount("kotlin", 3)
                    ),
                    dayCounts = dayCounts
                )
            )
        }
    }

    private fun user(username: String, displayName: String, avatarResource: Int) = User(
        name = "users/$username",
        role = UserRole.USER,
        username = username,
        displayName = displayName,
        avatarUrl = resourceUri(avatarResource),
        state = UserState.NORMAL
    )

    private fun account(id: String, siteTitle: String, displayName: String, logoResource: Int) = MemosAccount(
        id = id,
        instanceUrl = "https://preview.invalid/$id",
        username = id,
        userName = "users/$id",
        displayName = displayName,
        avatarUrl = resourceUri(
            when (id) {
                "daily" -> R.drawable.preview_avatar_mika
                "team" -> R.drawable.preview_avatar_rin
                else -> R.drawable.preview_avatar_lin
            }
        ),
        siteTitle = siteTitle,
        siteLogoUrl = resourceUri(logoResource)
    )

    private fun attachment(uid: String, filename: String) = Attachment(
        name = "attachments/$uid",
        filename = filename,
        type = "image/jpeg",
        size = 128_000
    )

    private fun reactions(count: Int): List<Reaction> = List(count) { index ->
        Reaction(
            name = "memos/preview/reactions/$index",
            creator = "users/user-$index",
            reactionType = if (index % 3 == 0) "❤️" else "👍"
        )
    }

    private fun resourceUri(resource: Int): String = "android.resource://${context.packageName}/$resource"
}
