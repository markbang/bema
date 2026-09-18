package dev.bema.shared.data.session

import dev.bema.shared.data.model.Memo
import kotlin.time.Instant

/**
 * Presentation helpers that both clients render.
 *
 * These used to live in the Android UI layer, so iOS had to re-implement them and
 * the two drifted (`formatTime` showed a bare `HH:MM` on Android but a full
 * timestamp on iOS). Keeping them in the shared module is what makes the two UIs
 * agree.
 */

/** The reaction the clients treat as "like". */
const val HEART_REACTION: String = "❤️"

/**
 * True when [account] has already liked this memo.
 *
 * Memos reports a reaction's creator either as the username or as a
 * `users/<id>` resource name depending on the endpoint, so both are matched.
 */
fun Memo.isLikedBy(account: MemosAccount?): Boolean {
    if (account == null) return false
    val userId = account.userName.substringAfterLast('/')
    return reactions.any { reaction ->
        val creator = reaction.creator.substringAfterLast('/')
        reaction.reactionType == HEART_REACTION &&
            (creator.equals(account.username, ignoreCase = true) || creator == userId)
    }
}

/** `2026-09-16T12:10:00Z` -> `12:10`. Server timestamps are UTC. */
fun formatMemoTime(instant: Instant?): String = instant
    ?.toString()
    ?.substringAfter('T')
    ?.substringBefore('.')
    ?.removeSuffix("Z")
    ?.take(5)
    .orEmpty()
