package ru.kbr.cargoshare

import org.junit.Assert.assertEquals
import org.junit.Test

class MapEnginePreferenceTest {
    @Test fun unknownEngineUsesExistingStableMap() {
        assertEquals(MAP_ENGINE_OSMDROID, normalizeMapEngine(null))
        assertEquals(MAP_ENGINE_OSMDROID, normalizeMapEngine(""))
        assertEquals(MAP_ENGINE_OSMDROID, normalizeMapEngine("unknown"))
        assertEquals(MAP_ENGINE_OSMDROID, normalizeMapEngine(MAP_ENGINE_OSMDROID))
    }

    @Test fun mapLibreIsOptIn() {
        assertEquals(MAP_ENGINE_MAPLIBRE, normalizeMapEngine(MAP_ENGINE_MAPLIBRE))
    }
}
