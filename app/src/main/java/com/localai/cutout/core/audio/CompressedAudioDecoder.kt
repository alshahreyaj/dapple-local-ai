package com.localai.cutout.core.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.min

/** Decodes audio tracks from containers/codecs available through the device's Android media codecs. */
internal object CompressedAudioDecoder {
    private const val MAX_SECONDS = 300
    // Bound decoded mono memory before resampling; compressed files can expand dramatically.
    private const val MAX_MONO_SAMPLES = 24_000_000
    private const val MAX_CHANNELS = 8

    fun decodeMono48k(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { track = i; format = candidate; break }
            }
            require(track >= 0) { "This file does not contain a supported audio track" }
            val selectedFormat = requireNotNull(format) { "This file does not contain a supported audio track" }
            extractor.selectTrack(track)
            val mime = selectedFormat.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(selectedFormat, null, null, 0)
            codec.start()

            var sampleRate = selectedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = selectedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            validateFormat(sampleRate, channels)

            val mono = FloatSamples()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("Audio decoder did not provide an input buffer")
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        validateFormat(sampleRate, channels)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val pcm = output.slice().order(ByteOrder.nativeOrder())
                            appendDownmixed(mono, pcm, channels, pcmEncoding, sampleRate)
                        }
                        val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                        outputEnded = ended
                    }
                }
            }
            require(mono.size > 0) { "No decoded audio samples were found" }
            val decoded = mono.toArray()
            return if (sampleRate == WavAudio.SAMPLE_RATE) decoded
            else WavAudio.resampleTo48k(decoded, sampleRate)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(e.message ?: "This audio format is not supported on this device", e)
        } catch (e: Exception) {
            throw IllegalArgumentException("Couldn't open this audio file. Try a WAV, MP3, M4A, FLAC or OGG file.", e)
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private fun appendDownmixed(samples: FloatSamples, pcm: java.nio.ByteBuffer, channels: Int, encoding: Int, sampleRate: Int) {
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            else -> throw IllegalArgumentException("This audio format isn't supported on your phone. Try a WAV, MP3, M4A, FLAC or OGG file.")
        }
        val frameBytes = channels * bytesPerSample
        val frames = pcm.remaining() / frameBytes
        require(samples.size.toLong() + frames <= MAX_MONO_SAMPLES && samples.size.toLong() + frames <= sampleRate.toLong() * MAX_SECONDS) {
            "Audio is too long to process (maximum five minutes, subject to available memory)"
        }
        repeat(frames) {
            var sum = 0.0
            repeat(channels) {
                val sample = when (encoding) {
                    AudioFormat.ENCODING_PCM_8BIT -> ((pcm.get().toInt() and 0xff) - 128) / 128.0
                    AudioFormat.ENCODING_PCM_16BIT -> pcm.short / 32768.0
                    AudioFormat.ENCODING_PCM_FLOAT -> pcm.float.toDouble()
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                        val b0 = pcm.get().toInt() and 0xff
                        val b1 = pcm.get().toInt() and 0xff
                        val b2 = pcm.get().toInt()
                        val value = (b0 or (b1 shl 8) or (b2 shl 16)).let { if (it and 0x800000 != 0) it or -0x1000000 else it }
                        value / 8_388_608.0
                    }
                    AudioFormat.ENCODING_PCM_32BIT -> pcm.int / 2_147_483_648.0
                    else -> error("This audio format isn't supported on your phone.")
                }
                sum += sample
            }
            samples.add((sum / channels).toFloat())
        }
    }

    private fun validateFormat(sampleRate: Int, channels: Int) {
        require(sampleRate in 8_000..192_000 && channels in 1..MAX_CHANNELS) {
            "This audio track has an unsupported sample rate or channel count"
        }
    }

    private class FloatSamples {
        private var values = FloatArray(16_384)
        var size = 0
            private set
        fun add(value: Float) {
            if (size == values.size) values = values.copyOf(min(MAX_MONO_SAMPLES, values.size * 2))
            values[size++] = value
        }
        fun toArray() = values.copyOf(size)
    }
}
