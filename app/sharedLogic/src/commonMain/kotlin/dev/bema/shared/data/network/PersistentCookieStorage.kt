package dev.bema.shared.data.network

import dev.bema.shared.data.storage.KeyValueStore
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PersistentCookieStorage(
    private val storageKey: String,
    private val store: KeyValueStore
) : CookiesStorage {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private var cookies: MutableList<StoredCookie> = loadCookies().toMutableList()

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        val now = getTimeMillis()
        val valid = cookies.filter { it.expiresAtMillis == null || it.expiresAtMillis > now }
        if (valid.size != cookies.size) {
            cookies = valid.toMutableList()
            saveCookies()
        }
        valid.filter { it.shouldSendTo(requestUrl) }.map { it.toCookie() }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = mutex.withLock {
        val stored = StoredCookie.from(requestUrl, cookie)
        cookies.removeAll { it.name == stored.name && it.domain == stored.domain && it.path == stored.path }

        val expiresAt = stored.expiresAtMillis
        if (cookie.value.isNotEmpty() && (expiresAt == null || expiresAt > getTimeMillis())) {
            cookies.add(stored)
        }
        saveCookies()
    }

    override fun close() = Unit

    fun clear() {
        cookies.clear()
        store.remove(storageKey)
    }

    private fun loadCookies(): List<StoredCookie> {
        val raw = store.getString(storageKey) ?: return emptyList()
        return runCatching { json.decodeFromString<List<StoredCookie>>(raw) }.getOrDefault(emptyList())
    }

    private fun saveCookies() {
        if (cookies.isEmpty()) {
            store.remove(storageKey)
        } else {
            store.putString(storageKey, json.encodeToString(cookies))
        }
    }
}

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val hostOnly: Boolean = true,
    val path: String? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val expiresAtMillis: Long? = null
) {
    fun shouldSendTo(url: Url): Boolean {
        if (secure && url.protocol.name != "https") return false
        val host = url.host.lowercase()
        val cookieDomain = domain?.removePrefix(".")?.lowercase()
        if (cookieDomain != null) {
            val domainMatches = if (hostOnly) host == cookieDomain else host == cookieDomain || host.endsWith(".$cookieDomain")
            if (!domainMatches) return false
        }
        val requestPath = url.encodedPath.ifBlank { "/" }
        val cookiePath = path ?: "/"
        return requestPath == cookiePath ||
            requestPath.startsWith(cookiePath) && (cookiePath.endsWith('/') || requestPath.getOrNull(cookiePath.length) == '/')
    }

    fun toCookie(): Cookie = Cookie(
        name = name,
        value = value,
        domain = domain.takeUnless { hostOnly },
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        expires = expiresAtMillis?.let(::GMTDate)
    )

    companion object {
        fun from(requestUrl: Url, cookie: Cookie): StoredCookie {
            val expiresAt = cookie.expires?.timestamp
                ?: cookie.maxAge?.let { maxAge ->
                    if (maxAge <= 0) 0L else getTimeMillis() + maxAge * 1000L
                }
            return StoredCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain ?: requestUrl.host,
                hostOnly = cookie.domain == null,
                path = cookie.path ?: "/",
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                expiresAtMillis = expiresAt
            )
        }
    }
}
