package com.localai.cutout.core.inference

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localai.cutout.core.model.ModelManager
import com.localai.cutout.core.model.ModelSpecs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the attached Android device; downloads the small public model if it is not installed. */
@RunWith(AndroidJUnit4::class)
class RealEsrganDeviceTest {
    @Test fun downloadsVerifiesAndUpscalesTiledAndTinyImages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = ModelManager(context, ModelSpecs.imageUpscaler)
        if (!manager.installed()) manager.download { _, _ -> }
        assertTrue("SHA-256 checked model should be installed", manager.installed())
        val before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val engine = RealEsrganUpscaler.fromFile(manager.path())
        try {
            val source = Bitmap.createBitmap(160, 145, Bitmap.Config.ARGB_8888)
            for (y in 0 until source.height) for (x in 0 until source.width) {
                source.setPixel(x, y, if ((x / 12 + y / 12) % 2 == 0) Color.rgb(230, 120, 70) else Color.rgb(25, 80, 190))
            }
            val output = engine.process(source)
            assertEquals(640, output.width)
            assertEquals(580, output.height)
            assertTrue(output.getPixel(320, 290) ushr 24 == 255)
            assertTrue(output.getPixel(80, 100) != output.getPixel(500, 400))
            output.recycle(); source.recycle()

            val tiny = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { setPixel(0, 0, Color.MAGENTA) }
            val tinyOutput = engine.process(tiny)
            assertEquals(4, tinyOutput.width)
            assertEquals(4, tinyOutput.height)
            tinyOutput.recycle(); tiny.recycle()

            val used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            android.util.Log.i("LocoraUpscalerTest", "backend=${engine.backend}, lastInferenceMs=${engine.inferenceMs}, heapDeltaBytes=${used - before}, output=640x580")
        } finally { engine.close() }
    }
}
