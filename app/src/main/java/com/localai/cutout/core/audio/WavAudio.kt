package com.localai.cutout.core.audio

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Minimal PCM WAV reader/writer for the speech model. Input is downmixed and resampled to 48 kHz. */
object WavAudio {
    const val SAMPLE_RATE = 48_000
    private const val MAX_AUDIO_SECONDS = 300

    fun readMono48k(context: Context, uri: Uri): FloatArray {
        val resolver = context.contentResolver
        val isWave = resolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(12)
            var read = 0
            while (read < header.size) {
                val count = input.read(header, read, header.size - read)
                if (count < 0) break
                read += count
            }
            read == header.size && ascii(header, 0, 4) == "RIFF" && ascii(header, 8, 4) == "WAVE"
        } ?: throw IllegalArgumentException("Could not open the selected audio file")
        if (!isWave) return CompressedAudioDecoder.decodeMono48k(context, uri)
        val bytes = resolver.openInputStream(uri)?.use(::readLimited)
            ?: throw IllegalArgumentException("Could not open the selected audio file")
        var format = 0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataLength = 0
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset, 4)
            val length = u32(bytes, offset + 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val start = offset + 8
            if (start + length > bytes.size) break
            when (id) {
                "fmt " -> if (length >= 16) {
                    format = u16(bytes, start)
                    channels = u16(bytes, start + 2)
                    sampleRate = u32(bytes, start + 4).toInt()
                    bitsPerSample = u16(bytes, start + 14)
                }
                "data" -> { dataOffset = start; dataLength = length }
            }
            offset = start + length + (length and 1)
        }
        require(format == 1 && bitsPerSample == 16 && channels in 1..2 && sampleRate in 8_000..192_000 && dataOffset >= 0) {
            "This WAV file isn't supported. Choose a 16-bit WAV or another audio format."
        }
        val frameCount = dataLength / (channels * 2)
        require(frameCount in 1..(sampleRate * MAX_AUDIO_SECONDS)) { "Audio must be shorter than five minutes" }
        val mono = FloatArray(frameCount)
        var cursor = dataOffset
        for (i in mono.indices) {
            var sample = 0f
            repeat(channels) {
                val value = (bytes[cursor].toInt() and 0xff) or (bytes[cursor + 1].toInt() shl 8)
                sample += value.toShort() / 32768f
                cursor += 2
            }
            mono[i] = sample / channels
        }
        return if (sampleRate == SAMPLE_RATE) mono else resample(mono, sampleRate, SAMPLE_RATE)
    }

    fun writeMono48k(file: File, samples: FloatArray) {
        require(samples.isNotEmpty()) { "There is no enhanced audio to save" }
        val dataLength = samples.size * 2
        FileOutputStream(file).buffered().use { output ->
            output.write("RIFF".toByteArray(Charsets.US_ASCII)); writeU32(output, 36L + dataLength)
            output.write("WAVEfmt ".toByteArray(Charsets.US_ASCII)); writeU32(output, 16)
            writeU16(output, 1); writeU16(output, 1); writeU32(output, SAMPLE_RATE.toLong())
            writeU32(output, SAMPLE_RATE * 2L); writeU16(output, 2); writeU16(output, 16)
            output.write("data".toByteArray(Charsets.US_ASCII)); writeU32(output, dataLength.toLong())
            samples.forEach { sample ->
                val pcm = (sample.coerceIn(-1f, 1f) * 32767f).toInt()
                output.write(pcm and 0xff); output.write((pcm ushr 8) and 0xff)
            }
        }
    }

    internal fun resampleTo48k(input: FloatArray, fromRate: Int): FloatArray = resample(input, fromRate, SAMPLE_RATE)

    /** Whisper expects mono float samples at 16 kHz; sherpa-onnx computes its 80-bin features internally. */
    fun resampleTo16k(input: FloatArray, fromRate: Int = SAMPLE_RATE): FloatArray = resample(input, fromRate, 16_000)

    private fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        val output = FloatArray((input.size.toLong() * toRate / fromRate).toInt().coerceAtLeast(1))
        val cutoff = min(1.0, toRate.toDouble() / fromRate) * 0.94
        val radius = 24
        for (i in output.indices) {
            val center = i.toDouble() * fromRate / toRate
            val first = max(0, kotlin.math.floor(center).toInt() - radius + 1)
            val last = min(input.lastIndex, kotlin.math.floor(center).toInt() + radius)
            var weighted = 0.0; var weightSum = 0.0
            for (j in first..last) {
                val x = j - center
                val scaled = cutoff * x
                val sinc = if (scaled == 0.0) 1.0 else sin(PI * scaled) / (PI * scaled)
                val windowPosition = (x / radius).coerceIn(-1.0, 1.0)
                val window = 0.5 + 0.5 * cos(PI * windowPosition)
                val weight = cutoff * sinc * window
                weighted += input[j] * weight; weightSum += weight
            }
            output[i] = if (weightSum == 0.0) 0f else (weighted / weightSum).toFloat()
        }
        return output
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            total += count
            require(total <= 120 * 1024 * 1024) { "This audio file is too large. Choose a shorter recording." }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int) = String(bytes, offset, length, Charsets.US_ASCII)
    private fun u16(bytes: ByteArray, offset: Int) = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or ((bytes[offset + 3].toLong() and 0xff) shl 24)
    private fun writeU16(output: OutputStream, value: Int) { output.write(value and 255); output.write((value ushr 8) and 255) }
    private fun writeU32(output: OutputStream, value: Long) {
        repeat(4) { shift -> output.write(((value ushr (shift * 8)) and 255).toInt()) }
    }
}
