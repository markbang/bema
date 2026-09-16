package dev.bema.shared.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val memo: String? = null
) {
    val uid: String get() = name.substringAfterLast('/')
    val isImage: Boolean get() = type.startsWith("image/")
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
    val content: ByteArray,
    val type: String
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
    val usernames: List<String>
)

@Serializable
data class BatchGetUsersResponse(
    val users: List<User> = emptyList()
)

@Serializable
data class InstanceSetting(
    val name: String = "",
    val generalSetting: GeneralSetting? = null
)

@Serializable
data class GeneralSetting(
    val customProfile: CustomProfile? = null
)

@Serializable
data class CustomProfile(
    val title: String = "",
    val description: String = "",
    val logoUrl: String = ""
)

@Serializable
data class ListMemosResponse(
    val memos: List<Memo> = emptyList(),
    val nextPageToken: String = ""
)

@Serializable
data class ListMemoCommentsResponse(
    val memos: List<Memo> = emptyList(),
    val nextPageToken: String = ""
)

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
    val accessTokenExpiresAt: Instant? = null
)

@Serializable
data class CurrentUserResponse(
    val user: User = User()
)

@Serializable
data class RefreshTokenResponse(
    val accessToken: String = "",
    val expiresAt: Instant? = null
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
