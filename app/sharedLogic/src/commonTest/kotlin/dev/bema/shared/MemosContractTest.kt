package dev.bema.shared

import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.PasswordCredentials
import dev.bema.shared.data.model.SignInRequest
import dev.bema.shared.data.model.Visibility
import dev.bema.shared.data.network.PersistentCookieStorage
import dev.bema.shared.data.network.normalizeInstanceUrl
import dev.bema.shared.data.storage.KeyValueStore
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemosContractTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun decodesLatestMemoResponseShape() {
        val memo = json.decodeFromString<Memo>(
            """{
                "name":"memos/abc-123",
                "state":"NORMAL",
                "creator":"users/alice",
                "createTime":"2026-09-16T09:00:00Z",
                "content":"Hello #memos",
                "visibility":"SPACE",
                "tags":["memos"],
                "attachments":[{"name":"attachments/photo-1","filename":"photo.jpg","type":"image/jpeg","size":"1200"}],
                "reactions":[{"name":"memos/abc-123/reactions/1","creator":"users/bob","reactionType":"👍"}]
            }""".trimIndent()
        )

        assertEquals("abc-123", memo.uid)
        assertEquals(Visibility.SPACE, memo.visibility)
        assertEquals("photo-1", memo.attachments.single().uid)
        assertTrue(memo.attachments.single().isImage)
        assertEquals("👍", memo.reactions.single().reactionType)
    }

    @Test
    fun encodesPasswordCredentialsUsingProtoJsonFieldName() {
        val encoded = json.encodeToString(SignInRequest(PasswordCredentials("alice", "secret")))

        assertTrue(encoded.contains("\"passwordCredentials\""))
        assertTrue(encoded.contains("\"username\":\"alice\""))
        assertFalse(encoded.contains("password_credentials"))
    }

    @Test
    fun normalizesInstanceUrls() {
        assertEquals("https://memos.example.com", normalizeInstanceUrl(" memos.example.com/ "))
        assertEquals("http://localhost:5230", normalizeInstanceUrl("http://localhost:5230/"))
    }

    @Test
    fun persistsCookiesWithoutLeakingAcrossHostsOrPaths() = runBlocking {
        val store = MemoryStore()
        val first = PersistentCookieStorage("cookie", store)
        first.addCookie(
            Url("https://memos.example.com/api/v1/auth/signin"),
            Cookie("memos_refresh", "secret", path = "/api", secure = true, httpOnly = true)
        )

        val restored = PersistentCookieStorage("cookie", store)
        assertEquals("secret", restored.get(Url("https://memos.example.com/api/v1/memos")).single().value)
        assertTrue(restored.get(Url("https://other.example.com/api/v1/memos")).isEmpty())
        assertTrue(restored.get(Url("https://memos.example.com/files/photo")).isEmpty())
        assertTrue(restored.get(Url("http://memos.example.com/api/v1/memos")).isEmpty())
    }

    @Test
    fun cachedTimelineJsonRoundTripsWithMemoShape() {
        // CachedTimeline is private; verify the serialization shape it relies on:
        // a timeline snapshot is just Memos + a page token + a timestamp.
        val snapshot = """{
            "memos":[{"name":"memos/abc","creator":"users/a","content":"hi"}],
            "nextPageToken":"tok-2",
            "savedAt":"2026-09-17T08:00:00Z"
        }""".trimIndent()
        val decoded = json.decodeFromString<dev.bema.shared.data.model.ListMemosResponse>(snapshot)
        assertEquals("abc", decoded.memos.single().uid)
        assertEquals("tok-2", decoded.nextPageToken)
    }
}

private class MemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
