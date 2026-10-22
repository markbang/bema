package dev.bema.shared.data.model

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class MemoState {
    @SerialName("STATE_UNSPECIFIED")
    UNSPECIFIED,
    @SerialName("NORMAL")
    NORMAL,
    @SerialName("ARCHIVED")
    ARCHIVED
}

@Serializable
enum class Visibility {
    @SerialName("VISIBILITY_UNSPECIFIED")
    UNSPECIFIED,
    @SerialName("PRIVATE")
    PRIVATE,
    @SerialName("PROTECTED")
    PROTECTED,
    @SerialName("PUBLIC")
    PUBLIC,
    @SerialName("SPACE")
    SPACE
}

@Serializable
data class Memo(
    val name: String = "",
    val state: MemoState = MemoState.NORMAL,
    val creator: String = "",
    val createTime: Instant? = null,
    val updateTime: Instant? = null,
    val content: String = "",
    val visibility: Visibility = Visibility.PRIVATE,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val relations: List<MemoRelation> = emptyList(),
    val reactions: List<Reaction> = emptyList(),
    val property: MemoProperty? = null,
    val parent: String? = null,
    val snippet: String = "",
    val location: Location? = null,
    val space: String? = null
) {
    val uid: String get() = name.substringAfterLast('/')

    /**
     * The attachment that holds this motion photo's video: the attachment
     * itself for an Android container, or the paired video for an Apple Live
     * Photo.
     */
    fun motionTargetFor(attachment: Attachment): Attachment? {
        val motion = attachment.motionMedia ?: return null
        return when {
            motion.hasEmbeddedVideo -> attachment
            motion.role == MotionMediaRole.STILL && motion.groupId.isNotBlank() ->
                attachments.firstOrNull {
                    it.motionMedia?.groupId == motion.groupId && it.motionMedia?.role == MotionMediaRole.VIDEO
                }
            else -> null
        }
    }
}

@Serializable
data class MemoProperty(
    val hasLink: Boolean = false,
    val hasTaskList: Boolean = false,
    val hasCode: Boolean = false,
    val hasIncompleteTasks: Boolean = false,
    val title: String = ""
)

