package dev.bema.shared

import dev.bema.shared.data.model.ListMemoCommentsResponse
import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.RefreshTokenResponse
import dev.bema.shared.data.model.SignInResponse
import dev.bema.shared.data.model.UserStats
import dev.bema.shared.data.network.MemosApiCapabilities
import dev.bema.shared.data.network.MemosApiSurface
import dev.bema.shared.data.network.MemosVersion
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemosCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun parsesReleaseAndCalendarVersions() {
        assertEquals(MemosVersion(0, 30, 1), MemosVersion.parse("v0.30.1"))
        assertEquals(MemosVersion(26, 10), MemosVersion.parse("26.10-rc.1"))
        assertEquals(MemosVersion(0, 22, 0), MemosVersion.parse("0.22.0"))
        assertNull(MemosVersion.parse("dev"))
    }

    @Test
    fun capabilitiesFollowTheBreakingChanges() {
        val old = MemosApiCapabilities.forVersion(MemosVersion(0, 26), MemosApiSurface.V1)
        assertFalse(old.batchUsersSupported)
        assertTrue(old.reactionIncludesContentId)

        val reactionChange = MemosApiCapabilities.forVersion(MemosVersion(0, 31), MemosApiSurface.V1)
        assertTrue(reactionChange.batchUsersSupported)
        assertFalse(reactionChange.reactionIncludesContentId)
        assertTrue(reactionChange.motionMediaSupported)
        assertTrue(reactionChange.chunkedUploadSupported)

        val motionOnly = MemosApiCapabilities.forVersion(MemosVersion(0, 30), MemosApiSurface.V1)
        assertTrue(motionOnly.motionMediaSupported)
        assertFalse(motionOnly.chunkedUploadSupported)

        val modern = MemosApiCapabilities.forVersion(MemosVersion(26, 10), MemosApiSurface.V1)
        assertEquals(MemosApiSurface.MODERN, modern.surface)
        assertTrue(modern.commentsUseTypedField)
        assertTrue(modern.batchUsersUseResourceNames)
        assertTrue(modern.userStatsUsesTagCounts)
        assertTrue(modern.instanceSettingUpdateIsWrapped)
        assertTrue(modern.accessTokenUsesExpireTime)
    }

    @Test
    fun resolvesTheVideoHalfOfALivePhoto() {
        val memo = json.decodeFromString<Memo>(
            """{"name":"memos/1","attachments":[
                {"name":"attachments/still","filename":"a.heic","type":"image/heic",
                 "motionMedia":{"family":"APPLE_LIVE_PHOTO","role":"STILL","groupId":"g"}},
                {"name":"attachments/video","filename":"a.mov","type":"video/quicktime",
                 "motionMedia":{"family":"APPLE_LIVE_PHOTO","role":"VIDEO","groupId":"g"}}
            ]}"""
        )
        val still = memo.attachments.first()
        assertEquals("attachments/video", memo.motionTargetFor(still)?.name)
    }

    @Test
    fun decodesModernFieldNames() {
        val comments = json.decodeFromString<ListMemoCommentsResponse>(
            """{"comments":[{"name":"memos/a","content":"reply"}]}"""
        )
        assertEquals("memos/a", comments.items.single().name)
        assertEquals(emptyList<Memo>(), comments.memos)

        val signIn = json.decodeFromString<SignInResponse>(
            """{"accessToken":"t","accessTokenExpireTime":"2026-10-22T00:00:00Z"}"""
        )
        assertNotNull(signIn.expiresAt)

        val refresh = json.decodeFromString<RefreshTokenResponse>(
            """{"accessToken":"t","accessTokenExpireTime":"2026-10-22T00:00:00Z"}"""
        )
        assertNotNull(refresh.expires)

        val stats = json.decodeFromString<UserStats>(
            """{"tagCounts":{"work":2},"memoCreatedTimestamps":["2026-10-22T00:00:00Z"]}"""
        )
        assertEquals(mapOf("work" to 2), stats.effectiveTagCounts)
        assertEquals(1, stats.effectiveMemoTimestamps.size)
    }
}
