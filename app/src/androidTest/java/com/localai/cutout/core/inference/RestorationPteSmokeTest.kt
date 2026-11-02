package com.localai.cutout.core.inference

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import java.security.MessageDigest

/**
 * Device smoke tests for the PTE artifacts. Provide `restorationModelsDir` as an
 * instrumentation argument pointing to a directory containing the PTE artifacts.
 * The PTEs are deliberately not packaged in the APK or test APK.
 *
 * Zero-filled tensors only check that the exported graph loads and executes with
 * the published tensor dimensions. They are not valid semantic/golden inputs.
 */
@RunWith(AndroidJUnit4::class)
class RestorationPteSmokeTest {
    private data class PteCase(
        val fileName: String,
        val sha256: String,
        val inputShape: LongArray,
        val outputElements: Int,
    )

    private val cases = listOf(
        PteCase(
            fileName = "bopbtl_scratch_t384_xnnpack_int8.pte",
            sha256 = "651299699f28ce2e7dedadcc4732137d7731d5cd3b21aeaa1395fbdfb8b232d5",
            inputShape = longArrayOf(1, 3, 384, 384),
            outputElements = 1 * 1 * 384 * 384,
        ),
        PteCase(
            fileName = "lama_big_t512_xnnpack.pte",
            sha256 = "5a808536fcbe0624bb81f0bb8ad20fca051efd640e994a720a78d066204947f4",
            inputShape = longArrayOf(1, 4, 512, 512),
            outputElements = 1 * 3 * 512 * 512,
        ),
        PteCase(
            fileName = "ddcolor_paper_tiny_t512_xnnpack.pte",
            sha256 = "a582fb6ae0d65c2c6424efcd53a8da38a3c38c5a22f7288b48aa29abbcfe6c89",
            inputShape = longArrayOf(1, 3, 512, 512),
            outputElements = 1 * 2 * 512 * 512,
        ),
    )

    @Test fun bopbtlLoadsRunsAndWritesDiagnosticMaskVisualization() {
        val spec = cases.first()
        val output = loadAndRun(spec)
        val visualization = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "restoration-debug/bopbtl-raw-output-minmax.png",
        )
        visualization.parentFile?.mkdirs()
        writeMinMaxVisualization(output, 384, 384, visualization)
        assertTrue("Diagnostic image was not written", visualization.isFile && visualization.length() > 0)
    }

    @Test fun lamaLoadsAndRunsAtPublishedTileTensorSize() {
        val output = loadAndRun(cases[1])
        assertEquals(1 * 3 * 512 * 512, output.size)
    }

    @Test fun ddcolorLoadsRunsAndColorizerPreservesImageDimensions() {
        val spec = cases[2]
        loadAndRun(spec)
        val args = InstrumentationRegistry.getArguments()
        val modelFile = File(requireNotNull(args.getString("restorationModelsDir")), spec.fileName)
        val source = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val gray = (x * 255 / source.width).coerceIn(0, 255)
            source.setPixel(x, y, (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray)
        }
        val colorized = DdcolorRestorationPte(modelFile.absolutePath).use { DdcolorColorizer(it).process(source) }
        try {
            assertEquals(source.width, colorized.width)
            assertEquals(source.height, colorized.height)
        } finally {
            source.recycle()
            colorized.recycle()
        }
    }

    private fun loadAndRun(spec: PteCase): FloatArray {
        val args = InstrumentationRegistry.getArguments()
        val modelsDir = args.getString("restorationModelsDir")
        assumeTrue(
            "Set instrumentation argument restorationModelsDir to run PTE smoke tests",
            !modelsDir.isNullOrBlank(),
        )
        val model = File(requireNotNull(modelsDir), spec.fileName)
        assumeTrue("PTE missing: ${model.absolutePath}", model.isFile)
        assertEquals("PTE SHA-256 mismatch: ${spec.fileName}", spec.sha256, sha256(model))

        val inputSize = spec.inputShape.fold(1L) { acc, dim -> acc * dim }.toInt()
        val input = Tensor.fromBlob(FloatArray(inputSize), spec.inputShape)
        val values = Module.load(model.absolutePath).use { module ->
            module.forward(EValue.from(input)).also { assertEquals("Expected one output", 1, it.size) }
        }
        val output = values.single().toTensor().dataAsFloatArray
        assertEquals("Unexpected output element count", spec.outputElements, output.size)
        assertTrue("PTE output contains non-finite values", output.all(Float::isFinite))
        return output
    }

    /** Min/max mapping is for visual inspection only; it is not a mask threshold. */
    private fun writeMinMaxVisualization(values: FloatArray, width: Int, height: Int, file: File) {
        require(values.size == width * height)
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 0f
        val range = max - min
        val pixels = IntArray(values.size) { index ->
            val gray = if (range > 0f) (((values[index] - min) / range) * 255f).toInt() else 0
            (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
