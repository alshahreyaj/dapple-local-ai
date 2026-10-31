package com.localai.cutout.core.inference

import org.jtransforms.fft.DoubleFFT_1D
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** DPDFNet8 48 kHz HR TFLite inference, following Ceva's frame-wise STFT contract. */
class DpdfnetSpeechDenoiser private constructor(private val interpreter: Interpreter) : Closeable {
    companion object {
        private const val WIN = 960 // 20 ms at 48 kHz
        private const val HOP = WIN / 2
        private const val BINS = WIN / 2 + 1
        private const val WNORM = 1.0 / WIN
        private const val MODEL_NAME = "dpdfnet8_48khz_hr"
        private val window = FloatArray(WIN) { i ->
            val half = WIN / 2.0
            val inner = sin(0.5 * PI * (i + 0.5) / half)
            sin(0.5 * PI * inner * inner).toFloat()
        }

        fun fromFile(path: String): DpdfnetSpeechDenoiser {
            val interpreter = Interpreter(File(path), Interpreter.Options().setNumThreads(2))
            try {
                interpreter.allocateTensors()
                val inputShape = interpreter.getInputTensor(0).shape()
                val outputShape = interpreter.getOutputTensor(0).shape()
                val expected = intArrayOf(1, 1, BINS, 2)
                require(inputShape.contentEquals(expected)) {
                    "$MODEL_NAME expects input [1,1,$BINS,2], got ${inputShape.contentToString()}"
                }
                require(outputShape.contentEquals(expected)) {
                    "$MODEL_NAME expects output [1,1,$BINS,2], got ${outputShape.contentToString()}"
                }
                require(interpreter.getInputTensor(0).dataType().name == "FLOAT32" &&
                    interpreter.getOutputTensor(0).dataType().name == "FLOAT32") {
                    "$MODEL_NAME requires float32 input and output"
                }
                return DpdfnetSpeechDenoiser(interpreter)
            } catch (error: Throwable) {
                interpreter.close()
                throw error
            }
        }
    }

    @Volatile private var cancelled = false
    private var closed = false

    /** Mono float audio at 48 kHz; STFT frames are passed through the stateful model in order. */
    @Synchronized
    fun enhance(input: FloatArray, onProgress: (Int, Int) -> Unit = { _, _ -> }): FloatArray {
        check(!closed) { "Speech enhancement model is closed" }
        require(input.isNotEmpty()) { "Choose an audio file first" }
        cancelled = false

        // Ceva's offline path appends one window of zeros before center/reflection-padded STFT.
        val padded = FloatArray(input.size + WIN).also { input.copyInto(it) }
        val frameCount = padded.size / HOP + 1
        val fft = DoubleFFT_1D(WIN.toLong())
        val time = DoubleArray(WIN * 2)
        val inputFrame = Array(1) { Array(1) { Array(BINS) { FloatArray(2) } } }
        val outputFrame = Array(1) { Array(1) { Array(BINS) { FloatArray(2) } } }
        val accumulated = DoubleArray((frameCount - 1) * HOP + WIN)
        val weight = DoubleArray(accumulated.size)

        onProgress(0, frameCount)
        for (frameIndex in 0 until frameCount) {
            check(!cancelled) { "Enhancement cancelled" }
            val frameStart = frameIndex * HOP - WIN / 2
            for (i in 0 until WIN) {
                val sampleIndex = reflectIndex(frameStart + i, padded.size)
                time[i] = padded[sampleIndex].toDouble() * window[i]
            }
            fft.realForwardFull(time)
            for (bin in 0 until BINS) {
                inputFrame[0][0][bin][0] = (time[2 * bin] * WNORM).toFloat()
                inputFrame[0][0][bin][1] = (time[2 * bin + 1] * WNORM).toFloat()
            }
            interpreter.run(inputFrame, outputFrame)

            // Rebuild Hermitian spectrum, inverse STFT and overlap-add like librosa.istft.
            time.fill(0.0)
            for (bin in 0 until BINS) {
                val real = outputFrame[0][0][bin][0].toDouble()
                val imag = outputFrame[0][0][bin][1].toDouble()
                time[2 * bin] = real
                time[2 * bin + 1] = if (bin == 0 || bin == BINS - 1) 0.0 else imag
                if (bin in 1 until BINS - 1) {
                    val mirror = WIN - bin
                    time[2 * mirror] = real
                    time[2 * mirror + 1] = -imag
                }
            }
            fft.complexInverse(time, true)
            for (i in 0 until WIN) {
                val target = frameStart + i + WIN / 2
                if (target in accumulated.indices) {
                    val w = window[i].toDouble()
                    accumulated[target] += time[2 * i] * w
                    weight[target] += w * w
                }
            }
            onProgress(frameIndex + 1, frameCount)
        }

        // Drop center padding, undo the reference's 40 ms alignment compensation, then trim.
        val stftAudio = FloatArray((frameCount - 1) * HOP) { index ->
            val at = index + WIN / 2
            if (weight[at] <= 1e-12) 0f else (accumulated[at] / weight[at] / WNORM).toFloat()
        }
        val delay = WIN * 2
        return FloatArray(input.size) { i ->
            val source = i + delay
            if (source < stftAudio.size) stftAudio[source] else 0f
        }
    }

    fun cancel() { cancelled = true }
    @Synchronized override fun close() {
        if (!closed) { closed = true; interpreter.close() }
    }

    private fun reflectIndex(index: Int, size: Int): Int {
        if (size <= 1) return 0
        var value = index
        while (value < 0 || value >= size) {
            value = if (value < 0) -value else 2 * size - 2 - value
        }
        return value
    }
}
