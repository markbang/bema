package dev.bema.shared.data.network

import dev.bema.shared.data.model.Attachment
import dev.bema.shared.data.model.AttachmentUpload
import dev.bema.shared.data.model.BatchGetUsersRequest
import dev.bema.shared.data.model.BatchGetUsersResponse
import dev.bema.shared.data.model.InstanceSetting
import dev.bema.shared.data.model.CreateMemoShareRequestBody
import dev.bema.shared.data.model.CurrentUserResponse
import dev.bema.shared.data.model.InstanceProfile
import dev.bema.shared.data.model.LinkMetadata
import dev.bema.shared.data.model.ListMemoCommentsResponse
import dev.bema.shared.data.model.ListMemoReactionsResponse
import dev.bema.shared.data.model.ListMemoSharesResponse
import dev.bema.shared.data.model.ListMemosResponse
import dev.bema.shared.data.model.ListUserNotificationsResponse
import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.MemoInput
import dev.bema.shared.data.model.MemoPatch
import dev.bema.shared.data.model.MemoShare
import dev.bema.shared.data.model.PasswordCredentials
import dev.bema.shared.data.model.Reaction
import dev.bema.shared.data.model.ReactionInput
import dev.bema.shared.data.model.RefreshTokenResponse
import dev.bema.shared.data.model.SignInRequest
import dev.bema.shared.data.model.SignInResponse
import dev.bema.shared.data.model.UpsertReactionBody
import dev.bema.shared.data.model.User
import dev.bema.shared.data.model.UserNotification
import dev.bema.shared.data.model.UserStats
import dev.bema.shared.data.model.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** Refresh this long before the access token lapses. */
private val TOKEN_REFRESH_MARGIN = 60.seconds

class MemosApiException(
    val status: HttpStatusCode,
    message: String
) : IllegalStateException(message)

