package dev.bema.shared

import com.sun.net.httpserver.HttpServer
import dev.bema.shared.data.model.CustomProfile
import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.StorageSetting
import dev.bema.shared.data.network.MemosApi
import dev.bema.shared.data.network.MemosApiException
import dev.bema.shared.data.network.PersistentCookieStorage
import dev.bema.shared.data.session.MemosAccount
import dev.bema.shared.data.session.MemosTimelineController
import dev.bema.shared.data.storage.KeyValueStore
import io.ktor.http.Cookie
import io.ktor.http.Url
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControllerRegressionTest {
    @Test
    fun blankSignInReportsValidationInsteadOfThrowing() = runBlocking {
        val controller = MemosTimelineController(TestStore())
        controller.addAccount("", "", "")
        assertEquals("Instance URL is required", controller.state.value.error)
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun failedPublishAndReplyThrowAndCanBeRetried() = runBlocking {
        var fail = true
        LocalMemosServer { request ->
            when {
                request.method == "POST" && request.path.startsWith("/api/v1/memos") ->
                    if (fail) Response(503, "unavailable") else Response(body = memoJson("created"))
                request.path == "/api/v1/memos/parent" -> Response(body = memoJson("parent"))
                else -> null
            }
        }.use { server ->
            val controller = controller(server)
            assertFailsWith<MemosApiException> { controller.publish("Keep this draft") }
            assertFalse(controller.state.value.isPublishing)
            assertTrue(controller.state.value.timeline.isEmpty())
            controller.openMemo("memos/parent")
            assertFailsWith<MemosApiException> { controller.comment("Keep this reply") }
            assertEquals("memos/parent", controller.state.value.selectedMemo?.name)
            assertTrue(controller.state.value.selectedComments.isEmpty())
            fail = false
            controller.publish("Keep this draft")
            controller.comment("Keep this reply")
            assertEquals("memos/created", controller.state.value.timeline.single().name)
            assertEquals(1, controller.state.value.selectedComments.size)
        }
    }

    @Test
    fun oldRefreshCannotOverwriteAnAccountEvenAfterSwitchingBack() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        LocalMemosServer { request ->
            if (request.path == "/api/v1/memos") {
                if (requests.incrementAndGet() == 1) {
                    entered.complete(Unit)
                    runBlocking { release.await() }
                    Response(body = page("old"))
                } else Response(body = page("fresh"))
            } else null
        }.use { a ->
            LocalMemosServer { null }.use { b ->
                val controller = controller(a, b)
                val old = async(start = CoroutineStart.UNDISPATCHED) { controller.refreshTimeline() }
                withTimeout(5_000) { entered.await() }
                controller.selectAccount("b")
                controller.selectAccount("a")
                release.complete(Unit)
                old.await()
                assertEquals("memos/fresh", controller.state.value.timeline.single().name)
                assertFalse(controller.state.value.isLoading)
            }
        }
    }

    @Test
    fun oldPageCannotEnterAnotherAccountsTimelineOrCache() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        LocalMemosServer { request ->
            if (request.path != "/api/v1/memos") null
            else if (request.query["pageToken"] == "page-2") {
                entered.complete(Unit)
                runBlocking { release.await() }
                Response(body = page("a-late"))
            } else Response(body = page("a", "page-2"))
        }.use { a ->
            LocalMemosServer { request ->
                if (request.path == "/api/v1/memos") Response(body = page("b")) else null
            }.use { b ->
                val store = TestStore()
                val controller = controller(a, b, store)
                controller.refreshTimeline()
                val old = async(start = CoroutineStart.UNDISPATCHED) { controller.loadMore() }
                withTimeout(5_000) { entered.await() }
                controller.selectAccount("b")
                release.complete(Unit)
                old.await()
                assertEquals("memos/b", controller.state.value.timeline.single().name)
                assertFalse(controller.state.value.isLoadingMore)
                assertEquals("memos/b", MemosTimelineController(store).state.value.timeline.single().name)
            }
        }
    }

    @Test
    fun switchingAccountsClearsDayAndActivityState() = runBlocking {
        LocalMemosServer { null }.use { a ->
            LocalMemosServer { null }.use { b ->
                val controller = controller(a, b)
                controller.refreshActivityStats()
                controller.showDay(20_000)
                assertNotNull(controller.state.value.activity)
                assertNotNull(controller.state.value.timelineFilter)
                controller.selectAccount("b")
                assertNull(controller.state.value.activity)
                assertNull(controller.state.value.timelineFilter)
                assertTrue(b.requests.filter { it.path == "/api/v1/memos" }.all { it.query["filter"] == null })
            }
        }
    }

    @Test
    fun closedOrSupersededDetailIgnoresItsLateResponse() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        LocalMemosServer { request ->
            when (request.path) {
                "/api/v1/memos/old" -> {
                    entered.complete(Unit)
                    runBlocking { release.await() }
                    Response(body = memoJson("old"))
                }
                "/api/v1/memos/new" -> Response(body = memoJson("new"))
                else -> null
            }
        }.use { server ->
            val controller = controller(server)
            val old = async(start = CoroutineStart.UNDISPATCHED) { controller.openMemo("memos/old") }
            withTimeout(5_000) { entered.await() }
            controller.closeMemo()
            controller.openMemo("memos/new")
            release.complete(Unit)
            old.await()
            assertEquals("memos/new", controller.state.value.selectedMemo?.name)
            controller.closeMemo()
            assertNull(controller.state.value.selectedMemo)
        }
    }

    @Test
    fun returningFromDetailBeforeItsRequestCompletesDoesNotReopenIt() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        LocalMemosServer { request ->
            if (request.path == "/api/v1/memos/old") {
                entered.complete(Unit)
                runBlocking { release.await() }
                Response(body = memoJson("old"))
            } else null
        }.use { server ->
            val controller = controller(server)
            val old = async(start = CoroutineStart.UNDISPATCHED) { controller.openMemo("memos/old") }
            withTimeout(5_000) { entered.await() }
            controller.closeMemo()
            release.complete(Unit)
            old.await()
            assertNull(controller.state.value.selectedMemo)
            assertTrue(controller.state.value.selectedComments.isEmpty())
        }
    }

    @Test
    fun paginationAndRevalidationKeepTheDayFilterOutOfTheDefaultCache() = runBlocking {
        LocalMemosServer { request ->
            if (request.path != "/api/v1/memos") null
            else if (request.query["filter"] == null) Response(body = page("all"))
            else if (request.query["pageToken"] == "day-2") Response(body = page("day-second"))
            else Response(body = page("day-first", "day-2"))
        }.use { server ->
            val store = TestStore()
            val controller = controller(server, store = store)
            controller.refreshTimeline()
            controller.showDay(20_000)
            val filter = controller.state.value.timelineFilter!!.cel
            controller.loadMore()
            assertEquals(2, controller.state.value.timeline.size)
            controller.revalidateTimeline()
            val filtered = server.requests.filter { it.path == "/api/v1/memos" }.drop(1)
            assertEquals(3, filtered.size)
            assertTrue(filtered.all { it.query["filter"] == filter })
            assertEquals("memos/all", MemosTimelineController(store).state.value.timeline.single().name)
        }
    }

    @Test
    fun cancelledRevalidationDoesNotPermanentlyBlockFutureChecks() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        LocalMemosServer { request ->
            if (request.path != "/api/v1/memos") null
            else {
                if (requests.incrementAndGet() == 1) {
                    entered.complete(Unit)
                    runBlocking { release.await() }
                }
                Response(body = page("fresh"))
            }
        }.use { server ->
            val controller = controller(server)
            val first = async { controller.revalidateTimeline() }
            withTimeout(5_000) { entered.await() }
            first.cancelAndJoin()
            release.complete(Unit)
            controller.revalidateTimeline()
            assertEquals("memos/fresh", controller.state.value.timeline.single().name)
        }
    }

    @Test
    fun settingsLoadRealGroupsAndPreserveUneditedServerFieldsOnSave() = runBlocking {
        LocalMemosServer { request ->
            when {
                request.method == "PATCH" -> Response(body = request.body)
                request.path == "/api/v1/instance/settings/GENERAL" -> Response(body = """{
                    "name":"instance/settings/GENERAL",
                    "generalSetting":{"customProfile":{"title":"Old","description":"About","logoUrl":"","serverOnly":"keep"},
                    "disallowUserRegistration":true,"disallowPasswordAuth":true,"additionalScript":"keep-script"}}
                """)
                request.path == "/api/v1/instance/settings/STORAGE" -> Response(body = """{
                    "name":"instance/settings/STORAGE",
                    "storageSetting":{"uploadSizeLimitMb":"64","storageType":"S3","s3Config":{"bucket":"keep-bucket"}}}
                """)
                else -> null
            }
        }.use { server ->
            val controller = controller(server)
            val loaded = controller.loadInstanceSettings()
            assertEquals(64, loaded.storageSetting?.uploadSizeLimitMb)
            assertEquals(true, loaded.generalSetting?.disallowPasswordAuth)
            controller.saveInstanceSettings(
                loaded.generalSetting!!.copy(customProfile = CustomProfile(title = "New"), disallowUserRegistration = false),
                StorageSetting(uploadSizeLimitMb = 32)
            )
            val patches = server.requests.filter { it.method == "PATCH" }
            assertEquals(listOf("/api/v1/instance/settings/GENERAL", "/api/v1/instance/settings/STORAGE"), patches.map { it.path })
            val general = Json.parseToJsonElement(patches[0].body).jsonObject.getValue("generalSetting").jsonObject
            assertEquals(JsonPrimitive(false), general["disallowUserRegistration"])
            assertEquals(JsonPrimitive("keep-script"), general["additionalScript"])
            assertEquals(JsonPrimitive("keep"), general.getValue("customProfile").jsonObject["serverOnly"])
            val storage = Json.parseToJsonElement(patches[1].body).jsonObject.getValue("storageSetting").jsonObject
            assertEquals(JsonPrimitive("keep-bucket"), storage.getValue("s3Config").jsonObject["bucket"])
        }
    }

    @Test
    fun avatarRedirectsDoNotForwardAccountCredentials() = runBlocking {
        LocalMemosServer { Response(body = "image") }.use { external ->
            LocalMemosServer { Response(302, "", mapOf("Location" to "${external.url}/avatar")) }.use { origin ->
                val cookies = PersistentCookieStorage("cookies", TestStore())
                cookies.addCookie(Url(origin.url), Cookie("session", "private-cookie", path = "/"))
                val api = MemosApi(origin.url, { "private-token" }, { error("Must not refresh") }, cookieStorage = cookies)
                try {
                    api.getUrlBytes("${origin.url}/avatar")
                    assertNull(external.requests.single().authorization)
                    assertNull(external.requests.single().cookie)
                } finally {
                    api.close()
                }
            }
        }
    }

    @Test
    fun externalAvatarsReceiveNeitherBearerTokenNorAccountCookies() = runBlocking {
        LocalMemosServer { Response(body = "image") }.use { origin ->
            LocalMemosServer { Response(body = "image") }.use { external ->
                val cookies = PersistentCookieStorage("cookies", TestStore())
                cookies.addCookie(Url(origin.url), Cookie("session", "private-cookie", path = "/"))
                val api = MemosApi(origin.url, { "private-token" }, { error("Must not refresh") }, cookieStorage = cookies)
                try {
                    api.getUrlBytes("${origin.url}/avatar")
                    api.getUrlBytes("${external.url}/avatar")
                    assertEquals("Bearer private-token", origin.requests.single().authorization)
                    assertTrue(origin.requests.single().cookie.orEmpty().contains("private-cookie"))
                    // Same host, different port: cookie rules alone would not isolate this.
                    assertNull(external.requests.single().authorization)
                    assertNull(external.requests.single().cookie)
                } finally {
                    api.close()
                }
            }
        }
    }
}

