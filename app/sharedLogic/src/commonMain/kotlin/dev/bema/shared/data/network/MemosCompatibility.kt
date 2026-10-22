package dev.bema.shared.data.network

import kotlinx.serialization.Serializable

/**
 * Which URL shape an instance speaks.
 *
 * Memos 26.10 removed the `v1` marker from both the REST routes and the Connect
 * package. Everything before that (0.22–0.31) uses the `v1` shape.
 */
@Serializable
enum class MemosApiSurface {
    V1,
    MODERN;

    val apiPath: String get() = if (this == V1) "api/v1" else "api"
    val connectPackage: String get() = if (this == V1) "memos.api.v1" else "memos.api"
    val other: MemosApiSurface get() = if (this == V1) MODERN else V1
}

/** A version as reported by `instance/profile` or `workspace/profile`. */
data class MemosVersion(
    val major: Int,
    val minor: Int,
    val patch: Int = 0
) : Comparable<MemosVersion> {
    override fun compareTo(other: MemosVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    val display: String get() = "$major.$minor.$patch"

    companion object {
        fun parse(value: String): MemosVersion? {
            val core = value.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            if (core.isBlank()) return null
            val numbers = core.split('.').map { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
            }
            if (numbers.isEmpty()) return null
            return MemosVersion(
                major = numbers.getOrElse(0) { 0 },
                minor = numbers.getOrElse(1) { 0 },
                patch = numbers.getOrElse(2) { 0 }
            )
        }
    }
}

internal val MEMOS_0_26 = MemosVersion(0, 26)
internal val MEMOS_0_27 = MemosVersion(0, 27)
internal val MEMOS_0_30 = MemosVersion(0, 30)
internal val MEMOS_0_31 = MemosVersion(0, 31)
internal val MEMOS_26_10 = MemosVersion(26, 10)

/** The version-dependent request and response details this client relies on. */
data class MemosApiCapabilities(
    val surface: MemosApiSurface = MemosApiSurface.V1,
    /** 26.10 renamed `ListMemoCommentsResponse.memos` to `comments`. */
    val commentsUseTypedField: Boolean = false,
    /** `BatchGetUsers` only exists from 0.30. */
    val batchUsersSupported: Boolean = true,
    /** 26.10 takes `names` (`users/{user}`) instead of bare `usernames`. */
    val batchUsersUseResourceNames: Boolean = false,
    /** 0.31 removed `Reaction.content_id`; 0.26–0.30 require it. */
    val reactionIncludesContentId: Boolean = true,
    /** 26.10 renamed `UserStats.tag_count` to `tag_counts`. */
    val userStatsUsesTagCounts: Boolean = false,
    /** 26.10 wraps the PATCH body in `instanceSetting`. */
    val instanceSettingUpdateIsWrapped: Boolean = false,
    /** 26.10 renamed both token expiry fields to `access_token_expire_time`. */
    val accessTokenUsesExpireTime: Boolean = false,
    /** Live Photo / motion photo metadata exists from 0.27. */
    val motionMediaSupported: Boolean = false,
    /** 0.31 added bounded-chunk `UploadAttachment`. */
    val chunkedUploadSupported: Boolean = false
) {
    companion object {
        fun forVersion(version: MemosVersion?, surface: MemosApiSurface): MemosApiCapabilities {
            if (version == null) {
                val modern = surface == MemosApiSurface.MODERN
                return MemosApiCapabilities(
                    surface = surface,
                    commentsUseTypedField = modern,
                    batchUsersUseResourceNames = modern,
                    reactionIncludesContentId = !modern,
                    userStatsUsesTagCounts = modern,
                    instanceSettingUpdateIsWrapped = modern,
                    accessTokenUsesExpireTime = modern,
                    motionMediaSupported = modern,
                    chunkedUploadSupported = modern
                )
            }
            if (version >= MEMOS_26_10) {
                return MemosApiCapabilities(
                    surface = MemosApiSurface.MODERN,
                    commentsUseTypedField = true,
                    batchUsersSupported = true,
                    batchUsersUseResourceNames = true,
                    reactionIncludesContentId = false,
                    userStatsUsesTagCounts = true,
                    instanceSettingUpdateIsWrapped = true,
                    accessTokenUsesExpireTime = true,
                    motionMediaSupported = true,
                    chunkedUploadSupported = true
                )
            }
            return MemosApiCapabilities(
                surface = MemosApiSurface.V1,
                batchUsersSupported = version >= MEMOS_0_30,
                reactionIncludesContentId = version < MEMOS_0_31,
                userStatsUsesTagCounts = false,
                motionMediaSupported = version >= MEMOS_0_27,
                chunkedUploadSupported = version >= MEMOS_0_31
            )
        }
    }
}

internal fun unsupportedVersionMessage(version: String?): String =
    if (version.isNullOrBlank()) {
        "This instance does not expose a Memos 0.26+ API. Update Memos and try again."
    } else {
        "Memos $version is not supported. Bema requires Memos 0.26 or newer."
    }
