package dev.bema.shared.data.session

import dev.bema.shared.data.model.Attachment
import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.User
import dev.bema.shared.data.model.Visibility
import dev.bema.shared.data.network.MemosApi
import dev.bema.shared.data.network.PersistentCookieStorage
import dev.bema.shared.data.network.normalizeInstanceUrl
import dev.bema.shared.data.storage.KeyValueStore
import dev.bema.shared.data.storage.PlatformKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PendingAttachment(
    val filename: String,
    val content: ByteArray,
    val type: String
)

@Serializable
data class MemosAccount(
    val id: String,
    val instanceUrl: String,
    val username: String,
    val userName: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val siteTitle: String = "Memos",
    val siteLogoUrl: String = "",
    val lastSignedInAt: Instant? = null
) {
    val visibleName: String get() = displayName.ifBlank { username }
}

data class MemosAppState(
    val accounts: List<MemosAccount> = emptyList(),
    val activeAccountId: String = "",
    val timeline: List<Memo> = emptyList(),
    val nextPageToken: String = "",
    val selectedMemo: Memo? = null,
    val selectedComments: List<Memo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isPublishing: Boolean = false,
    val error: String? = null,
    val userProfiles: Map<String, User> = emptyMap()
) {
    val activeAccount: MemosAccount? get() = accounts.firstOrNull { it.id == activeAccountId }
    val canLoadMore: Boolean get() = nextPageToken.isNotBlank() && !isLoadingMore
}

interface MemosUiController {
    val state: StateFlow<MemosAppState>

    suspend fun addAccount(instanceUrl: String, username: String, password: String)
    suspend fun selectAccount(accountId: String)
    suspend fun refreshTimeline(filter: String = "")
    suspend fun loadMore()
    suspend fun publish(
        content: String,
        visibility: Visibility = Visibility.PRIVATE,
        pendingAttachments: List<PendingAttachment> = emptyList()
    )
    suspend fun openMemo(name: String)
    fun closeMemo()
    suspend fun comment(content: String)
    suspend fun react(memo: Memo, reactionType: String)
    fun siteLogoUrl(): String
    fun accountLogoUrl(account: MemosAccount): String
    suspend fun avatarBytes(user: User): ByteArray?
    suspend fun attachmentBytes(attachment: Attachment, thumbnail: Boolean = false): ByteArray?
}

