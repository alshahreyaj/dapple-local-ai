package com.localai.cutout.core.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.util.concurrent.atomic.AtomicInteger

/** ORMBG integration copied from john-rocky/LiteRT-Models (Apache-2.0), preserving its tensor contract. */
class BgRemover private constructor(context: Context, path: String) : AutoCloseable {
    companion object {
        const val SIZE = 1024
        fun fromFile(context: Context, path: String) = BgRemover(context, path)
    }
    private val model: CompiledModel
    private val inputBuffers: List<TensorBuffer>
    private val outputBuffers: List<TensorBuffer>
    private val input = FloatArray(3 * SIZE * SIZE)
    private val pixels = IntArray(SIZE * SIZE)
    private val square = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(square)
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val generation = AtomicInteger()
    @Volatile private var cancelledGeneration = 0
    private var closed = false
    var loadMs = 0L; private set
    var modelMs = 0L; private set

    init {
        val started = System.nanoTime()
        model = CompiledModel.create(path, CompiledModel.Options(Accelerator.GPU), null)
        inputBuffers = model.createInputBuffers(); outputBuffers = model.createOutputBuffers()
        inputBuffers[0].writeFloat(input); model.run(inputBuffers, outputBuffers); outputBuffers[0].readFloat()
        loadMs = (System.nanoTime() - started) / 1_000_000
        Log.i("ormbg", "GPU ready in ${loadMs}ms")
    }

    @Synchronized fun process(bitmap: Bitmap): Matte? {
        check(!closed) { "BgRemover is closed" }
        val id = generation.incrementAndGet()
        matrix.setScale(SIZE.toFloat() / bitmap.width, SIZE.toFloat() / bitmap.height)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(bitmap, matrix, paint)
        square.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        val plane = SIZE * SIZE
        for (i in 0 until plane) {
            val px = pixels[i]
            input[i] = ((px shr 16) and 255) / 255f
            input[plane + i] = ((px shr 8) and 255) / 255f
            input[2 * plane + i] = (px and 255) / 255f
        }
        if (cancelledGeneration == id) return null
        val t = System.nanoTime()
        inputBuffers[0].writeFloat(input); model.run(inputBuffers, outputBuffers)
        val raw = outputBuffers[0].readFloat(); modelMs = (System.nanoTime() - t) / 1_000_000
        if (cancelledGeneration == id) return null
        var min = Float.MAX_VALUE; var max = -Float.MAX_VALUE
        raw.forEach { if (it < min) min = it; if (it > max) max = it }
        val scale = 1f / (max - min + 1e-6f)
        for (i in raw.indices) raw[i] = (raw[i] - min) * scale
        return Matte(raw, SIZE, min, max)
    }
    fun cancel() { cancelledGeneration = generation.get() }
    @Synchronized override fun close() {
        if (closed) return
        closed = true; inputBuffers.forEach { it.close() }; outputBuffers.forEach { it.close() }; model.close(); square.recycle()
    }
}

class Matte(val alpha: FloatArray, val size: Int, val rawMin: Float, val rawMax: Float) {
    fun cutout(source: Bitmap): Bitmap {
        val maskPixels = IntArray(alpha.size) { (alpha[it] * 255f + .5f).toInt().coerceIn(0, 255) shl 24 }
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { setPixels(maskPixels, 0, size, 0, 0, size, size) }
        val scaled = if (source.width == size && source.height == size) mask else Bitmap.createScaledBitmap(mask, source.width, source.height, true)
        val original = IntArray(source.width * source.height); val matte = IntArray(original.size)
        source.getPixels(original, 0, source.width, 0, 0, source.width, source.height)
        scaled.getPixels(matte, 0, source.width, 0, 0, source.width, source.height)
        for (i in original.indices) original[i] = (matte[i] and -0x1000000) or (original[i] and 0x00ffffff)
        if (scaled !== mask) scaled.recycle(); mask.recycle()
        return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).apply { setPixels(original, 0, source.width, 0, 0, source.width, source.height) }
    }
}