/** HTTP client for one account on one Memos instance. */
class MemosApi(
    instanceUrl: String,
    private val accessTokenProvider: suspend () -> String?,
    private val refreshAccessToken: suspend () -> String?,
    private val accessTokenExpiresAt: () -> Instant? = { null },
    private val onUnauthorized: suspend () -> Unit = {},
    private val rawHttpClient: HttpClient = createPlatformHttpClient(),
    private val cookieStorage: CookiesStorage = AcceptAllCookiesStorage()
) {
    private val baseUrl = normalizeInstanceUrl(instanceUrl)
    private val instanceOrigin = Url(baseUrl)
    private val settingsJson = Json { encodeDefaults = true; explicitNulls = false }
    private val refreshMutex = Mutex()
    private val httpClient = rawHttpClient.config {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
        install(HttpCookies) {
            storage = object : CookiesStorage {
                override suspend fun get(requestUrl: Url): List<Cookie> =
                    if (isInstanceOrigin(requestUrl)) cookieStorage.get(requestUrl) else emptyList()

                override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
                    if (isInstanceOrigin(requestUrl)) cookieStorage.addCookie(requestUrl, cookie)
                }

                override fun close() = cookieStorage.close()
            }
        }
    }.also { client ->
        client.plugin(HttpSend).intercept { request ->
            // Redirects can cross origins even when the initial URL was trusted.
            if (!isInstanceOrigin(request.url.build())) {
                request.headers.remove(HttpHeaders.Authorization)
                request.headers.remove(HttpHeaders.Cookie)
            }
            execute(request)
        }
    }

    private fun isInstanceOrigin(target: Url): Boolean = target.protocol == instanceOrigin.protocol &&
        target.host.equals(instanceOrigin.host, ignoreCase = true) && target.port == instanceOrigin.port

    private suspend fun request(block: suspend (String?) -> HttpResponse): HttpResponse {
        // Refresh ahead of expiry rather than after a 401: the access token is short
        // lived, and letting it lapse turns the next tap into a visible failure.
        val current = accessTokenProvider()
        val expiring = accessTokenExpiresAt()?.let { it <= Clock.System.now() + TOKEN_REFRESH_MARGIN } == true
        val token = if (current.isNullOrBlank() || expiring) {
            refreshMutex.withLock {
                // Several requests start at once on a cold launch and all see no
                // token. Re-read under the lock so they share one refresh: each
                // refresh rotates the refresh token server side, and a burst of them
                // can leave the app holding one the server has already retired.
                val fresh = accessTokenProvider()
                val freshExpiring = accessTokenExpiresAt()?.let { it <= Clock.System.now() + TOKEN_REFRESH_MARGIN } == true
                if (!fresh.isNullOrBlank() && !freshExpiring) fresh else refreshAccessToken() ?: fresh ?: current
            }
        } else {
            current
        }
        val first = block(token)
        if (first.status != HttpStatusCode.Unauthorized) {
            return first.requireSuccess()
        }

        val refreshed = refreshMutex.withLock { refreshAccessToken() }
        if (refreshed.isNullOrBlank()) {
            onUnauthorized()
            throw MemosApiException(HttpStatusCode.Unauthorized, "Session expired")
        }

        return block(refreshed).requireSuccess()
    }

    private suspend fun HttpResponse.requireSuccess(): HttpResponse {
        if (!status.isSuccess()) {
            // Name the request and repeat what the server said: a bare status left us
            // guessing at request shapes more than once.
            val where = "${this.request.method.value} ${this.request.url.encodedPath}"
            val detail = runCatching { bodyAsText() }.getOrNull().orEmpty().trim()
            throw MemosApiException(
                status,
                buildString {
                    append("$where failed: ${status.value} ${status.description}")
                    if (detail.isNotEmpty()) append(" — ${detail.take(400)}")
                }
            )
        }
        return this
    }

    /**
     * Connect-protocol path for a service method, e.g.
     * `memos.api.v1.AuthService/SignIn`.
     *
     * Memos mounts grpc-gateway under `/api/v1` and the Connect handlers, which is
     * where cookies are handled, under `/memos.api.v1`. The refresh cookie is both
     * written (`Set-Cookie`) and read (the gateway never forwards `Cookie` into
     * request metadata) only on the Connect path, so the session calls use it.
     */
    private fun URLBuilder.connect(service: String, method: String) {
        takeFrom(baseUrl)
        appendPathSegments("memos.api.v1.$service", method)
    }

    private fun HttpRequestBuilder.connectProtocol() {
        header("Connect-Protocol-Version", "1")
    }

    private fun URLBuilder.api(vararg path: String) {
        takeFrom(baseUrl)
        val segments = buildList {
            add("api")
            add("v1")
            path.forEach { piece ->
                addAll(piece.split('/').filter { it.isNotBlank() })
            }
        }
        appendPathSegments(*segments.toTypedArray())
    }

    private fun URLBuilder.file(vararg path: String) {
        takeFrom(baseUrl)
        val segments = buildList {
            add("file")
            path.forEach { piece ->
                addAll(piece.split('/').filter { it.isNotBlank() })
            }
        }
        appendPathSegments(*segments.toTypedArray())
    }

    private fun HttpRequestBuilder.auth(token: String?) {
        if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
    }

    private inline fun <reified T : Any> HttpRequestBuilder.jsonBody(value: T) {
        contentType(ContentType.Application.Json)
        setBody(value)
    }

    suspend fun signIn(username: String, password: String): SignInResponse =
        httpClient.post {
            url { connect("AuthService", "SignIn") }
            connectProtocol()
            jsonBody(SignInRequest(PasswordCredentials(username, password)))
        }.requireSuccess().body()

    suspend fun refresh(): RefreshTokenResponse =
        httpClient.post {
            url { connect("AuthService", "RefreshToken") }
            connectProtocol()
            jsonBody(emptyMap<String, String>())
        }.requireSuccess().body()

    suspend fun currentUser(): User =
        request { token -> httpClient.get { url { api("auth", "me") }; auth(token) } }
            .body<CurrentUserResponse>().user

    /**
     * Activity statistics for one user: the tag cloud and the timestamps behind the
     * heatmap. The path ends in `:getStats`, which is part of the route, so the
     * colon has to survive path encoding.
     */
    suspend fun userStats(username: String): UserStats =
        request { token ->
            httpClient.get {
                url { api("users", "$username:getStats") }
                auth(token)
            }
        }.body()

    suspend fun signOut() {
        request { token ->
            httpClient.post {
                url { connect("AuthService", "SignOut") }
                connectProtocol()
                auth(token)
                jsonBody(emptyMap<String, String>())
            }
        }
    }

    suspend fun instanceProfile(): InstanceProfile =
        httpClient.get { url { api("instance", "profile") } }.requireSuccess().body()

    suspend fun generalSetting(): InstanceSetting =
        request { token ->
            httpClient.get {
                url { api("instance", "settings", "GENERAL") }
                auth(token)
            }
        }.body()

    suspend fun instanceSetting(setting: String): InstanceSetting =
        request { token ->
            httpClient.get {
                url { api("instance", "settings", setting) }
                auth(token)
            }
        }.body()

    suspend fun updateInstanceSetting(setting: InstanceSetting): InstanceSetting {
        require(setting.name.startsWith("instance/settings/")) { "Invalid instance setting name" }
        // Memos replaces the whole resource (its update_mask is not applied).
        // Merge only fields this client edits into a fresh, lossless server copy.
        val current = request { token ->
            httpClient.get { url { api(setting.name) }; auth(token) }
        }.body<JsonObject>()
        val edits = settingsJson.encodeToJsonElement(setting).jsonObject
        val body = mergeSettingFields(current, edits)
        return request { token ->
            httpClient.patch {
                url { api(setting.name) }
                auth(token)
                jsonBody(body)
            }
        }.body()
    }

    suspend fun batchGetUsers(usernames: List<String>): List<dev.bema.shared.data.model.User> {
        if (usernames.isEmpty()) return emptyList()
        return request { token ->
            httpClient.post {
                url { api("users:batchGet") }
                auth(token)
                jsonBody(BatchGetUsersRequest(usernames.distinct().take(100)))
            }
        }.body<BatchGetUsersResponse>().users
    }

    fun userAvatarUrl(username: String): String =
        URLBuilder().apply { file("users", username, "avatar") }.buildString()

    fun assetUrl(value: String): String {
        if (value.isBlank() || value.startsWith("data:") || value.startsWith("http://") || value.startsWith("https://")) return value
        return URLBuilder().apply {
            takeFrom(baseUrl)
            appendPathSegments(*value.trimStart('/').split('/').filter { it.isNotBlank() }.toTypedArray())
        }.buildString()
    }

    suspend fun listMemos(
        pageSize: Int = 30,
        pageToken: String = "",
        filter: String = "",
        orderBy: String = "pinned desc, create_time desc"
    ): ListMemosResponse =
        request { token ->
            httpClient.get {
                url {
                    api("memos")
                    parameters.append("pageSize", pageSize.coerceIn(1, 1000).toString())
                    if (pageToken.isNotBlank()) parameters.append("pageToken", pageToken)
                    if (filter.isNotBlank()) parameters.append("filter", filter)
                    if (orderBy.isNotBlank()) parameters.append("orderBy", orderBy)
                }
                auth(token)
            }
        }.body()

    suspend fun getMemo(name: String): Memo =
        request { token -> httpClient.get { url { api(name) }; auth(token) } }.body()

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun createAttachment(filename: String, content: ByteArray, type: String): Attachment =
        request { token ->
            httpClient.post {
                url { api("attachments") }
                auth(token)
                // `content` is a proto bytes field, which JSON carries as base64; a
                // ByteArray would serialise to an array of numbers and be rejected.
                jsonBody(AttachmentUpload(filename, Base64.encode(content), type))
            }
        }.body()

    suspend fun createMemo(
        content: String,
        visibility: Visibility,
        pinned: Boolean = false,
        attachments: List<Attachment> = emptyList()
    ): Memo =
        request { token ->
            httpClient.post {
                url { api("memos") }
                auth(token)
                jsonBody(MemoInput(content = content, visibility = visibility, pinned = pinned, attachments = attachments.map { Attachment(name = it.name) }))
            }
        }.body()

    suspend fun updateMemo(
        memo: Memo,
        content: String? = null,
        visibility: Visibility? = null,
        pinned: Boolean? = null
    ): Memo {
        val mask = buildList {
            if (content != null) add("content")
            if (visibility != null) add("visibility")
            if (pinned != null) add("pinned")
        }
        if (mask.isEmpty()) return memo
        return request { token ->
            httpClient.patch {
                url {
                    api(memo.name)
                    parameters.append("updateMask", mask.joinToString(","))
                }
                auth(token)
                jsonBody(MemoPatch(name = memo.name, content = content, visibility = visibility, pinned = pinned))
            }
        }.body()
    }

    suspend fun deleteMemo(name: String, force: Boolean = false) {
        request { token ->
            httpClient.delete {
                url {
                    api(name)
                    if (force) parameters.append("force", "true")
                }
                auth(token)
            }
        }
    }

    suspend fun listComments(
        name: String,
        pageSize: Int = 50,
        pageToken: String = ""
    ): ListMemoCommentsResponse =
        request { token ->
            httpClient.get {
                url {
                    api(name, "comments")
                    parameters.append("pageSize", pageSize.coerceIn(1, 1000).toString())
                    if (pageToken.isNotBlank()) parameters.append("pageToken", pageToken)
                    parameters.append("orderBy", "create_time asc")
                }
                auth(token)
            }
        }.body()

    suspend fun createComment(name: String, content: String, visibility: Visibility): Memo =
        request { token ->
            httpClient.post {
                url { api(name, "comments") }
                auth(token)
                jsonBody(MemoInput(content = content, visibility = visibility))
            }
        }.body()

    suspend fun listReactions(name: String): ListMemoReactionsResponse =
        request { token -> httpClient.get { url { api(name, "reactions") }; auth(token) } }.body()

    suspend fun upsertReaction(name: String, reactionType: String): Reaction =
        request { token ->
            httpClient.post {
                url { api(name, "reactions") }
                auth(token)
                jsonBody(UpsertReactionBody(reaction = ReactionInput(contentId = name, reactionType = reactionType)))
            }
        }.body()

    suspend fun deleteReaction(reactionName: String) {
        request { token -> httpClient.delete { url { api(reactionName) }; auth(token) } }
    }

    suspend fun createMemoShare(parent: String): MemoShare =
        request { token ->
            httpClient.post {
                url { api(parent, "shares") }
                auth(token)
                jsonBody(CreateMemoShareRequestBody())
            }
        }.body()

    suspend fun listMemoShares(parent: String): ListMemoSharesResponse =
        request { token -> httpClient.get { url { api(parent, "shares") }; auth(token) } }.body()

    suspend fun deleteMemoShare(shareName: String) {
        request { token -> httpClient.delete { url { api(shareName) }; auth(token) } }
    }

    suspend fun getSharedMemo(shareToken: String): Memo =
        httpClient.get { url { api("shares", shareToken, "memo") } }.requireSuccess().body()

    suspend fun getLinkMetadata(url: String): LinkMetadata =
        request { token ->
            httpClient.get {
                url {
                    api("memos", "-", "linkMetadata")
                    parameters.append("url", url)
                }
                auth(token)
            }
        }.body()

    suspend fun listNotifications(parentUserName: String, pageSize: Int = 50): ListUserNotificationsResponse =
        request { token ->
            httpClient.get {
                url {
                    api(parentUserName, "notifications")
                    parameters.append("pageSize", pageSize.coerceIn(1, 1000).toString())
                }
                auth(token)
            }
        }.body()

    suspend fun archiveNotification(notification: UserNotification): UserNotification =
        request { token ->
            httpClient.patch {
                url {
                    api(notification.name)
                    parameters.append("updateMask", "status")
                }
                auth(token)
                jsonBody(notification.copy(status = "ARCHIVED"))
            }
        }.body()

    suspend fun getUrlBytes(url: String): ByteArray {
        val target = Url(url)
        require(target.protocol.name == "http" || target.protocol.name == "https") { "Unsupported resource URL" }
        val sameOrigin = isInstanceOrigin(target)
        // The raw client has no account cookie plugin either: cookies ignore ports,
        // so omitting only the Bearer header would still leak credentials cross-origin.
        return if (sameOrigin) {
            request { token -> httpClient.get(url) { auth(token) } }.bodyAsBytes()
        } else {
            rawHttpClient.get(url).requireSuccess().bodyAsBytes()
        }
    }

    suspend fun getAttachmentBytes(attachment: Attachment, thumbnail: Boolean = false): ByteArray {
        if (attachment.externalLink.isNotBlank()) {
            return rawHttpClient.get(attachment.externalLink).requireSuccess().bodyAsBytes()
        }
        return request { token ->
            httpClient.get {
                url {
                    file("attachments", attachment.uid, attachment.filename)
                    if (thumbnail) parameters.append("thumbnail", "true")
                }
                auth(token)
            }
        }.bodyAsBytes()
    }

    fun attachmentUrl(attachment: Attachment, thumbnail: Boolean = false, motion: Boolean = false): String {
        if (attachment.externalLink.isNotBlank()) return attachment.externalLink
        return URLBuilder().apply {
            file("attachments", attachment.uid, attachment.filename)
            if (thumbnail) parameters.append("thumbnail", "true")
            if (motion) parameters.append("motion", "true")
        }.buildString()
    }

    suspend fun close() {
        httpClient.close()
        rawHttpClient.close()
    }
}

private fun mergeSettingFields(current: JsonObject, edits: JsonObject): JsonObject = JsonObject(
    current.toMutableMap().apply {
        edits.forEach { (key, value) ->
            val previous = this[key]
            this[key] = if (previous is JsonObject && value is JsonObject) mergeSettingFields(previous, value) else value
        }
    }
)

fun normalizeInstanceUrl(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    if (trimmed.isBlank()) return trimmed
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}
