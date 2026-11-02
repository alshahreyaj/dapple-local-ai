package com.localai.cutout.core.inference

/**
 * Tiling candidates for experiments with the fixed 512px LaMa PTE. The 64px
 * overlap / 448px step is a requested starting experiment, not a publisher rule.
 */
object RestorationTileExperiment {
    const val TILE_SIZE = 512
    const val REQUESTED_START_OVERLAP = 64

    data class Tile(val x: Int, val y: Int)

    fun positions(width: Int, height: Int, overlap: Int): List<Tile> {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(overlap in 0 until TILE_SIZE) { "Overlap must be between 0 and 511 pixels" }
        val xs = starts(width, overlap)
        val ys = starts(height, overlap)
        return xs.flatMap { x -> ys.map { y -> Tile(x, y) } }
    }

    fun starts(dimension: Int, overlap: Int): List<Int> {
        require(dimension > 0)
        require(overlap in 0 until TILE_SIZE)
        if (dimension <= TILE_SIZE) return listOf(0)
        val step = TILE_SIZE - overlap
        val values = mutableListOf<Int>()
        var start = 0
        while (start < dimension) {
            values += start
            if (start + TILE_SIZE >= dimension) break
            start += step
        }
        return values
    }

    /**
     * Experimental cosine feather weight for a pixel in a tile. This is an
     * explicit candidate blend rule for A/B evaluation, not an upstream rule.
     */
    fun cosineFeatherWeight(
        localX: Int,
        localY: Int,
        tileWidth: Int,
        tileHeight: Int,
        startsAtLeft: Boolean,
        startsAtTop: Boolean,
        endsAtRight: Boolean,
        endsAtBottom: Boolean,
        overlap: Int,
    ): Float {
        require(localX in 0 until tileWidth && localY in 0 until tileHeight)
        require(overlap in 0 until TILE_SIZE)
        if (overlap == 0) return 1f
        fun edgeWeight(position: Int, length: Int, hasPrevious: Boolean, hasNext: Boolean): Float {
            var weight = 1f
            if (hasPrevious && position < overlap) {
                val t = (position + 1f) / (overlap + 1f)
                weight = minOf(weight, .5f - .5f * kotlin.math.cos(Math.PI.toFloat() * t))
            }
            if (hasNext && length - position <= overlap) {
                val t = (length - position).toFloat() / (overlap + 1f)
                weight = minOf(weight, .5f - .5f * kotlin.math.cos(Math.PI.toFloat() * t))
            }
            return weight
        }
        val wx = edgeWeight(localX, tileWidth, !startsAtLeft, !endsAtRight)
        val wy = edgeWeight(localY, tileHeight, !startsAtTop, !endsAtBottom)
        return wx * wy
    }
}
