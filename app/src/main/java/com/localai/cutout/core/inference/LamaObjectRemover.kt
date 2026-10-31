package com.localai.cutout.core.inference

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import com.google.ai.edge.litert.TensorType
import java.util.concurrent.atomic.AtomicBoolean

/** Qualcomm AI Hub LaMa-Dilated float export. Images and masks are RGB/1-channel float [0,1]. */
class LamaObjectRemover private constructor(private val runner: Runner, val backend: String) : AutoCloseable {
    companion object {
        const val TILE = 512
        const val OVERLAP = 64
        private const val PIXELS = TILE * TILE

        fun fromFile(path: String): LamaObjectRemover {
            val attempts = buildList {
                if (hasQualcommRuntime()) add("Qualcomm NPU" to Accelerator.NPU)
                add("GPU" to Accelerator.GPU)
                add("CPU" to Accelerator.CPU)
            }
            var lastError: Throwable? = null
            for ((label, accelerator) in attempts) {
                var runner: LamaCompiledRunner? = null
                try {
                    val candidate = LamaCompiledRunner.create(path, accelerator)
                    runner = candidate
                    // Warm up the exact two-input graph; a delegate that cannot execute is skipped.
                    candidate.infer(FloatArray(PIXELS * 3), FloatArray(PIXELS))
                    return LamaObjectRemover(candidate, label)
                } catch (error: Throwable) {
                    runCatching { runner?.close() }
                    lastError?.addSuppressed(error)
                    lastError = error
                    Log.w("DappleObjectRemover", "$label failed initialization or warmup", error)
                }
            }
            throw IllegalStateException("Object Remover couldn't start on this phone", lastError)
        }

        private fun hasQualcommRuntime(): Boolean {
            if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) return false
            val manufacturer = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER.orEmpty() else ""
            return (Build.HARDWARE + " " + Build.BOARD + " " + manufacturer).contains("qcom", true) || manufacturer.contains("qualcomm", true)
        }
    }

    private val cancelled = AtomicBoolean(false)
    @Volatile private var closed = false

    @Synchronized
    fun process(source: Bitmap, mask: Bitmap, onProgress: (Int, Int) -> Unit = { _, _ -> }): Bitmap {
        check(!closed) { "Object Remover is closed" }
        require(source.width == mask.width && source.height == mask.height) { "Photo and selection sizes do not match" }
        require(source.width > 0 && source.height > 0) { "Choose a valid photo" }
        val width = source.width
        val height = source.height
        val totalPixels = width.toLong() * height
        require(totalPixels <= Runtime.getRuntime().maxMemory() / 24L) {
            "This photo is too large to edit safely on this phone. Try a smaller photo."
        }
        val sourcePixels = IntArray(totalPixels.toInt())
        val maskPixels = IntArray(totalPixels.toInt())
        val accumulatedWeights = IntArray(totalPixels.toInt())
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)
        require(maskPixels.any { (it ushr 24) > 0 && (it and 255) > 127 }) { "Paint over the object you want to remove first." }

        val result = try { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
        catch (oom: OutOfMemoryError) { throw IllegalStateException("Not enough memory to edit this photo. Try a smaller photo.", oom) }
        result.setPixels(sourcePixels, 0, width, 0, 0, width, height)
        val startsX = ObjectRemovalTiling.starts(width)
        val startsY = ObjectRemovalTiling.starts(height)
        val jobs = startsX.flatMap { x -> startsY.map { y -> x to y } }
        val candidates = jobs.filter { (x, y) -> ObjectRemovalTiling.containsSelection(maskPixels, width, height, x, y) }
        cancelled.set(false)
        try {
            candidates.forEachIndexed { index, (x, y) ->
                if (cancelled.get()) throw InterruptedException("Object removal cancelled")
                val imageInput = makeImageInput(sourcePixels, width, height, x, y)
                val maskInput = makeMaskInput(maskPixels, width, height, x, y)
                val output = runner.infer(imageInput, maskInput)
                blendOutput(result, accumulatedWeights, output, x, y, width, height)
                onProgress(index + 1, candidates.size)
            }
            return result
        } catch (error: Throwable) {
            result.recycle()
            throw error
        } finally {
            cancelled.set(false)
        }
    }

    private fun makeImageInput(source: IntArray, width: Int, height: Int, x: Int, y: Int): FloatArray {
        val values = FloatArray(PIXELS * 3)
        var cursor = 0
        for (ty in 0 until TILE) for (tx in 0 until TILE) {
            val p = source[(y + ty).coerceAtMost(height - 1) * width + (x + tx).coerceAtMost(width - 1)]
            values[cursor++] = ((p shr 16) and 255) / 255f
            values[cursor++] = ((p shr 8) and 255) / 255f
            values[cursor++] = (p and 255) / 255f
        }
        return values
    }

    private fun makeMaskInput(mask: IntArray, width: Int, height: Int, x: Int, y: Int): FloatArray {
        val values = FloatArray(PIXELS)
        var cursor = 0
        for (ty in 0 until TILE) for (tx in 0 until TILE) {
            val p = mask[(y + ty).coerceAtMost(height - 1) * width + (x + tx).coerceAtMost(width - 1)]
            values[cursor++] = if ((p ushr 24) > 0 && (p and 255) > 127) 1f else 0f
        }
        return values
    }

    private fun blendOutput(result: Bitmap, accumulatedWeights: IntArray, output: FloatArray, x: Int, y: Int, imageWidth: Int, imageHeight: Int) {
        val tile = IntArray(PIXELS)
        for (i in tile.indices) {
            val r = (output[i * 3].coerceIn(0f, 1f) * 255f).toInt()
            val g = (output[i * 3 + 1].coerceIn(0f, 1f) * 255f).toInt()
            val b = (output[i * 3 + 2].coerceIn(0f, 1f) * 255f).toInt()
            tile[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        val w = minOf(TILE, imageWidth - x)
        val h = minOf(TILE, imageHeight - y)
        val old = IntArray(w * h)
        result.getPixels(old, 0, w, x, y, w, h)
        for (ty in 0 until h) for (tx in 0 until w) {
            val hasNextX = x + TILE - OVERLAP < imageWidth
            val hasNextY = y + TILE - OVERLAP < imageHeight
            val featherX = minOf(if (x > 0) (tx + 1).toFloat() / OVERLAP else 1f, if (hasNextX) (w - tx).toFloat() / OVERLAP else 1f).coerceIn(0f, 1f)
            val featherY = minOf(if (y > 0) (ty + 1).toFloat() / OVERLAP else 1f, if (hasNextY) (h - ty).toFloat() / OVERLAP else 1f).coerceIn(0f, 1f)
            val weight = (minOf(featherX, featherY) * 32f).toInt().coerceAtLeast(1)
            val i = ty * w + tx
            val previous = old[i]
            val imageIndex = (y + ty) * imageWidth + x + tx
            val previousWeight = accumulatedWeights[imageIndex]
            val next = tile[ty * TILE + tx]
            val sum = previousWeight + weight
            fun mix(oldChannel: Int, newChannel: Int) = if (previousWeight == 0) newChannel else (oldChannel * previousWeight + newChannel * weight) / sum
            accumulatedWeights[imageIndex] = sum.coerceAtMost(255)
            old[i] = (0xff shl 24) or
                (mix((previous shr 16) and 255, (next shr 16) and 255) shl 16) or
                (mix((previous shr 8) and 255, (next shr 8) and 255) shl 8) or
                mix(previous and 255, next and 255)
        }
        result.setPixels(old, 0, w, x, y, w, h)
    }

    fun cancel() { cancelled.set(true) }
    @Synchronized override fun close() { if (!closed) { closed = true; runner.close() } }
}

internal object ObjectRemovalTiling {
    fun starts(dimension: Int): List<Int> {
        require(dimension > 0)
        if (dimension <= LamaObjectRemover.TILE) return listOf(0)
        val starts = mutableListOf<Int>()
        var current = 0
        while (current < dimension) {
            starts += current
            if (current + LamaObjectRemover.TILE >= dimension) break
            current += LamaObjectRemover.TILE - LamaObjectRemover.OVERLAP
        }
        return starts
    }

    fun containsSelection(mask: IntArray, width: Int, height: Int, x: Int, y: Int): Boolean {
        require(width > 0 && height > 0 && mask.size == width * height)
        val right = minOf(x + LamaObjectRemover.TILE, width)
        val bottom = minOf(y + LamaObjectRemover.TILE, height)
        for (py in y.coerceAtLeast(0) until bottom) for (px in x.coerceAtLeast(0) until right) {
            val p = mask[py * width + px]
            if ((p ushr 24) > 0 && (p and 255) > 127) return true
        }
        return false
    }
}

private interface Runner : AutoCloseable {
    fun infer(image: FloatArray, mask: FloatArray): FloatArray
}

private class LamaCompiledRunner private constructor(
    private val model: CompiledModel,
    private val image: TensorBuffer,
    private val mask: TensorBuffer,
    private val output: TensorBuffer,
    private val imageType: TensorType,
    private val maskType: TensorType,
    private val outputType: TensorType,
) : Runner {
    override fun infer(imageRgb: FloatArray, binaryMask: FloatArray): FloatArray {
        image.writeFloat(layout(imageRgb, imageType, channels = 3))
        mask.writeFloat(layout(binaryMask, maskType, channels = 1))
        model.run(mapOf("image" to image, "mask" to mask), mapOf("painted_image" to output))
        return toHwc(output.readFloat(), outputType, 3)
    }

    private fun layout(values: FloatArray, type: TensorType, channels: Int): FloatArray {
        val dims = type.layout?.dimensions
            ?: throw IllegalArgumentException("The downloaded LaMa model is missing its input tensor layout.")
        require(type.elementType == TensorType.ElementType.FLOAT && (dims == listOf(1, channels, 512, 512) || dims == listOf(1, 512, 512, channels))) {
            "The downloaded LaMa model has an unsupported input tensor shape or type."
        }
        if (dims == listOf(1, 512, 512, channels)) return values
        val transposed = FloatArray(values.size)
        for (p in 0 until 512 * 512) for (c in 0 until channels) transposed[c * 512 * 512 + p] = values[p * channels + c]
        return transposed
    }

    private fun toHwc(values: FloatArray, type: TensorType, channels: Int): FloatArray {
        val dims = type.layout?.dimensions
            ?: throw IllegalArgumentException("The downloaded LaMa model is missing its output tensor layout.")
        require(type.elementType == TensorType.ElementType.FLOAT && (dims == listOf(1, channels, 512, 512) || dims == listOf(1, 512, 512, channels))) {
            "The downloaded LaMa model has an unsupported output tensor shape or type."
        }
        if (dims == listOf(1, 512, 512, channels)) return values
        val transposed = FloatArray(values.size)
        for (p in 0 until 512 * 512) for (c in 0 until channels) transposed[p * channels + c] = values[c * 512 * 512 + p]
        return transposed
    }

    override fun close() {
        runCatching { image.close() }; runCatching { mask.close() }; runCatching { output.close() }; model.close()
    }

    companion object {
        fun create(path: String, accelerator: Accelerator): LamaCompiledRunner {
            val model = CompiledModel.create(path, CompiledModel.Options(accelerator), null)
            var image: TensorBuffer? = null
            var mask: TensorBuffer? = null
            var output: TensorBuffer? = null
            try {
                val imageType = model.getInputTensorType("image")
                val maskType = model.getInputTensorType("mask")
                val outputType = model.getOutputTensorType("painted_image")
                val imageBuffer = model.createInputBuffer("image").also { image = it }
                val maskBuffer = model.createInputBuffer("mask").also { mask = it }
                val outputBuffer = model.createOutputBuffer("painted_image").also { output = it }
                return LamaCompiledRunner(model, imageBuffer, maskBuffer, outputBuffer, imageType, maskType, outputType)
            } catch (error: Throwable) {
                runCatching { image?.close() }; runCatching { mask?.close() }; runCatching { output?.close() }
                runCatching { model.close() }
                throw error
            }
        }
    }
}