@Serializable
data class Location(
    val placeholder: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

@Serializable
data class Attachment(
    val name: String = "",
    val createTime: Instant? = null,
    val filename: String = "",
    val externalLink: String = "",
    val type: String = "",
    @Serializable(with = ProtoLongSerializer::class)
    val size: Long = 0,
    val memo: String? = null,
    val motionMedia: MotionMedia? = null
) {
    val uid: String get() = name.substringAfterLast('/')
    val isImage: Boolean get() = type.startsWith("image/")
}

@Serializable
data class MotionMedia(
    val family: MotionMediaFamily = MotionMediaFamily.UNSPECIFIED,
    val role: MotionMediaRole = MotionMediaRole.UNSPECIFIED,
    val groupId: String = "",
    @Serializable(with = ProtoLongSerializer::class)
    val presentationTimestampUs: Long = 0,
    val hasEmbeddedVideo: Boolean = false
)

@Serializable
enum class MotionMediaFamily {
    @SerialName("MOTION_MEDIA_FAMILY_UNSPECIFIED")
    UNSPECIFIED,
    @SerialName("APPLE_LIVE_PHOTO")
    APPLE_LIVE_PHOTO,
    @SerialName("ANDROID_MOTION_PHOTO")
    ANDROID_MOTION_PHOTO
}

@Serializable
enum class MotionMediaRole {
    @SerialName("MOTION_MEDIA_ROLE_UNSPECIFIED")
    UNSPECIFIED,
    @SerialName("STILL")
    STILL,
    @SerialName("VIDEO")
    VIDEO,
    @SerialName("CONTAINER")
    CONTAINER
}

@Serializable
data class MemoRelation(
    val memo: MemoReference = MemoReference(),
    val relatedMemo: MemoReference = MemoReference(),
    val type: RelationType = RelationType.UNSPECIFIED
)

@Serializable
data class MemoReference(
    val name: String = "",
    val snippet: String = ""
)

@Serializable
enum class RelationType {
    @SerialName("TYPE_UNSPECIFIED")
    UNSPECIFIED,
    @SerialName("REFERENCE")
    REFERENCE,
    @SerialName("COMMENT")
    COMMENT
}

@Serializable
data class Reaction(
    val name: String = "",
    val creator: String = "",
    val reactionType: String = "",
    val createTime: Instant? = null
)

@Serializable
data class User(
    val name: String = "",
    val role: UserRole = UserRole.USER,
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val description: String = "",
    val state: UserState = UserState.NORMAL,
    val createTime: Instant? = null,
    val updateTime: Instant? = null
) {
    val visibleName: String get() = displayName.ifBlank { username }
}

@Serializable
enum class UserRole {
    @SerialName("ROLE_UNSPECIFIED")
    UNSPECIFIED,
    @SerialName("ADMIN")
    ADMIN,
    @SerialName("USER")
    USER
}

@Serializable
enum class UserState {
    @SerialName("STATE_UNSPECIFIED")
    UNSPECIFIED,
    @SerialName("NORMAL")
    NORMAL,
    @SerialName("ARCHIVED")
    ARCHIVED
}

@Serializable
data class AttachmentUpload(
    val filename: String,
    /** Base64: proto JSON carries a `bytes` field as a string, not an array. */
    val content: String,
    val type: String,
    val motionMedia: MotionMedia? = null
)

@Serializable
data class UploadAttachmentSpec(
    val attachment: Attachment,
    val attachmentId: String = "",
    @Serializable(with = ProtoLongSerializer::class)
    val totalSize: Long = 0
)

@Serializable
data class UploadAttachmentRequest(
    val spec: UploadAttachmentSpec? = null,
    val uploadId: String? = null,
    @Serializable(with = ProtoLongSerializer::class)
    val writeOffset: Long = 0,
    /** Base64 chunk. */
    val data: String? = null,
    val finishWrite: Boolean = false
)

@Serializable
data class UploadAttachmentResponse(
    val uploadId: String = "",
    @Serializable(with = ProtoLongSerializer::class)
    val committedSize: Long = 0,
    val maxChunkSize: Int = 0,
    val attachment: Attachment? = null
)

@Serializable
data class MemoInput(
    val content: String,
    val visibility: Visibility,
    val pinned: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val relations: List<MemoRelation> = emptyList(),
    val location: Location? = null,
    val space: String? = null
)

@Serializable
data class MemoPatch(
    val name: String,
    val content: String? = null,
    val visibility: Visibility? = null,
    val pinned: Boolean? = null,
    val attachments: List<Attachment>? = null,
    val relations: List<MemoRelation>? = null,
    val location: Location? = null,
    val space: String? = null
)

@Serializable
data class ReactionInput(
    /** Required by 0.26–0.30; removed in 0.31. */
    val contentId: String? = null,
    val reactionType: String
)

@Serializable
data class UpsertReactionBody(
    val reaction: ReactionInput
)

@Serializable
data class MemoShare(
    val name: String = "",
    val createTime: Instant? = null,
    val expireTime: Instant? = null
) {
    val token: String get() = name.substringAfterLast('/')
}

@Serializable
data class CreateMemoShareRequestBody(
    val expireTime: Instant? = null
)

@Serializable
data class LinkMetadata(
    val url: String = "",
    val title: String = "",
    val description: String = "",
    val image: String = ""
)

@Serializable
data class UserNotification(
    val name: String = "",
    val sender: String = "",
    val senderUser: User? = null,
    val status: String = "UNREAD",
    val createTime: Instant? = null,
    val type: String = "TYPE_UNSPECIFIED",
    val memoComment: MemoCommentNotification? = null,
    val memoMention: MemoMentionNotification? = null
)

@Serializable
data class MemoCommentNotification(
    val memo: String = "",
    val relatedMemo: String = "",
    val memoSnippet: String = "",
    val relatedMemoSnippet: String = ""
)

@Serializable
data class MemoMentionNotification(
    val memo: String = "",
    val relatedMemo: String = "",
    val memoSnippet: String = "",
    val relatedMemoSnippet: String = ""
)

@Serializable
data class BatchGetUsersRequest(
    val usernames: List<String>? = null,
    /** 26.10: resource names (`users/{user}`). */
    val names: List<String>? = null
)

@Serializable
data class BatchGetUsersResponse(
    val users: List<User> = emptyList()
)

@Serializable
data class InstanceSetting(
    val name: String = "",
    val generalSetting: GeneralSetting? = null,
    val storageSetting: StorageSetting? = null,
    val memoRelatedSetting: MemoRelatedSetting? = null,
    @SerialName("dataSetting") val dataSetting: JsonElement? = null
)

@Serializable
data class GeneralSetting(
    val customProfile: CustomProfile? = null,
    val disallowUserRegistration: Boolean = false,
    val disallowPasswordAuth: Boolean = false,
    val disallowChangeUsername: Boolean = false,
    val disallowChangeNickname: Boolean = false
)

@Serializable
data class CustomProfile(
    val title: String = "",
    val description: String = "",
    val logoUrl: String = ""
)

@Serializable
data class StorageSetting(
    @Serializable(with = ProtoLongSerializer::class)
    val uploadSizeLimitMb: Long = 0
)

@Serializable
data class MemoRelatedSetting(
    val reactions: JsonElement? = null
)

@Serializable
data class ListMemosResponse(
    val memos: List<Memo> = emptyList(),
    val nextPageToken: String = ""
)

@Serializable
data class ListMemoCommentsResponse(
    /** 0.22–0.31. */
    val memos: List<Memo> = emptyList(),
    /** 26.10. */
    val comments: List<Memo> = emptyList(),
    val nextPageToken: String = ""
) {
    val items: List<Memo> get() = if (comments.isNotEmpty() || memos.isEmpty()) comments else memos
}

@Serializable
data class ListMemoReactionsResponse(
    val reactions: List<Reaction> = emptyList(),
    val nextPageToken: String = ""
)

@Serializable
data class ListMemoSharesResponse(
    val memoShares: List<MemoShare> = emptyList()
)

@Serializable
data class ListUserNotificationsResponse(
    val notifications: List<UserNotification> = emptyList(),
    val nextPageToken: String = ""
)

@Serializable
data class SignInRequest(
    val passwordCredentials: PasswordCredentials
)

@Serializable
data class PasswordCredentials(
    val username: String,
    val password: String
)

@Serializable
data class SignInResponse(
    val user: User = User(),
    val accessToken: String = "",
    val accessTokenExpiresAt: Instant? = null,
    /** 26.10. */
    val accessTokenExpireTime: Instant? = null
) {
    val expiresAt: Instant? get() = accessTokenExpiresAt ?: accessTokenExpireTime
}

@Serializable
data class CurrentUserResponse(
    val user: User = User()
)

@Serializable
data class RefreshTokenResponse(
    val accessToken: String = "",
    val expiresAt: Instant? = null,
    /** 26.10. */
    val accessTokenExpireTime: Instant? = null
) {
    val expires: Instant? get() = expiresAt ?: accessTokenExpireTime
}

@Serializable
data class UserStats(
    val name: String = "",
    /** 0.26–0.31. */
    val tagCount: Map<String, Int> = emptyMap(),
    /** 26.10. */
    val tagCounts: Map<String, Int> = emptyMap(),
    /** Creation time of every memo the account can see; the activity heatmap's data. */
    val memoCreatedTimestamps: List<Instant> = emptyList(),
    /** 0.26–0.29 reported display time instead. */
    val memoDisplayTimestamps: List<Instant> = emptyList(),
    val memoTypeStats: MemoTypeStats = MemoTypeStats()
) {
    val effectiveTagCounts: Map<String, Int> get() = tagCounts.ifEmpty { tagCount }
    val effectiveMemoTimestamps: List<Instant> get() = memoCreatedTimestamps.ifEmpty { memoDisplayTimestamps }
}

@Serializable
data class MemoTypeStats(
    val linkCount: Int = 0,
    val codeCount: Int = 0,
    val todoCount: Int = 0,
    val undoCount: Int = 0
)

@Serializable
data class InstanceProfile(
    val version: String = "",
    val instanceUrl: String = "",
    val accessMode: InstanceAccessMode = InstanceAccessMode.PRIVATE,
    val needsSetup: Boolean = false
)

@Serializable
enum class InstanceAccessMode {
    @SerialName("INSTANCE_ACCESS_MODE_UNSPECIFIED")
    UNSPECIFIED,
    @SerialName("INSTANCE_ACCESS_MODE_PRIVATE")
    PRIVATE,
    @SerialName("INSTANCE_ACCESS_MODE_PUBLIC")
    PUBLIC
}
