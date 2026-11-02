package com.localai.cutout.core.inference

import android.graphics.Bitmap
import kotlin.math.pow

/**
 * DDColor Tiny image adapter following the upstream OpenCV/PyTorch example:
 * RGB bytes -> float [0,1] -> Lab L -> neutral Lab -> RGB NCHW at 512²;
 * output is two Lab chroma planes, resized with PyTorch's default nearest mode,
 * joined with original full-resolution L and converted back to RGB.
 */
class DdcolorColorizer(private val model: DdcolorRestorationPte) : AutoCloseable {
    fun process(source: Bitmap): Bitmap {
        require(!source.isRecycled && source.width > 0 && source.height > 0)
        val width = source.width
        val height = source.height
        val originalPixels = IntArray(width * height)
        source.getPixels(originalPixels, 0, width, 0, 0, width, height)
        val originalL = FloatArray(originalPixels.size)
        for (i in originalPixels.indices) originalL[i] = rgbToLab(originalPixels[i]).l

        val input = FloatArray(DdcolorRestorationPte.INPUT_ELEMENTS)
        val plane = 512 * 512
        for (y in 0 until 512) {
            val fy = ((y + 0.5f) * height / 512f - 0.5f).coerceIn(0f, (height - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = minOf(y0 + 1, height - 1)
            val wy = fy - y0
            for (x in 0 until 512) {
                val fx = ((x + 0.5f) * width / 512f - 0.5f).coerceIn(0f, (width - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = minOf(x0 + 1, width - 1)
                val wx = fx - x0
                val p00 = sourcePixel(originalPixels, width, x0, y0)
                val p10 = sourcePixel(originalPixels, width, x1, y0)
                val p01 = sourcePixel(originalPixels, width, x0, y1)
                val p11 = sourcePixel(originalPixels, width, x1, y1)
                val r = bilinear(channel(p00, 16), channel(p10, 16), channel(p01, 16), channel(p11, 16), wx, wy) / 255f
                val g = bilinear(channel(p00, 8), channel(p10, 8), channel(p01, 8), channel(p11, 8), wx, wy) / 255f
                val b = bilinear(channel(p00, 0), channel(p10, 0), channel(p01, 0), channel(p11, 0), wx, wy) / 255f
                val lab = rgbToLab(r, g, b)
                val neutralRgb = labToRgb(lab.l, 0f, 0f)
                val i = y * 512 + x
                input[i] = neutralRgb.r
                input[plane + i] = neutralRgb.g
                input[2 * plane + i] = neutralRgb.b
            }
        }

        val ab = model.predictChroma(input)
        val pixels = IntArray(originalPixels.size)
        for (y in 0 until height) for (x in 0 until width) {
            // torch.nn.functional.interpolate defaults to nearest when mode is omitted.
            val sx = (x.toLong() * 512 / width).toInt().coerceIn(0, 511)
            val sy = (y.toLong() * 512 / height).toInt().coerceIn(0, 511)
            val sample = sy * 512 + sx
            val rgb = labToRgb(originalL[y * width + x], ab[sample], ab[plane + sample])
            pixels[y * width + x] = (0xff shl 24) or
                (toByte(rgb.r) shl 16) or (toByte(rgb.g) shl 8) or toByte(rgb.b)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    override fun close() = model.close()

    private data class Lab(val l: Float, val a: Float, val b: Float)
    private data class Rgb(val r: Float, val g: Float, val b: Float)

    private fun rgbToLab(argb: Int): Lab = rgbToLab(channel(argb, 16) / 255f, channel(argb, 8) / 255f, channel(argb, 0) / 255f)

    private fun rgbToLab(r8: Float, g8: Float, b8: Float): Lab {
        val r = linearize(r8)
        val g = linearize(g8)
        val b = linearize(b8)
        val x = (0.4124564f * r + 0.3575761f * g + 0.1804375f * b) / 0.95047f
        val y = 0.2126729f * r + 0.7151522f * g + 0.0721750f * b
        val z = (0.0193339f * r + 0.1191920f * g + 0.9503041f * b) / 1.08883f
        val fx = labPivot(x)
        val fy = labPivot(y)
        val fz = labPivot(z)
        return Lab(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    private fun labToRgb(l: Float, a: Float, b: Float): Rgb {
        val fy = (l + 16f) / 116f
        val fx = fy + a / 500f
        val fz = fy - b / 200f
        val x = 0.95047f * inverseLabPivot(fx)
        val y = inverseLabPivot(fy)
        val z = 1.08883f * inverseLabPivot(fz)
        val r = 3.2404542f * x - 1.5371385f * y - 0.4985314f * z
        val g = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z
        val blue = 0.0556434f * x - 0.2040259f * y + 1.0572252f * z
        return Rgb(gamma(r), gamma(g), gamma(blue))
    }

    private fun linearize(value: Float): Float = if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    private fun gamma(value: Float): Float {
        val c = value.coerceIn(0f, 1f)
        return if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
    }
    private fun labPivot(value: Float): Float = if (value > 0.008856452f) value.pow(1f / 3f) else 7.787037f * value + 16f / 116f
    private fun inverseLabPivot(value: Float): Float {
        val cube = value * value * value
        return if (cube > 0.008856452f) cube else (value - 16f / 116f) / 7.787037f
    }
    private fun channel(argb: Int, shift: Int) = (argb ushr shift) and 255
    private fun sourcePixel(pixels: IntArray, width: Int, x: Int, y: Int) = pixels[y * width + x]
    private fun bilinear(p00: Int, p10: Int, p01: Int, p11: Int, wx: Float, wy: Float): Float {
        val top = p00 * (1f - wx) + p10 * wx
        val bottom = p01 * (1f - wx) + p11 * wx
        return top * (1f - wy) + bottom * wy
    }
    private fun toByte(value: Float) = (value.coerceIn(0f, 1f) * 255f + .5f).toInt()
}
