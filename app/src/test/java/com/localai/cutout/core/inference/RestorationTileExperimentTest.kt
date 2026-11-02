package com.localai.cutout.core.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorationTileExperimentTest {
    @Test fun requestedStartOverlapMapsTo448Stride() {
        assertEquals(448, RestorationTileExperiment.TILE_SIZE - RestorationTileExperiment.REQUESTED_START_OVERLAP)
    }

    @Test fun candidateOverlapsCoverPortraitLandscapeAndExactBoundaries() {
        val dimensions = listOf(1 to 1, 511 to 800, 512 to 512, 513 to 1400, 4000 to 3000)
        val overlaps = listOf(32, 64, 96)
        dimensions.forEach { (width, height) ->
            overlaps.forEach { overlap ->
                val tiles = RestorationTileExperiment.positions(width, height, overlap)
                assertEquals(0, tiles.minOf { it.x })
                assertEquals(0, tiles.minOf { it.y })
                assertTrue(tiles.maxOf { it.x } + RestorationTileExperiment.TILE_SIZE >= width)
                assertTrue(tiles.maxOf { it.y } + RestorationTileExperiment.TILE_SIZE >= height)
                val covered = BooleanArray(width * height)
                tiles.forEach { tile ->
                    for (y in tile.y until minOf(height, tile.y + RestorationTileExperiment.TILE_SIZE)) {
                        for (x in tile.x until minOf(width, tile.x + RestorationTileExperiment.TILE_SIZE)) {
                            covered[y * width + x] = true
                        }
                    }
                }
                assertTrue("Uncovered pixel for ${width}x$height at overlap $overlap", covered.all { it })
            }
        }
    }

    @Test fun featherWeightsAreFiniteAndPositiveInTileOverlap() {
        val overlap = RestorationTileExperiment.REQUESTED_START_OVERLAP
        for (position in 0 until RestorationTileExperiment.TILE_SIZE) {
            val left = RestorationTileExperiment.cosineFeatherWeight(
                position, 256, 512, 512,
                startsAtLeft = false, startsAtTop = true,
                endsAtRight = true, endsAtBottom = true,
                overlap = overlap,
            )
            assertTrue(left.isFinite() && left > 0f && left <= 1f)
        }
    }
}
