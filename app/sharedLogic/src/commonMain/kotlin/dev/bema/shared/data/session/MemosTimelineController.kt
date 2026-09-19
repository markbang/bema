package dev.bema.shared.data.session

import dev.bema.shared.data.model.Attachment
import dev.bema.shared.data.model.GeneralSetting
import dev.bema.shared.data.model.InstanceSetting
import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.MemoRelatedSetting
import dev.bema.shared.data.model.Reaction
import dev.bema.shared.data.model.User
import dev.bema.shared.data.model.Visibility
import dev.bema.shared.data.model.WorkspaceSetting
import dev.bema.shared.data.network.MemosApi
import dev.bema.shared.data.network.PersistentCookieStorage
import dev.bema.shared.data.network.normalizeInstanceUrl
import dev.bema.shared.data.storage.KeyValueStore
import dev.bema.shared.data.storage.PlatformKeyValueStore
import dev.bema.shared.data.update.AppVersion
import dev.bema.shared.data.update.AvailableUpdate
import dev.bema.shared.data.update.UpdateApi
import dev.bema.shared.data.update.UpdateDownload
import dev.bema.shared.data.session.TimelineFilter
import dev.bema.shared.data.session.localDayFilter
import dev.bema.shared.deviceUtcOffsetSeconds
import dev.bema.shared.data.update.deviceAbi
import dev.bema.shared.data.update.installedVersionName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
    // The instance's reported version, so the client can say which Memos it talks
    // to. Blank until fetched.
    val instanceVersion: String = "",
    // Pinned accounts sort to the top of the account switcher.
    val pinned: Boolean = false,
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
    val userProfiles: Map<String, User> = emptyMap(),
    // Set when a silent refresh found memos newer than the visible cached list.
    val hasNewerMemos: Boolean = false,
    // Set when the update host has a newer signed APK for this device's ABI.
    val availableUpdate: AvailableUpdate? = null,
    // Progress of an in-app update download, cleared when it finishes.
    val updateDownload: UpdateDownload? = null,
    // Tag cloud and heatmap data for the activity panel; null until fetched.
    val activity: ActivityStats? = null,
    // Set while the timeline is narrowed to one day from the activity panel.
    val timelineFilter: TimelineFilter? = null,
    // The client's own light/dark choice; see ThemePreference.kt.
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    val activeAccount: MemosAccount? get() = accounts.firstOrNull { it.id == activeAccountId }
    val orderedAccounts: List<MemosAccount> get() = accounts.sortedByDescending { it.pinned }
    val canLoadMore: Boolean get() = nextPageToken.isNotBlank() && !isLoadingMore
}

interface MemosUiController {
    val state: StateFlow<MemosAppState>

    // Annotated with @Throws so Swift receives them as `async throws`: Kotlin/Native
    // terminates the process when a non-CancellationException escapes an unannotated
    // suspend function, and a client can reach the `require`/`error` validation paths.
    @Throws(Exception::class)
    suspend fun addAccount(instanceUrl: String, username: String, password: String)
    @Throws(Exception::class)
    suspend fun selectAccount(accountId: String)
    @Throws(Exception::class)
    suspend fun renameAccount(accountId: String, displayName: String)
    @Throws(Exception::class)
    suspend fun setAccountPinned(accountId: String, pinned: Boolean)
    @Throws(Exception::class)
    suspend fun removeAccount(accountId: String)
    @Throws(Exception::class)
    suspend fun refreshTimeline(filter: String = "")

    // Silent check for newer memos; shows a banner instead of jumping the visible list.
    @Throws(Exception::class)
    suspend fun revalidateTimeline()
    @Throws(Exception::class)
    suspend fun loadMore()
    @Throws(Exception::class)
    suspend fun search(query: String): List<Memo>
    @Throws(Exception::class)
    suspend fun loadInstanceSettings(): InstanceSetting
    @Throws(Exception::class)
    suspend fun saveInstanceSettings(
        general: GeneralSetting,
        memoRelated: MemoRelatedSetting,
        workspace: WorkspaceSetting
    )
    @Throws(Exception::class)
    suspend fun publish(
        content: String,
        visibility: Visibility = Visibility.PRIVATE,
        pendingAttachments: List<PendingAttachment> = emptyList()
    )
    @Throws(Exception::class)
    suspend fun openMemo(name: String)
    fun closeMemo()
    fun setThemeMode(mode: ThemeMode)
    @Throws(Exception::class)
    suspend fun comment(content: String)
    @Throws(Exception::class)
    suspend fun react(memo: Memo, reactionType: String)
    fun memoUrl(memo: Memo): String
    fun siteLogoUrl(): String
    fun accountLogoUrl(account: MemosAccount): String
    @Throws(Exception::class)
    suspend fun avatarBytes(user: User): ByteArray?
    @Throws(Exception::class)
    suspend fun accountAvatarBytes(account: MemosAccount): ByteArray?
    @Throws(Exception::class)
    suspend fun attachmentBytes(attachment: Attachment, thumbnail: Boolean = false): ByteArray?

