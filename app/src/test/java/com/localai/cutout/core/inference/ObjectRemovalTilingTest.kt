package com.localai.cutout.core.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectRemovalTilingTest {
    @Test fun smallAndExactTileUseOnePass() {
        assertEquals(listOf(0), ObjectRemovalTiling.starts(1))
        assertEquals(listOf(0), ObjectRemovalTiling.starts(512))
    }

    @Test fun overlappingTilesCoverWideAndTallImagesWithoutGaps() {
        listOf(513, 900, 1024, 1920, 4096).forEach { dimension ->
            val starts = ObjectRemovalTiling.starts(dimension)
            assertEquals(0, starts.first())
            assertTrue(starts.last() + LamaObjectRemover.TILE >= dimension)
            starts.zipWithNext().forEach { (left, right) ->
                assertTrue("tiles left a gap for $dimension", right <= left + LamaObjectRemover.TILE)
                assertTrue("tile overlap too small for $dimension", right - left <= LamaObjectRemover.TILE - LamaObjectRemover.OVERLAP)
            }
        }
    }

    @Test fun selectionIsAssignedToItsTileIncludingImageEdges() {
        val width = 1024
        val height = 768
        val mask = IntArray(width * height)
        mask[0] = 0xffffffff.toInt()
        mask[(height - 1) * width + (width - 1)] = 0xffffffff.toInt()

        assertTrue(ObjectRemovalTiling.containsSelection(mask, width, height, 0, 0))
        assertTrue(ObjectRemovalTiling.containsSelection(mask, width, height, 896, 256))
        assertFalse(ObjectRemovalTiling.containsSelection(mask, width, height, 448, 0))
    }

    @Test fun transparentOrDimPixelsDoNotTriggerInference() {
        val mask = IntArray(8 * 8)
        mask[0] = 0x000000ff
        mask[1] = 0x7fff00ff
        assertFalse(ObjectRemovalTiling.containsSelection(mask, 8, 8, 0, 0))
        mask[2] = 0xffff00ff.toInt()
        assertTrue(ObjectRemovalTiling.containsSelection(mask, 8, 8, 0, 0))
    }
}
