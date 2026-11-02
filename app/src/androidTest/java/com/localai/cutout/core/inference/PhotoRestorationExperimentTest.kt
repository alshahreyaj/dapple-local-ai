package com.localai.cutout.core.inference

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Manual on-device evaluation harness. Supply a directory of upright official sample
 * images as `restorationFixturesDir`, PTEs as `restorationModelsDir`, and optionally
 * upstream golden PNGs as `restorationGoldenDir` (`<name>.mask.png` and
 * `<name>.restored.png`). All outputs and a CSV report are written under the app's
 * external files directory. Nothing is copied into the APK.
 */
@RunWith(AndroidJUnit4::class)
class PhotoRestorationExperimentTest {
    @Test fun saveIntermediatesAndMeasureOverlapCandidates() {
        val args = InstrumentationRegistry.getArguments()
        val modelsRoot = args.getString("restorationModelsDir")
        val fixturesRoot = args.getString("restorationFixturesDir")
        assumeTrue("Provide restorationModelsDir and restorationFixturesDir instrumentation arguments", !modelsRoot.isNullOrBlank() && !fixturesRoot.isNullOrBlank())

        val bop = File(requireNotNull(modelsRoot), "bopbtl_scratch_t384_xnnpack_int8.pte")
        val lama = File(requireNotNull(modelsRoot), "lama_big_t512_xnnpack.pte")
        val colorizer = File(requireNotNull(modelsRoot), "ddcolor_paper_tiny_t512_xnnpack.pte")
        val enhancer = File(requireNotNull(modelsRoot), "realesr_general_x4v3.tflite")
        assumeTrue("BOPBTL, LaMa, DDColor and the Photo Restoration enhancer must be present", bop.isFile && lama.isFile && colorizer.isFile && enhancer.isFile)
        assertEquals("BOPBTL artifact hash", BOP_HASH, sha256(bop))
        assertEquals("LaMa artifact hash", LAMA_HASH, sha256(lama))
        assertEquals("DDColor artifact hash", COLOR_HASH, sha256(colorizer))
        assertEquals("Photo Restoration enhancer artifact hash", ENHANCER_HASH, sha256(enhancer))

        val fixtureFiles = File(requireNotNull(fixturesRoot)).listFiles()
            .orEmpty().filter { it.isFile && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }.sortedBy { it.name }
        assumeTrue("No supported sample photos found in restorationFixturesDir", fixtureFiles.isNotEmpty())

        val overlaps = args.getString("restorationOverlaps", "32,64,96")!!
            .split(',').map(String::trim).filter(String::isNotEmpty).map(String::toInt)
        val outputRoot = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "photo-restoration-experiments",
        ).apply { mkdirs() }
        val goldenRoot = args.getString("restorationGoldenDir")?.let(::File)
        val report = File(outputRoot, "metrics.csv")
        report.writeText("image,overlap_px,stride_px,elapsed_ms,heap_delta_kib,psnr_vs_golden,mask_iou_vs_golden\n")