    // Deliberately not annotated @Throws: everything except cancellation is
    // swallowed, because an unreachable update host must not surface as an app error.
    suspend fun checkForUpdate()
    fun skipUpdate(version: String)

    /**
     * Refreshes [accountId]'s instance version from `instance/profile`. Best effort:
     * an unreachable instance leaves the stored value alone.
     */
    suspend fun refreshInstanceVersion(accountId: String)

    /**
     * Refreshes the activity panel's tag cloud and heatmap for the active account.
     * Best effort, like the version above.
     */
    suspend fun refreshActivityStats()

    /** Narrows the timeline to one local day, or clears it when [epochDay] is null. */
    @Throws(Exception::class)
    suspend fun showDay(epochDay: Long?)

    /**
     * Downloads the update [checkForUpdate] found, reporting progress through
     * [MemosAppState.updateDownload]. Null when there is nothing to install or the
     * transfer failed.
     */
    suspend fun downloadUpdate(): ByteArray?
}

@Serializable
private data class CachedTimeline(
    val memos: List<Memo> = emptyList(),
    val nextPageToken: String = "",
    val savedAt: Instant? = null
)

@OptIn(ExperimentalEncodingApi::class)
class MemosTimelineController(
    private val keyValueStore: KeyValueStore = PlatformKeyValueStore
) : MemosUiController {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val updateApi = UpdateApi()
    private val reactionMutex = Mutex()
    private val sessions = mutableMapOf<String, AccountSession>()
    private val avatarCache = mutableMapOf<String, ByteArray>()
    private val _state = MutableStateFlow(loadState())
    override val state: StateFlow<MemosAppState> = _state

    override suspend fun addAccount(instanceUrl: String, username: String, password: String) {
        val normalized = normalizeInstanceUrl(instanceUrl)
        require(normalized.isNotBlank()) { "Instance URL is required" }
        require(username.isNotBlank()) { "Username is required" }
        require(password.isNotBlank()) { "Password is required" }

        setBusy(isLoading = true)
        try {
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
                instanceVersion = session.loadInstanceVersion(),
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
        } catch (e: CancellationException) {
            // Cancellation is not a sign-in failure; reporting it as one turned a
            // Compose scope teardown into a visible "…left the composition" error.
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Sign in failed") }
        } finally {
            setBusy(isLoading = false)
        }
    }

    override suspend fun selectAccount(accountId: String) {
        if (_state.value.activeAccountId == accountId) return
        require(_state.value.accounts.any { it.id == accountId }) { "Unknown account" }
        val cached = loadCachedTimeline(accountId)
        _state.update {
            it.copy(
                activeAccountId = accountId,
                timeline = cached?.memos.orEmpty(),
                nextPageToken = cached?.nextPageToken.orEmpty(),
                selectedMemo = null,
                selectedComments = emptyList(),
                error = null
            )
        }
        persistAccounts()
        refreshTimeline("")
    }

    override suspend fun renameAccount(accountId: String, displayName: String) {
        val trimmed = displayName.trim()
        _state.update { current ->
            current.copy(
                accounts = current.accounts.map { if (it.id == accountId) it.copy(displayName = trimmed) else it }
            )
        }
        persistAccounts()
    }

    override suspend fun setAccountPinned(accountId: String, pinned: Boolean) {
        _state.update { current ->
            current.copy(
                accounts = current.accounts.map { if (it.id == accountId) it.copy(pinned = pinned) else it }
            )
        }
        persistAccounts()
    }

    override suspend fun removeAccount(accountId: String) {
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
        val accountId = _state.value.activeAccountId
        // A blank filter means "whatever is current": the refresh controls must not
        // silently drop a day the user picked in the activity panel.
        val effective = filter.ifBlank { _state.value.timelineFilter?.cel.orEmpty() }
        // Show stale content while revalidating instead of an empty loading screen.
        setBusy(isLoading = _state.value.timeline.isEmpty())
        runCatching {
            val response = session.api.listMemos(filter = effective)
            val profiles = session.loadUsers(response.memos)
            _state.update { current ->
                current.copy(
                    timeline = response.memos,
                    nextPageToken = response.nextPageToken,
                    selectedMemo = null,
                    selectedComments = emptyList(),
                    userProfiles = profiles,
                    hasNewerMemos = false,
                    error = null
                )
            }
            // Only the unfiltered list is worth keeping: caching one day would greet
            // the next launch with that day alone.
            if (effective.isBlank()) {
                persistTimeline(accountId, response.memos, response.nextPageToken)
            }
        }.onFailure { error ->
            // Serve the cache when offline instead of a dead-end error screen.
            if (_state.value.timeline.isEmpty()) {
                _state.update { it.copy(error = error.message ?: "Failed to load timeline") }
            }
        }
        setBusy(isLoading = false)
    }

    override suspend fun showDay(epochDay: Long?) {
        val filter = epochDay?.let { localDayFilter(it, deviceUtcOffsetSeconds()) }
        _state.update { it.copy(timelineFilter = filter) }
        refreshTimeline("")
    }

    private var revalidating = false

    // Silent refresh on timeline entry: if the server has newer memos than the
    // visible (cached) list, surface a banner instead of shifting content mid-read.
    override suspend fun revalidateTimeline() {
        val session = activeSessionOrNull() ?: return
        if (revalidating || _state.value.isLoading) return
        revalidating = true
        try {
            val response = session.api.listMemos()
            val profiles = session.loadUsers(response.memos)
            _state.update { current ->
                val hasNewer = current.timeline.isNotEmpty() &&
                    response.memos.firstOrNull()?.name != current.timeline.first().name
                current.copy(
                    timeline = if (hasNewer) current.timeline else response.memos,
                    nextPageToken = if (hasNewer) current.nextPageToken else response.nextPageToken,
                    userProfiles = current.userProfiles + profiles,
                    hasNewerMemos = hasNewer,
                    error = null
                )
            }
            if (!_state.value.hasNewerMemos) {
                persistTimeline(_state.value.activeAccountId, _state.value.timeline, _state.value.nextPageToken)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Silent revalidation stays quiet on failure; the cache remains visible.
        }
        revalidating = false
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
            persistTimeline(_state.value.activeAccountId, _state.value.timeline, _state.value.nextPageToken)
        }.onFailure { error -> _state.update { it.copy(error = error.message ?: "Failed to load more") } }
        _state.update { it.copy(isLoadingMore = false) }
    }

    // Memos has no dedicated search endpoint; the web app searches by passing a
    // CEL filter to listMemos, so mirror that here.
    override suspend fun search(query: String): List<Memo> {
        val trimmed = query.trim()
        val session = activeSessionOrNull() ?: return emptyList()
        val response = session.api.listMemos(pageSize = 50, filter = "content.contains(\"${celEscape(trimmed)}\")")
        val profiles = session.loadUsers(response.memos)
        _state.update { it.copy(userProfiles = it.userProfiles + profiles) }
        return response.memos
    }

    override suspend fun loadInstanceSettings(): InstanceSetting {
        val session = activeSessionOrNull() ?: error("No active account")
        return session.api.instanceSetting("GENERAL")
    }

    override suspend fun saveInstanceSettings(
        general: GeneralSetting,
        memoRelated: MemoRelatedSetting,
        workspace: WorkspaceSetting
    ) {
        val account = _state.value.activeAccount ?: error("No active account")
        val session = activeSessionOrNull() ?: error("No active account")
        session.api.updateInstanceSetting(InstanceSetting(name = "instanceSettings/GENERAL", generalSetting = general))
        session.api.updateInstanceSetting(InstanceSetting(name = "instanceSettings/MEMO_RELATED", memoRelatedSetting = memoRelated))
        session.api.updateInstanceSetting(InstanceSetting(name = "instanceSettings/WORKSPACE", workspaceSetting = workspace))
        // Custom profile (title/logo) feeds the header and account switcher; refresh the cached account.
        val refreshed = account.copy(
            siteTitle = general.customProfile?.title?.ifBlank { "Memos" } ?: "Memos",
            siteLogoUrl = general.customProfile?.logoUrl.orEmpty()
        )
        if (refreshed != account) {
            sessions[account.id] = AccountSession(refreshed)
            _state.update { current ->
                current.copy(accounts = current.accounts.map { if (it.id == refreshed.id) refreshed else it })
            }
            persistAccounts()
        }
    }

    private fun celEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

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
            _state.update {
                it.copy(
                    timeline = listOf(memo) + it.timeline,
                    hasNewerMemos = false,
                    error = null
                )
            }
            persistTimeline(_state.value.activeAccountId, _state.value.timeline, _state.value.nextPageToken)
        }.onFailure { error -> _state.update { it.copy(error = error.message ?: "Publish failed") } }
        _state.update { it.copy(isPublishing = false) }
    }

    override suspend fun openMemo(name: String) {
        val session = activeSessionOrNull() ?: return
        // Render the memo we already have right away so tapping a post responds
        // instantly; the fresh copy and its replies replace it when the network returns.
        val cached = _state.value.timeline.firstOrNull { it.name == name }
        if (cached != null) {
            _state.update { it.copy(selectedMemo = cached, selectedComments = emptyList(), error = null) }
        } else {
            setBusy(isLoading = true)
        }
        try {
            val memo = session.api.getMemo(name)
            val comments = session.api.listComments(name).memos
            _state.update { it.copy(selectedMemo = memo, selectedComments = comments, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Failed to open memo") }
        }
        setBusy(isLoading = false)
    }

    override fun closeMemo() {
        _state.update { it.copy(selectedMemo = null, selectedComments = emptyList()) }
    }

    override fun setThemeMode(mode: ThemeMode) {
        if (_state.value.themeMode == mode) return
        keyValueStore.putString(THEME_KEY, themeModeValue(mode))
        _state.update { it.copy(themeMode = mode) }
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
        val account = _state.value.activeAccount ?: return
        val session = activeSessionOrNull() ?: return
        // Serialised: a second tap that read the placeholder added below would
        // otherwise be sent as a delete of a reaction that does not exist, which
        // Memos answers with `400 invalid reaction ID "local"`.
        reactionMutex.withLock {
            // The live list, not the caller's snapshot: a placeholder has no server
            // name, so it must never be treated as a reaction to withdraw.
            val previous = (_state.value.memoNamed(memo.name)?.reactions ?: memo.reactions)
                .filterNot { it.name.endsWith(LOCAL_REACTION_SUFFIX) }
            val existing = previous.firstOrNull { it.reactionType == reactionType && it.isBy(account) }
            val optimistic = if (existing != null) {
                previous.filterNot { it.name == existing.name }
            } else {
                previous.filterNot { it.isBy(account) } + Reaction(
                    name = "${memo.name}$LOCAL_REACTION_SUFFIX",
                    creator = account.userName.ifBlank { "users/${account.username}" },
                    reactionType = reactionType
                )
            }
            _state.update { it.withMemoReactions(memo.name, optimistic) }
            try {
                if (existing != null) {
                    session.api.deleteReaction(existing.name)
                } else {
                    val reaction = session.api.upsertReaction(memo.name, reactionType)
                    val current = _state.value.memoNamed(memo.name)?.reactions.orEmpty()
                    _state.update {
                        it.withMemoReactions(
                            memo.name,
                            current.filterNot { reaction -> reaction.name.endsWith(LOCAL_REACTION_SUFFIX) || reaction.isBy(account) } + reaction
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.withMemoReactions(memo.name, previous).copy(error = e.message ?: "Reaction failed") }
            }
        }
    }

    override fun memoUrl(memo: Memo): String {
        val account = _state.value.activeAccount ?: return ""
        return memoWebUrl(account.instanceUrl, memo)
    }

    fun memoCreator(memo: Memo): User? = _state.value.userProfiles[memo.creator.substringAfterLast('/')]

    override fun siteLogoUrl(): String {
        val account = _state.value.activeAccount ?: return ""
        return accountLogoUrl(account)
    }

    override fun accountLogoUrl(account: MemosAccount): String =
        sessions[account.id]?.api?.assetUrl(account.siteLogoUrl).orEmpty()

    // Memos reports avatarUrl as an instance-relative path (or omits it); resolve
    // it against the account's base URL and fetch with session credentials since
    // Coil cannot attach the auth token itself. Avatars are small and immutable;
    // disk-cache them so timelines render instantly on relaunch.
    override suspend fun avatarBytes(user: User): ByteArray? {
        val session = activeSessionOrNull() ?: return null
        val account = _state.value.activeAccount
        return diskCachedBytes("avatar.${account?.id.orEmpty()}.${user.username}") {
            session.api.getUrlBytes(session.api.assetUrl(user.avatarUrl.ifBlank { "file/users/${user.username}/avatar" }))
        }
    }

    override suspend fun accountAvatarBytes(account: MemosAccount): ByteArray? {
        val session = sessions.getOrPut(account.id) { AccountSession(account) }
        return diskCachedBytes("avatar.${account.id}") {
            session.api.getUrlBytes(session.api.assetUrl(account.avatarUrl.ifBlank { "file/users/${account.username}/avatar" }))
        }
    }

    private suspend fun diskCachedBytes(key: String, fetch: suspend () -> ByteArray): ByteArray? {
        avatarCache[key]?.let { return it }
        keyValueStore.getString(key)?.let { encoded ->
            runCatching { Base64.decode(encoded) }.getOrNull()?.let { bytes ->
                avatarCache[key] = bytes
                return bytes
            }
        }
        // Avatar failures are cosmetic; fall back to the initial letter instead of surfacing errors.
        return try {
            fetch().also { bytes ->
                avatarCache[key] = bytes
                // Keep the encrypted store small; skip unusually large avatars.
                if (bytes.size <= MAX_CACHED_AVATAR_BYTES) {
                    runCatching { keyValueStore.putString(key, Base64.encode(bytes)) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

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
        val cached = if (active.isBlank()) null else loadCachedTimeline(active)
        return MemosAppState(
            accounts = accounts,
            activeAccountId = active,
            timeline = cached?.memos.orEmpty(),
            nextPageToken = cached?.nextPageToken.orEmpty(),
            themeMode = canonicalThemeMode(keyValueStore.getString(THEME_KEY))
        )
    }

    private fun loadCachedTimeline(accountId: String): CachedTimeline? = runCatching {
        keyValueStore.getString(TIMELINE_KEY_PREFIX + accountId)?.let { json.decodeFromString<CachedTimeline>(it) }
    }.getOrNull()

    // Cap the persisted list so the encrypted store stays small; the newest page is what matters offline.
    private fun persistTimeline(accountId: String, memos: List<Memo>, nextPageToken: String) {
        if (accountId.isBlank() || memos.isEmpty()) return
        runCatching {
            val snapshot = CachedTimeline(
                memos = memos.take(CACHED_MEMO_LIMIT),
                nextPageToken = nextPageToken,
                savedAt = Clock.System.now()
            )
            keyValueStore.putString(TIMELINE_KEY_PREFIX + accountId, json.encodeToString(snapshot))
        }
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

    override suspend fun checkForUpdate() {
        val update = try {
            val abi = deviceAbi() ?: return
            val current = AppVersion.parse(installedVersionName()) ?: return
            val skipped = keyValueStore.getString(SKIPPED_UPDATE_KEY)?.let { AppVersion.parse(it) }
            updateApi.availableUpdate(current, abi, skipped)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        _state.update { it.copy(availableUpdate = update) }
    }

    override fun skipUpdate(version: String) {
        keyValueStore.putString(SKIPPED_UPDATE_KEY, version)
        _state.update { it.copy(availableUpdate = null) }
    }

    override suspend fun refreshInstanceVersion(accountId: String) {
        val account = _state.value.accounts.firstOrNull { it.id == accountId } ?: return
        val session = sessions.getOrPut(accountId) { AccountSession(account) }
        val version = session.loadInstanceVersion()
        if (version.isBlank() || version == account.instanceVersion) return
        _state.update { current ->
            current.copy(
                accounts = current.accounts.map {
                    if (it.id == accountId) it.copy(instanceVersion = version) else it
                }
            )
        }
        persistAccounts()
    }

    override suspend fun refreshActivityStats() {
        val account = _state.value.activeAccount ?: return
        val session = activeSessionOrNull() ?: return
        val stats = try {
            session.api.userStats(account.username)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        val activity = stats.toActivityStats(deviceUtcOffsetSeconds())
        _state.update { it.copy(activity = activity) }
    }

    override suspend fun downloadUpdate(): ByteArray? {
        val update = _state.value.availableUpdate ?: return null
        val total = update.sizeBytes.takeIf { it > 0 }
        _state.update { it.copy(updateDownload = UpdateDownload(update.version, 0, total)) }
        return try {
            updateApi.download(update.downloadUrl) { read, reported ->
                _state.update { it.copy(updateDownload = UpdateDownload(update.version, read, reported ?: total)) }
            }.also {
                _state.update { it.copy(updateDownload = null) }
            }
        } catch (e: CancellationException) {
            _state.update { it.copy(updateDownload = null) }
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(updateDownload = null, error = e.message ?: "Update download failed") }
            null
        }
    }

    private inner class AccountSession(private val account: MemosAccount) {
        private var accessToken: String? = null
        private var accessTokenExpiresAt: Instant? = null
        private val cookieStorage = PersistentCookieStorage("memos.cookies.${account.id}", keyValueStore)
        val api: MemosApi = MemosApi(
            instanceUrl = account.instanceUrl,
            accessTokenProvider = { accessToken },
            refreshAccessToken = { refreshToken() },
            accessTokenExpiresAt = { accessTokenExpiresAt },
            onUnauthorized = { _state.update { it.copy(error = "Session expired for ${account.username} — sign in again") } },
            cookieStorage = cookieStorage
        )

        suspend fun signIn(username: String, password: String) = api.signIn(username, password).also {
            accessToken = it.accessToken
            accessTokenExpiresAt = it.accessTokenExpiresAt
        }

        suspend fun loadBranding(): Pair<String, String> = runCatching {
            val profile = api.generalSetting().generalSetting?.customProfile
            (profile?.title?.ifBlank { "Memos" } ?: "Memos") to (profile?.logoUrl.orEmpty())
        }.getOrDefault("Memos" to "")

        /** The instance's version, or empty when it cannot be read. */
        suspend fun loadInstanceVersion(): String = runCatching {
            api.instanceProfile().version
        }.getOrDefault("")

        suspend fun loadUsers(memos: List<Memo>): Map<String, User> = runCatching {
            api.batchGetUsers(memos.map { it.creator.substringAfterLast('/') })
                .associateBy { it.username }
        }.getOrDefault(emptyMap())

        suspend fun refreshToken(): String? = runCatching {
            val response = api.refresh()
            accessToken = response.accessToken
            accessTokenExpiresAt = response.expiresAt
            accessToken
        }.getOrNull()

        fun clearCookies() {
            cookieStorage.clear()
        }
    }

    companion object {
        private const val ACCOUNTS_KEY = "memos.accounts"
        private const val TIMELINE_KEY_PREFIX = "memos.timeline."
        private const val SKIPPED_UPDATE_KEY = "update.skippedVersion"

        /** Suffix of the placeholder a like shows before the server answers. */
        private const val LOCAL_REACTION_SUFFIX = "/reactions/local"
        private const val THEME_KEY = "ui.themeMode"
        private const val CACHED_MEMO_LIMIT = 100
        private const val MAX_CACHED_AVATAR_BYTES = 512 * 1024

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

private fun MemosAppState.memoNamed(name: String): Memo? =
    selectedMemo?.takeIf { it.name == name }
        ?: selectedComments.firstOrNull { it.name == name }
        ?: timeline.firstOrNull { it.name == name }

private fun MemosAppState.withMemoReactions(name: String, reactions: List<dev.bema.shared.data.model.Reaction>): MemosAppState =
    copy(
        timeline = timeline.replaceMemo(name) { it.copy(reactions = reactions) },
        selectedMemo = selectedMemo?.let { if (it.name == name) it.copy(reactions = reactions) else it },
        selectedComments = selectedComments.replaceMemo(name) { it.copy(reactions = reactions) }
    )

private fun dev.bema.shared.data.model.Reaction.isBy(account: MemosAccount): Boolean {
    val creator = creator.substringAfterLast('/')
    return creator.equals(account.username, ignoreCase = true) || creator == account.userName.substringAfterLast('/')
}
