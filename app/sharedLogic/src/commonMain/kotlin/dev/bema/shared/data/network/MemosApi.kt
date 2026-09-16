package dev.bema.shared.data.network

import dev.bema.shared.data.model.Attachment
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
import dev.bema.shared.data.model.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class MemosApiException(
    val status: HttpStatusCode,
    message: String
) : IllegalStateException(message)

/** HTTP client for one account on one Memos instance. */
class MemosApi(
    instanceUrl: String,
    private val accessTokenProvider: suspend () -> String?,
    private val refreshAccessToken: suspend () -> String?,
    private val onUnauthorized: suspend () -> Unit = {},
    rawHttpClient: HttpClient = createPlatformHttpClient(),
    cookieStorage: CookiesStorage = AcceptAllCookiesStorage()
) {
    private val baseUrl = normalizeInstanceUrl(instanceUrl)
    private val refreshMutex = Mutex()
    private val httpClient = rawHttpClient.config {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
        install(HttpCookies) {
            storage = cookieStorage
        }
    }

    private suspend fun request(block: suspend (String?) -> HttpResponse): HttpResponse {
        val existingToken = accessTokenProvider()
        val token = existingToken.takeUnless { it.isNullOrBlank() } ?: refreshMutex.withLock { refreshAccessToken() }
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

    private fun HttpResponse.requireSuccess(): HttpResponse {
        if (!status.isSuccess()) {
            throw MemosApiException(status, "Memos API request failed: ${status.value} ${status.description}")
        }
        return this
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
            url { api("auth", "signin") }
            jsonBody(SignInRequest(PasswordCredentials(username, password)))
        }.requireSuccess().body()

    suspend fun refresh(): RefreshTokenResponse =
        httpClient.post {
            url { api("auth", "refresh") }
            jsonBody(emptyMap<String, String>())
        }.requireSuccess().body()

    suspend fun currentUser(): User =
        request { token -> httpClient.get { url { api("auth", "me") }; auth(token) } }
            .body<CurrentUserResponse>().user

    suspend fun signOut() {
        request { token -> httpClient.post { url { api("auth", "signout") }; auth(token) } }
    }

    suspend fun instanceProfile(): InstanceProfile =
        httpClient.get { url { api("instance", "profile") } }.requireSuccess().body()

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

    suspend fun createMemo(
        content: String,
        visibility: Visibility,
        pinned: Boolean = false
    ): Memo =
        request { token ->
            httpClient.post {
                url { api("memos") }
                auth(token)
                jsonBody(MemoInput(content = content, visibility = visibility, pinned = pinned))
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
                jsonBody(UpsertReactionBody(reaction = ReactionInput(reactionType)))
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

    suspend fun getAttachmentBytes(attachment: Attachment, thumbnail: Boolean = false): ByteArray {
        if (attachment.externalLink.isNotBlank()) {
            return httpClient.get(attachment.externalLink).requireSuccess().bodyAsBytes()
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

    suspend fun close() = httpClient.close()
}

fun normalizeInstanceUrl(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    if (trimmed.isBlank()) return trimmed
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}
