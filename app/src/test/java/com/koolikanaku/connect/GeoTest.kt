package com.koolikanaku.connect

import com.koolikanaku.connect.logic.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test fun encodesKnownGeohashes() {
        assertEquals("u4pruydqqvj", Geo.encode(57.64911, 10.40744, 11))
        assertEquals("ezs42", Geo.encode(42.6, -5.6, 5))
    }

    @Test fun decodedCenterIsInsideTheOriginalCell() {
        val hash = Geo.encode(9.9312, 76.2673)          // Kochi
        val center = Geo.decodeCenter(hash)
        assertEquals(hash, Geo.encode(center.lat, center.lng))
        assertTrue(Geo.distanceKm(Geo.LatLng(9.9312, 76.2673), center) < 1.0)
    }

    @Test fun storedLocationIsCoarse() {
        // a worker's stored cell centre is within ~1 km of the true spot, never the exact point
        val truth = Geo.LatLng(9.9312, 76.2673)
        val stored = Geo.decodeCenter(Geo.encode(truth.lat, truth.lng, Geo.STORED_PRECISION))
        assertTrue(Geo.distanceKm(truth, stored) > 0.0)
        assertTrue(Geo.distanceKm(truth, stored) < 1.0)
    }

    @Test fun neighborsAreNineDistinctCellsAroundTheOriginal() {
        val cells = Geo.cellAndNeighbors("ezs42")
        assertEquals(9, cells.size)
        assertEquals(9, cells.toSet().size)
        assertTrue("ezs42" in cells)
        assertTrue(cells.all { it.length == 5 })
    }

    @Test fun neighborsHandleTheDateLineAndPoles() {
        val edge = Geo.encode(0.0, 179.9999, 5)
        assertEquals(9, Geo.cellAndNeighbors(edge).size)
        val pole = Geo.encode(89.9999, 10.0, 5)
        assertEquals(6, Geo.cellAndNeighbors(pole).size)   // no row of cells above the pole
    }

    @Test fun aPointNearACellEdgeStillFindsTheWorkerNextDoor() {
        val owner = Geo.LatLng(9.9312, 76.2673)
        // a worker about 4 km away is in a different 5-char cell, but inside the 9-cell search
        val worker = Geo.LatLng(9.9312 + 0.035, 76.2673)
        val workerCell = Geo.encode(worker.lat, worker.lng, Geo.QUERY_PRECISION)
        assertTrue(Geo.searchPrefixes(owner.lat, owner.lng).any { workerCell.startsWith(it) })
    }

    @Test fun distanceMatchesAKnownPair() {
        // Kochi to Thiruvananthapuram is about 172 km in a straight line (about 200 km by road)
        val km = Geo.distanceKm(Geo.LatLng(9.9312, 76.2673), Geo.LatLng(8.5241, 76.9366))
        assertTrue("was $km", km in 165.0..180.0)
        assertEquals(0.0, Geo.distanceKm(Geo.LatLng(1.0, 1.0), Geo.LatLng(1.0, 1.0)), 1e-9)
    }

    @Test fun distanceIsShownAsABand() {
        assertEquals("under 1 km", Geo.distanceBand(0.4))
        assertEquals("about 3 km", Geo.distanceBand(2.6))
    }
}
