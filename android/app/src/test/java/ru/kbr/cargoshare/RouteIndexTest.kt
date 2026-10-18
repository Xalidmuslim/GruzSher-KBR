package ru.kbr.cargoshare

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteIndexTest {
    @Test fun walkingRouteOfTwoPointsDoesNotCrash() {
        val route = listOf("user", "truck")
        assertEquals("truck", route[centerIndexForRoute(route.size)])
    }

    @Test fun drivingRouteOfFivePointsFindsMiddle() {
        val route = listOf("start", "one", "middle", "three", "finish")
        assertEquals("middle", route[centerIndexForRoute(route.size)])
    }

    @Test fun onePointRouteHasSafeCenter() {
        assertEquals(0, centerIndexForRoute(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPointRouteFailsWithHelpfulError() {
        centerIndexForRoute(0)
    }
}
