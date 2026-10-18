package ru.kbr.cargoshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationHistoryTest {
    @Test fun backReturnsThroughMoreAndTripsNotImmediatelyHome() {
        val fromTrips = pushNavigationRoute(emptyList(), 0, 1)
        val fromMore = pushNavigationRoute(fromTrips, 1, 3)
        val fromLive = pushNavigationRoute(fromMore, 3, 4)
        assertEquals(listOf(0, 1, 3), fromLive)
        assertEquals(3, fromLive.last())
        assertEquals(1, fromLive.dropLast(1).last())
        assertEquals(0, fromLive.dropLast(2).last())
    }

    @Test fun homeResetsHistoryAndReselectionDoesNotDuplicate() {
        assertTrue(pushNavigationRoute(listOf(0, 1, 3), 4, 0).isEmpty())
        assertEquals(listOf(0, 1), pushNavigationRoute(listOf(0, 1), 3, 3))
    }

    @Test fun historyNeverGrowsUnbounded() {
        var history = emptyList<Int>()
        repeat(80) { i -> history = pushNavigationRoute(history, i % 2 + 1, (i+1) % 2 + 1) }
        assertEquals(24, history.size)
    }
}