class MemosTimelineController(
    private val keyValueStore: KeyValueStore = PlatformKeyValueStore
) : MemosUiController {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val sessions = mutableMapOf<String, AccountSession>()
    private val _state = MutableStateFlow(loadState())
    override val state: StateFlow<MemosAppState> = _state

    override suspend fun addAccount(instanceUrl: String, username: String, password: String) {
        val normalized = normalizeInstanceUrl(instanceUrl)
        require(normalized.isNotBlank()) { "Instance URL is required" }
        require(username.isNotBlank()) { "Username is required" }
        require(password.isNotBlank()) { "Password is required" }

        setBusy(isLoading = true)
        runCatching {
            val accountId = accountId(normalized, username)
            val draft = MemosAccount(id = accountId, instanceUrl = normalized, username = username.trim())
            val session = sessions.getOrPut(accountId) { AccountSession(draft) }
            val response = session.signIn(username.trim(), password)
            val branding = session.loadBranding()
            val account = draft.copy(
                userName = response.user.name,
                displayName = response.user.visibleName,
                avatarUrl = response.user.avatarUrl,
                siteTitle = branding.first,
                siteLogoUrl = branding.second,
                lastSignedInAt = Clock.System.now()
            )
            sessions[accountId] = session
            _state.update { current ->
                current.copy(
                    accounts = (current.accounts.filterNot { it.id == account.id } + account).sortedBy { it.instanceUrl + it.username },
                    activeAccountId = account.id,
                    timeline = emptyList(),
                    nextPageToken = "",
                    selectedMemo = null,
                    selectedComments = emptyList(),
                    error = null
                )
            }
            persistAccounts()
            refreshTimeline("")
        }.onFailure { error ->
            _state.update { it.copy(error = error.message ?: "Sign in failed") }
        }
        setBusy(isLoading = false)
    }

    override suspend fun selectAccount(accountId: String) {
        if (_state.value.activeAccountId == accountId) return
        require(_state.value.accounts.any { it.id == accountId }) { "Unknown account" }
        _state.update {
            it.copy(
                activeAccountId = accountId,
                timeline = emptyList(),
                nextPageToken = "",
                selectedMemo = null,
                selectedComments = emptyList(),
                error = null
            )
        }
        persistAccounts()
        refreshTimeline("")
    }

    suspend fun removeAccount(accountId: String) {
        sessions.remove(accountId)?.clearCookies()
        _state.update { current ->
            val accounts = current.accounts.filterNot { it.id == accountId }
            val active = when {
                current.activeAccountId != accountId -> current.activeAccountId
                accounts.isNotEmpty() -> accounts.first().id
                else -> ""
            }
            current.copy(
                accounts = accounts,
                activeAccountId = active,
                timeline = if (active.isBlank()) emptyList() else current.timeline,
                nextPageToken = if (active.isBlank()) "" else current.nextPageToken,
                selectedMemo = null,
                selectedComments = emptyList(),
                error = null
            )
        }
        persistAccounts()
        if (_state.value.activeAccountId.isNotBlank()) refreshTimeline("")
    }

    override suspend fun refreshTimeline(filter: String) {
        val session = activeSessionOrNull() ?: return
        setBusy(isLoading = true)
        runCatching {
            val response = session.api.listMemos(filter = filter)
            val profiles = session.loadUsers(response.memos)
            _state.update {
                it.copy(
                    timeline = response.memos,
                    nextPageToken = response.nextPageToken,
                    selectedMemo = null,
                    selectedComments = emptyList(),
                    userProfiles = profiles,
                    error = null
                )
            }
        }.onFailure { error -> _state.update { it.copy(error = error.message ?: "Failed to load timeline") } }
        setBusy(isLoading = false)
    }

    override suspend fun loadMore() {
        val session = activeSessionOrNull() ?: return
        val token = _state.value.nextPageToken
        if (token.isBlank() || _state.value.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        runCatching {
            val response = session.api.listMemos(pageToken = token)
            val profiles = session.loadUsers(response.memos)
            _state.update {
                it.copy(
                    timeline = it.timeline + response.memos,
                    nextPageToken = response.nextPageToken,
                    userProfiles = it.userProfiles + profiles,
                    error = null
                )
            }
        }.onFailure { error -> _state.update { it.copy(error = error.message ?: "Failed to load more") } }
        _state.update { it.copy(isLoadingMore = false) }
    }

    override suspend fun publish(
        content: String,
        visibility: Visibility,
        pendingAttachments: List<PendingAttachment>
    ) {
        val body = content.trim()
        if (body.isBlank() && pendingAttachments.isEmpty()) return
        val session = activeSessionOrNull() ?: return
        _state.update { it.copy(isPublishing = true) }
        runCatching {
            val attachments = pendingAttachments.map { upload ->
                session.api.createAttachment(upload.filename, upload.content, upload.type)
            }
            val memo = session.api.createMemo(body, visibility, attachments = attachments)
            _state.update { it.copy(timeline = listOf(memo) + it.timeline, error = null) }
        }.onFailure { error -> _state.update { it.copy(error = error.message ?: "Publish failed") } }
        _state.update { it.copy(isPublishing = false) }
    }

    override suspend fun openMemo(name: String) {
        val session = activeSessionOrNull() ?: return
        setBusy(isLoading = true)
        runCatching {
            val memo = session.api.getMemo(name)
            val comments = session.api.listComments(name).memos
            _state.update { it.copy(selectedMemo = memo, selectedComments = comments, error = null) }
        }.onFailure { error -> _state.update { it.copy(error = error.message ?: "Failed to open memo") } }
        setBusy(isLoading = false)
    }

    override fun closeMemo() {
        _state.update { it.copy(selectedMemo = null, selectedComments = emptyList()) }
    }

    override suspend fun comment(content: String) {
        val parent = _state.value.selectedMemo ?: return
        val body = content.trim()
        if (body.isBlank()) return
        val session = activeSessionOrNull() ?: return
        runCatching {
            val comment = session.api.createComment(parent.name, body, parent.visibility)
            _state.update { it.copy(selectedComments = it.selectedComments + comment, error = null) }
        }.onFailure { error -> _state.update { it.copy(error = error.message ?: "Comment failed") } }
    }

    override suspend fun react(memo: Memo, reactionType: String) {
        val session = activeSessionOrNull() ?: return
        runCatching {
            val reaction = session.api.upsertReaction(memo.name, reactionType)
            _state.update { current ->
                current.copy(
                    timeline = current.timeline.replaceMemo(memo.name) { it.copy(reactions = it.reactions.withReaction(reaction)) },
                    selectedMemo = current.selectedMemo?.let { selected ->
                        if (selected.name == memo.name) selected.copy(reactions = selected.reactions.withReaction(reaction)) else selected
                    },
                    error = null
                )
            }
        }.onFailure { error -> _state.update { it.copy(error = error.message ?: "Reaction failed") } }
    }

    fun memoCreator(memo: Memo): User? = _state.value.userProfiles[memo.creator.substringAfterLast('/')]

    override fun siteLogoUrl(): String {
        val account = _state.value.activeAccount ?: return ""
        return accountLogoUrl(account)
    }

    override fun accountLogoUrl(account: MemosAccount): String =
        sessions[account.id]?.api?.assetUrl(account.siteLogoUrl).orEmpty()

    override suspend fun avatarBytes(user: User): ByteArray? =
        runCatching {
            val session = activeSessionOrNull() ?: return@runCatching null
            session.api.getUrlBytes(session.api.userAvatarUrl(user.username))
        }.getOrNull()

    override suspend fun attachmentBytes(attachment: Attachment, thumbnail: Boolean): ByteArray? =
        runCatching { activeSessionOrNull()?.api?.getAttachmentBytes(attachment, thumbnail) }.getOrNull()

    fun attachmentUrl(memo: Memo, attachmentName: String, thumbnail: Boolean = false): String {
        val attachment = memo.attachments.firstOrNull { it.name == attachmentName } ?: return ""
        return activeSessionOrNull()?.api?.attachmentUrl(attachment, thumbnail = thumbnail).orEmpty()
    }

    private fun activeSessionOrNull(): AccountSession? {
        val account = _state.value.activeAccount ?: return null
        return sessions.getOrPut(account.id) { AccountSession(account) }
    }

    private fun loadState(): MemosAppState {
        val stored = runCatching {
            keyValueStore.getString(ACCOUNTS_KEY)?.let { json.decodeFromString<StoredAccounts>(it) }
        }.getOrNull()
        val accounts = stored?.accounts.orEmpty()
        val active = stored?.activeAccountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull()?.id.orEmpty()
        return MemosAppState(accounts = accounts, activeAccountId = active)
    }

    private fun persistAccounts() {
        val current = _state.value
        keyValueStore.putString(
            ACCOUNTS_KEY,
            json.encodeToString(StoredAccounts(current.activeAccountId, current.accounts))
        )
    }

    private fun setBusy(isLoading: Boolean) {
        _state.update { it.copy(isLoading = isLoading) }
    }

    private inner class AccountSession(private val account: MemosAccount) {
        private var accessToken: String? = null
        private val cookieStorage = PersistentCookieStorage("memos.cookies.${account.id}", keyValueStore)
        val api: MemosApi = MemosApi(
            instanceUrl = account.instanceUrl,
            accessTokenProvider = { accessToken },
            refreshAccessToken = { refreshToken() },
            onUnauthorized = { _state.update { it.copy(error = "Session expired for ${account.username}") } },
            cookieStorage = cookieStorage
        )

        suspend fun signIn(username: String, password: String) = api.signIn(username, password).also {
            accessToken = it.accessToken
        }

        suspend fun loadBranding(): Pair<String, String> = runCatching {
            val profile = api.generalSetting().generalSetting?.customProfile
            (profile?.title?.ifBlank { "Memos" } ?: "Memos") to (profile?.logoUrl.orEmpty())
        }.getOrDefault("Memos" to "")

        suspend fun loadUsers(memos: List<Memo>): Map<String, User> = runCatching {
            api.batchGetUsers(memos.map { it.creator.substringAfterLast('/') })
                .associateBy { it.username }
        }.getOrDefault(emptyMap())

        suspend fun refreshToken(): String? = runCatching {
            val response = api.refresh()
            accessToken = response.accessToken
            accessToken
        }.getOrNull()

        fun clearCookies() {
            cookieStorage.clear()
        }
    }

    companion object {
        private const val ACCOUNTS_KEY = "memos.accounts"

        private fun accountId(instanceUrl: String, username: String): String {
            val raw = "${normalizeInstanceUrl(instanceUrl)}:${username.trim().lowercase()}"
            return raw.hashCode().toUInt().toString(16) + "-" + username.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        }
    }
}

@Serializable
private data class StoredAccounts(
    val activeAccountId: String = "",
    val accounts: List<MemosAccount> = emptyList()
)

private inline fun List<Memo>.replaceMemo(name: String, transform: (Memo) -> Memo): List<Memo> =
    map { if (it.name == name) transform(it) else it }

private fun List<dev.bema.shared.data.model.Reaction>.withReaction(
    reaction: dev.bema.shared.data.model.Reaction
): List<dev.bema.shared.data.model.Reaction> = filterNot { it.creator == reaction.creator } + reaction
