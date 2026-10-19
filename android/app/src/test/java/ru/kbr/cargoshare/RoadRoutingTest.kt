package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

class RoadRoutingTest {
    @Test fun readsRoadDistanceAndGeoJsonPointsInCorrectLatLonOrder() {
        val response = """{
          "code": "Ok",
          "routes": [{
            "distance": 3542.8,
            "duration": 430.0,
            "geometry": {
              "type": "LineString",
              "coordinates": [[43.6071,43.4853],[43.6101,43.4893],[43.6141,43.4943]]
            }
          }]
        }"""
        val route = parseRoadRoute(response)
        assertNotNull(route)
        assertEquals(3542.8, route!!.distanceMeters, 0.01)
        assertEquals(3, route.points.size)
        assertEquals(43.4853, route.points[0].first, 0.00001)
        assertEquals(43.6071, route.points[0].second, 0.00001)
    }

    @Test fun refusesNoRouteInsteadOfDisplayingStraightLineDistance() {
        assertNull(parseRoadRoute("""{"code":"NoRoute","routes":[]}"""))
        assertNull(parseRoadRoute("""{"code":"Ok","routes":[]}"""))
        assertNull(parseRoadRoute("""{"code":"Ok","routes":[{"distance":120.0,"geometry":{"coordinates":[]}}]}"""))
        assertNull(parseRoadRoute("""{"code":"Ok","routes":[{"distance":-1,"geometry":{"coordinates":[[43,44],[43.1,44.1]]}}]}"""))
    }

    @Test fun refusesMalformedCoordinatesAndInvalidJson() {
        assertNull(parseRoadRoute("not a response"))
        assertNull(parseRoadRoute("""{"code":"Ok","routes":[{"distance":120.0,"geometry":{"coordinates":[[43.6,43.4],[200.0,43.7]]}}]}"""))
    }
}
