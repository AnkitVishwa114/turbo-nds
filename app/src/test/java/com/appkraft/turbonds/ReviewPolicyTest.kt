package com.appkraft.turbonds

import com.appkraft.turbonds.data.ReviewPolicy
import com.appkraft.turbonds.data.Usage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPolicyTest {
    private val now = 100L * 24 * 60 * 60 * 1000
    private val engaged = Usage(firstOpen = now - 86_400_000, launches = 2, foregroundMs = 6 * 60_000, gamesPlayed = 1, downloads = 1)

    @Test fun engagedUserIsAsked() = assertTrue(ReviewPolicy.due(engaged, now))

    @Test fun downloadsAloneCount() = assertTrue(ReviewPolicy.due(engaged.copy(gamesPlayed = 0, downloads = 2), now))

    @Test fun firstSessionIsNeverAsked() = assertFalse(ReviewPolicy.due(engaged.copy(launches = 1), now))

    @Test fun shortVisitsAreNotAsked() = assertFalse(ReviewPolicy.due(engaged.copy(foregroundMs = 2 * 60_000), now))

    @Test fun browsingOnlyIsNotAsked() = assertFalse(ReviewPolicy.due(engaged.copy(gamesPlayed = 0, downloads = 1), now))

    @Test fun asksAreSpacedAndCapped() {
        assertFalse(ReviewPolicy.due(engaged.copy(lastReviewAsk = now - 5L * 86_400_000, reviewAsks = 1), now))
        assertTrue(ReviewPolicy.due(engaged.copy(lastReviewAsk = now - 31L * 86_400_000, reviewAsks = 1), now))
        assertFalse(ReviewPolicy.due(engaged.copy(lastReviewAsk = now - 90L * 86_400_000, reviewAsks = 3), now))
    }
}
