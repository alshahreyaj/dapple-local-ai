package com.localai.cutout.core.inference

import android.graphics.Bitmap
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp

/**
 * Experimental BOPBTL -> LaMa -> DDColor -> Real-ESRGAN General x4v3 pipeline.
 * BOPBTL input/output and LaMa tile choices remain documented experimental guesses.
 * Models are loaded one at a time to avoid keeping the full model set resident in RAM.
 */
class PhotoRestorationPipeline(
    private val scratchModelPath: String,
    private val lamaModelPath: String,
    private val colorizerModelPath: String,
    private val enhancerModelPath: String,
    val tileOverlap: Int = RestorationTileExperiment.REQUESTED_START_OVERLAP,
    val scratchThreshold: Float = UPSTREAM_SCRATCH_THRESHOLD,
) : Closeable {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var activeUpscaler: RealEsrganGeneralX4v3? = null
    @Volatile private var closed = false

    init {
        require(scratchThreshold in 0f..1f)
        require(tileOverlap in 0 until RestorationTileExperiment.TILE_SIZE)
    }

    @Synchronized
    fun restore(
        source: Bitmap,
        onStage: (stage: String, completed: Int, total: Int) -> Unit = { _, _, _ -> },
    ): Result {
        check(!closed) { "Photo Restoration is closed" }
        require(!source.isRecycled && source.width > 0 && source.height > 0)
        val width = source.width
        val height = source.height
        require(width.toLong() * height <= Runtime.getRuntime().maxMemory() / 120L) {
            "This photo is too large for the restoration stages on this phone. Choose a smaller photo."
        }
        cancelled.set(false)
        var pendingMask: Bitmap? = null
        var pendingImage: Bitmap? = null
        try {
            checkCancelled()
            val rawScratch = BopbtlScratchPte(scratchModelPath).use { model ->
                model.predictRaw(makeScratchInput(source))
            }
            checkCancelled()
            onStage("Preparing damage mask", 1, 1)
            val damageMask = thresholdAndUpscale(rawScratch, width, height)
            val maskPreview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { pendingMask = it }
            val maskPixels = IntArray(damageMask.size) { if (damageMask[it]) -1 else 0xff000000.toInt() }
            maskPreview.setPixels(maskPixels, 0, width, 0, 0, width, height)

            val tiles = RestorationTileExperiment.positions(width, height, tileOverlap)
                .filter { tileContainsDamage(damageMask, width, height, it.x, it.y) }
            var stageImage = (if (tiles.isEmpty()) {
                onStage("No scratches detected", 1, 1)
                source.copy(Bitmap.Config.ARGB_8888, false)
                    ?: throw IllegalStateException("Not enough memory to prepare the restored photo.")
            } else {
                repairWithLama(source, damageMask, tiles, onStage)
            }).also { pendingImage = it }

            checkCancelled()
            onStage("Colorizing photo", 0, 1)
            val colorized = DdcolorRestorationPte(colorizerModelPath).use { model ->
                DdcolorColorizer(model).process(stageImage)
            }
            stageImage.recycle()
            stageImage = colorized.also { pendingImage = it }
            onStage("Colorizing photo", 1, 1)

            checkCancelled()
            onStage("Enhancing details · 4×", 0, 1)
            val enhancedModel = RealEsrganGeneralX4v3.fromFile(enhancerModelPath)
            activeUpscaler = enhancedModel
            val enhanced = try {
                enhancedModel.use { model -> model.process(stageImage) { done, total -> onStage("Enhancing details · 4×", done, total) } }
            } finally { activeUpscaler = null }
            stageImage.recycle()
            stageImage = enhanced.also { pendingImage = it }
            onStage("Enhancing details · 4×", 1, 1)

            pendingMask = null
            pendingImage = null
            return Result(rawScratch, damageMask, maskPreview, stageImage, tiles.size, tileOverlap, scratchThreshold)
        } finally {
            pendingMask?.recycle()
            pendingImage?.recycle()
            cancelled.set(false)
        }
    }

    private fun repairWithLama(
        source: Bitmap,
        mask: BooleanArray,
        tiles: List<RestorationTileExperiment.Tile>,
        onStage: (String, Int, Int) -> Unit,
    ): Bitmap {
        val width = source.width
        val height = source.height
        val count = width * height
        val sourcePixels = IntArray(count)
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
        val red = FloatArray(count)
        val green = FloatArray(count)
        val blue = FloatArray(count)
        val weights = FloatArray(count)
        LamaRestorationPte(lamaModelPath).use { model ->
            tiles.forEachIndexed { index, tile ->
                checkCancelled()
                val output = model.inpaintRaw(makeLamaInput(sourcePixels, mask, width, height, tile.x, tile.y))
                accumulateTile(output, mask, width, height, tile.x, tile.y, red, green, blue, weights)
                onStage("Repairing damage", index + 1, tiles.size)
            }
        }
        val resultPixels = sourcePixels.copyOf()
        for (index in resultPixels.indices) {
            val weight = weights[index]
            if (weight <= 0f) continue
            val r = (red[index] / weight).coerceIn(0f, 1f).times(255f).toInt()
            val g = (green[index] / weight).coerceIn(0f, 1f).times(255f).toInt()
            val b = (blue[index] / weight).coerceIn(0f, 1f).times(255f).toInt()
            resultPixels[index] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(resultPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun makeScratchInput(source: Bitmap): FloatArray {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val values = FloatArray(BopbtlScratchPte.INPUT_ELEMENTS)
        val plane = 384 * 384
        // Experimental transform: RGB [0,1] and nearest resize stretched to the PTE square.
        for (y in 0 until 384) for (x in 0 until 384) {
            val sx = (x.toLong() * width / 384).toInt().coerceAtMost(width - 1)
            val sy = (y.toLong() * height / 384).toInt().coerceAtMost(height - 1)
            val pixel = pixels[sy * width + sx]
            val i = y * 384 + x
            values[i] = ((pixel ushr 16) and 255) / 255f
            values[plane + i] = ((pixel ushr 8) and 255) / 255f
            values[2 * plane + i] = (pixel and 255) / 255f
        }
        return values
    }

    private fun thresholdAndUpscale(raw: FloatArray, width: Int, height: Int): BooleanArray {
        require(raw.size == 384 * 384)
        val mask = BooleanArray(width * height)
        val outputLooksNormalized = raw.minOrNull()!! >= 0f && raw.maxOrNull()!! <= 1f
        for (y in 0 until height) for (x in 0 until width) {
            val sx = (x.toLong() * 384 / width).toInt().coerceIn(0, 383)
            val sy = (y.toLong() * 384 / height).toInt().coerceIn(0, 383)
            val value = raw[sy * 384 + sx]
            val probability = if (outputLooksNormalized) value else (1f / (1f + exp(-value.toDouble()).toFloat()))
            mask[y * width + x] = probability >= scratchThreshold
        }
        return mask
    }

    private fun tileContainsDamage(mask: BooleanArray, width: Int, height: Int, x: Int, y: Int): Boolean {
        for (py in y until minOf(height, y + 512))
            for (px in x until minOf(width, x + 512)) if (mask[py * width + px]) return true
        return false
    }

    private fun makeLamaInput(pixels: IntArray, mask: BooleanArray, width: Int, height: Int, startX: Int, startY: Int): FloatArray {
        val area = 512 * 512
        val input = FloatArray(LamaRestorationPte.INPUT_ELEMENTS)
        for (ty in 0 until 512) for (tx in 0 until 512) {
            val sx = reflect(startX + tx, width)
            val sy = reflect(startY + ty, height)
            val src = sy * width + sx
            val dst = ty * 512 + tx
            val pixel = pixels[src]
            input[dst] = ((pixel ushr 16) and 255) / 255f
            input[area + dst] = ((pixel ushr 8) and 255) / 255f
            input[2 * area + dst] = (pixel and 255) / 255f
            input[3 * area + dst] = if (mask[src]) 1f else 0f
        }
        return input
    }

    private fun accumulateTile(output: FloatArray, mask: BooleanArray, imageWidth: Int, imageHeight: Int,
        startX: Int, startY: Int, red: FloatArray, green: FloatArray, blue: FloatArray, weights: FloatArray) {
        val plane = 512 * 512
        val xs = RestorationTileExperiment.starts(imageWidth, tileOverlap)
        val ys = RestorationTileExperiment.starts(imageHeight, tileOverlap)
        val ix = xs.indexOf(startX); val iy = ys.indexOf(startY)
        val actualWidth = minOf(512, imageWidth - startX)
        val actualHeight = minOf(512, imageHeight - startY)
        for (ty in 0 until actualHeight) for (tx in 0 until actualWidth) {
            val x = startX + tx; val y = startY + ty
            val target = y * imageWidth + x
            if (!mask[target]) continue
            val weight = RestorationTileExperiment.cosineFeatherWeight(
                tx, ty, 512, 512, ix == 0, iy == 0, ix == xs.lastIndex, iy == ys.lastIndex, tileOverlap,
            )
            val out = ty * 512 + tx
            red[target] += output[out].coerceIn(0f, 1f) * weight
            green[target] += output[plane + out].coerceIn(0f, 1f) * weight
            blue[target] += output[2 * plane + out].coerceIn(0f, 1f) * weight
            weights[target] += weight
        }
    }

    private fun reflect(index: Int, size: Int): Int {
        if (size <= 1) return 0
        val period = 2 * size - 2
        val folded = Math.floorMod(index, period)
        return if (folded < size) folded else period - folded
    }

    private fun checkCancelled() { if (cancelled.get()) throw InterruptedException("Photo restoration cancelled") }
    fun cancel() { cancelled.set(true); activeUpscaler?.cancel() }
    override fun close() { closed = true; cancel() }

    data class Result(
        val rawScratchPrediction: FloatArray,
        val damageMask: BooleanArray,
        val maskPreview: Bitmap,
        val restored: Bitmap,
        val tilesProcessed: Int,
        val overlapExperiment: Int,
        val scratchThresholdExperiment: Float,
    )

    companion object { const val UPSTREAM_SCRATCH_THRESHOLD = 0.4f }
}
