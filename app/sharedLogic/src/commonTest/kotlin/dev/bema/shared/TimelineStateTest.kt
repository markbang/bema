package dev.bema.shared

import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.session.MemosAppState
import dev.bema.shared.data.session.localDayFilter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineStateTest {
    @Test
    fun paginationIsBlockedWhileTheFirstPageIsBeingReplaced() {
        val state = MemosAppState(nextPageToken = "next")
        assertTrue(state.canLoadMore)
        assertFalse(state.copy(isLoading = true).canLoadMore)
        assertFalse(state.copy(isLoadingMore = true).canLoadMore)
        assertFalse(state.copy(nextPageToken = "").canLoadMore)
    }

    @Test
    fun emptyStatesDistinguishAnEmptyAccountFromAnEmptyDay() {
        val empty = MemosAppState()
        val day = empty.copy(timelineFilter = localDayFilter(20_000, 0))
        assertNotNull(empty.timelineEmptyMessage)
        assertNotNull(day.timelineEmptyMessage)
        assertNotEquals(empty.timelineEmptyMessage, day.timelineEmptyMessage)
    }

    @Test
    fun loadingErrorsAndContentDoNotDisplayAnEmptyState() {
        assertNull(MemosAppState(isLoading = true).timelineEmptyMessage)
        assertNull(MemosAppState(error = "Offline").timelineEmptyMessage)
        assertNull(MemosAppState(timeline = listOf(Memo(name = "memos/a"))).timelineEmptyMessage)
    }
}