        fixtureFiles.forEach { fixture ->
            val decoded = BitmapFactory.decodeFile(fixture.absolutePath)
                ?: throw AssertionError("Couldn't decode fixture ${fixture.name}")
            val source = ImageProcessorForTest.bound(decoded)
            if (source !== decoded) decoded.recycle()
            try {
                overlaps.forEach { overlap ->
                    val heapBefore = Debug.getNativeHeapAllocatedSize() + usedJavaHeap()
                    val started = SystemClock.elapsedRealtimeNanos()
                    val pipeline = PhotoRestorationPipeline(bop.absolutePath, lama.absolutePath, colorizer.absolutePath, enhancer.absolutePath, tileOverlap = overlap)
                    val result = try { pipeline.restore(source) } finally { pipeline.close() }
                    val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                    val heapAfter = Debug.getNativeHeapAllocatedSize() + usedJavaHeap()
                    val base = "${fixture.nameWithoutExtension}-overlap-$overlap"
                    saveBitmap(result.maskPreview, File(outputRoot, "$base.mask.png"))
                    saveBitmap(result.restored, File(outputRoot, "$base.restored.png"))
                    saveFloatMap(result.rawScratchPrediction, 384, 384, File(outputRoot, "$base.scratch-raw.png"))
                    assertEquals(source.width, result.maskPreview.width)
                    assertEquals(source.height, result.maskPreview.height)

                    val goldenMask = goldenRoot?.let { File(it, "${fixture.nameWithoutExtension}.mask.png") }
                        ?.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }
                    val goldenRestored = goldenRoot?.let { File(it, "${fixture.nameWithoutExtension}.restored.png") }
                        ?.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }
                    val maskIou = goldenMask?.let { maskIou(result.damageMask, source.width, source.height, it) }
                    val comparable = if (result.restored.width == source.width && result.restored.height == source.height) result.restored
                        else Bitmap.createScaledBitmap(result.restored, source.width, source.height, true)
                    val psnr = goldenRestored?.let { psnr(comparable, it) }
                    if (comparable !== result.restored) comparable.recycle()
                    goldenMask?.recycle()
                    goldenRestored?.recycle()
                    report.appendText("${fixture.name},$overlap,${512 - overlap},$elapsedMs,${(heapAfter - heapBefore) / 1024},${psnr ?: ""},${maskIou ?: ""}\n")
                    result.maskPreview.recycle()
                    result.restored.recycle()
                    System.gc()
                }
            } finally {
                source.recycle()
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        file.outputStream().use { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
    }

    private fun saveFloatMap(values: FloatArray, width: Int, height: Int, file: File) {
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 0f
        val range = max - min
        val pixels = IntArray(values.size) { i ->
            val gray = if (range == 0f) 0 else (((values[i] - min) / range) * 255f).toInt().coerceIn(0, 255)
            (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        try { saveBitmap(bitmap, file) } finally { bitmap.recycle() }
    }

    private fun maskIou(predicted: BooleanArray, width: Int, height: Int, golden: Bitmap): Double {
        if (golden.width != width || golden.height != height) return Double.NaN
        val pixels = IntArray(width * height)
        golden.getPixels(pixels, 0, width, 0, 0, width, height)
        var intersection = 0L
        var union = 0L
        for (i in pixels.indices) {
            val expected = (pixels[i] ushr 24) > 0 && (pixels[i] and 255) > 127
            if (predicted[i] && expected) intersection++
            if (predicted[i] || expected) union++
        }
        return if (union == 0L) 1.0 else intersection.toDouble() / union
    }

    private fun psnr(actual: Bitmap, golden: Bitmap): Double {
        if (actual.width != golden.width || actual.height != golden.height) return Double.NaN
        val a = IntArray(actual.width * actual.height)
        val b = IntArray(a.size)
        actual.getPixels(a, 0, actual.width, 0, 0, actual.width, actual.height)
        golden.getPixels(b, 0, golden.width, 0, 0, golden.width, golden.height)
        var error = 0.0
        for (i in a.indices) {
            val dr = ((a[i] ushr 16) and 255) - ((b[i] ushr 16) and 255)
            val dg = ((a[i] ushr 8) and 255) - ((b[i] ushr 8) and 255)
            val db = (a[i] and 255) - (b[i] and 255)
            error += (dr * dr + dg * dg + db * db) / 3.0
        }
        val mse = error / a.size
        return if (mse == 0.0) Double.POSITIVE_INFINITY else 10.0 * log10((255.0 * 255.0) / mse)
    }

    private fun usedJavaHeap(): Long = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private object ImageProcessorForTest {
        fun bound(bitmap: Bitmap): Bitmap {
            val maxPixels = 2_000_000L
            val pixels = bitmap.width.toLong() * bitmap.height
            if (pixels <= maxPixels) return bitmap
            val scale = sqrt(maxPixels.toDouble() / pixels)
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }
    }

    companion object {
        private const val BOP_HASH = "651299699f28ce2e7dedadcc4732137d7731d5cd3b21aeaa1395fbdfb8b232d5"
        private const val LAMA_HASH = "5a808536fcbe0624bb81f0bb8ad20fca051efd640e994a720a78d066204947f4"
        private const val COLOR_HASH = "a582fb6ae0d65c2c6424efcd53a8da38a3c38c5a22f7288b48aa29abbcfe6c89"
        private const val ENHANCER_HASH = "6d597b46d812ada42bdde641ec6e78c0ef0b78ff5a825fcfd74b9a1b3bcbe9a4"
    }
}