private class TestStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

private fun controller(a: LocalMemosServer, b: LocalMemosServer? = null, store: TestStore = TestStore()): MemosTimelineController {
    val accounts = listOfNotNull(
        MemosAccount(id = "a", instanceUrl = a.url, username = "alice"),
        b?.let { MemosAccount(id = "b", instanceUrl = it.url, username = "bob") }
    )
    store.putString("memos.accounts", """{"activeAccountId":"a","accounts":${Json.encodeToString(accounts)}}""")
    return MemosTimelineController(store)
}

private fun memoJson(id: String): String = Json.encodeToString(Memo(name = "memos/$id", creator = "users/alice", content = id))
private fun page(id: String, token: String = ""): String = """{"memos":[${memoJson(id)}],"nextPageToken":"$token"}"""

private data class Request(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val body: String,
    val authorization: String?,
    val cookie: String?
)
private data class Response(val status: Int = 200, val body: String, val headers: Map<String, String> = emptyMap())

private class LocalMemosServer(handler: (Request) -> Response?) : AutoCloseable {
    val requests = CopyOnWriteArrayList<Request>()
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val url: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            exchange.use {
                val parsed = Url("$url${exchange.requestURI}")
                val request = Request(
                    exchange.requestMethod,
                    exchange.requestURI.path,
                    parsed.parameters.entries().associate { it.key to it.value.first() },
                    exchange.requestBody.bufferedReader().readText(),
                    exchange.requestHeaders.getFirst("Authorization"),
                    exchange.requestHeaders.getFirst("Cookie")
                )
                requests.add(request)
                val response = handler(request) ?: when {
                    request.path.endsWith("/RefreshToken") -> Response(body = """{"accessToken":"test-token"}""")
                    request.path == "/api/v1/users:batchGet" -> Response(body = """{"users":[]}""")
                    else -> Response(body = "{}")
                }
                val bytes = response.body.toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                response.headers.forEach { (name, value) -> exchange.responseHeaders.set(name, value) }
                exchange.sendResponseHeaders(response.status, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        server.start()
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}
