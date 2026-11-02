package com.localai.cutout.core.document

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.android.Utils
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A line recognized by the PP-OCRv5 detector/recognizer; bounds are in source-image pixels. */
data class OcrBounds(val left: Int, val top: Int, val width: Int, val height: Int) { val bottom get() = top + height }
data class OcrLine(val text: String, val confidence: Float, val bounds: OcrBounds)
data class OcrPageResult(val lines: List<OcrLine>) { val text get() = lines.joinToString("\n") { it.text } }
data class NormalizedQuad(val points: List<Pair<Float, Float>>) {
    companion object { fun full() = NormalizedQuad(listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)) }
}

/** OpenCV document geometry, image filters and PP-OCRv5 Mobile ONNX inference. */
class DocumentOcrEngine(context: Context, private val modelDirectory: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
    private val det: OrtSession
    private val rec: OrtSession
    private val alphabet: List<String>
    private val detName: String
    private val recName: String
    private val detShape: LongArray
    private val recShape: LongArray

    init {
        check(OpenCVLoader.initLocal()) { "Image tools couldn't start on this phone." }
        det = env.createSession(File(modelDirectory, "det.onnx").absolutePath, options)
        rec = env.createSession(File(modelDirectory, "rec.onnx").absolutePath, options)
        detName = det.inputNames.singleOrNull() ?: error("Unsupported text detector model")
        recName = rec.inputNames.singleOrNull() ?: error("Unsupported text recognizer model")
        val detInfo = det.inputInfo[detName]?.info as? TensorInfo ?: error("Unsupported text detector input")
        val recInfo = rec.inputInfo[recName]?.info as? TensorInfo ?: error("Unsupported text recognizer input")
        require(detInfo.type == ai.onnxruntime.OnnxJavaType.FLOAT && recInfo.type == ai.onnxruntime.OnnxJavaType.FLOAT) { "This OCR model needs a different input type." }
        detShape = detInfo.shape
        recShape = recInfo.shape
        require(detShape.size == 4 && recShape.size == 4) { "This OCR model has an unsupported image layout." }
        require(detShape[1] == 3L && recShape[1] == 3L && recShape[2] == 48L) { "This OCR model uses an unsupported image format." }
        require(recShape[3] <= 0L || recShape[3] <= 3200L) { "This OCR recognizer expects a different crop width." }
        alphabet = File(modelDirectory, "latin_dict.txt").readLines(Charsets.UTF_8).filter { it.isNotEmpty() }
        require(alphabet.size == 836) { "The OCR language file is incomplete. Download the models again." }
        val outputDims = (rec.outputInfo.values.first().info as? TensorInfo)?.shape ?: error("Unsupported text recognizer output")
        require(outputDims.lastOrNull()?.let { it < 0L || it == alphabet.size + 2L } == true) { "The OCR recognizer and language file don't match." }
    }

    suspend fun scan(bitmap: Bitmap, onProgress: (Int, Int) -> Unit = { _, _ -> }): OcrPageResult = withContext(Dispatchers.Default) {
        val detections = detect(bitmap)
        val lines = ArrayList<OcrLine>(detections.size)
        detections.forEachIndexed { index, bounds ->
            coroutineContext.ensureActive()
            val crop = cropLine(bitmap, bounds)
            try { recognize(crop)?.let { (text, confidence) -> if (text.isNotBlank()) lines += OcrLine(text, confidence, OcrBounds(bounds.x, bounds.y, bounds.width, bounds.height)) } }
            finally { crop.recycle() }
            onProgress(index + 1, detections.size)
        }
        OcrPageResult(lines)
    }

    private fun detect(bitmap: Bitmap): List<Rect> {
        val maxSide = 960f
        // Paddle's published detector resize limits the long edge; small scans are not enlarged.
        val scale = min(1f, maxSide / max(bitmap.width, bitmap.height))
        val targetW = round32(max(32, (bitmap.width * scale).toInt()))
        val targetH = round32(max(32, (bitmap.height * scale).toInt()))
        val resized = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val chw = bitmapToChw(resized, targetW, targetH, 1f / 255f, floatArrayOf(.485f, .456f, .406f), floatArrayOf(.229f, .224f, .225f))
        if (resized !== bitmap) resized.recycle()
        val dims = longArrayOf(1, 3, targetH.toLong(), targetW.toLong())
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), dims)
        val result = try { det.run(mapOf(detName to tensor)) } finally { tensor.close() }
        result.use {
            val output = it[0] as? OnnxTensor ?: error("The text detector returned an unsupported result")
            val info = output.info
            val shape = info.shape
            require(shape.size in 3..4) { "The text detector returned an unsupported result" }
            val data = FloatArray(shape.fold(1L) { a, b -> a * b }.toInt())
            output.floatBuffer.get(data)
            val mapH: Int
            val mapW: Int
            if (shape.size == 3) { mapH = shape[1].toInt(); mapW = shape[2].toInt() }
            else if (shape[1] == 1L || shape[1] < 0L) { mapH = shape[2].toInt(); mapW = shape[3].toInt() }
            else { mapH = shape[1].toInt(); mapW = shape[2].toInt() }
            require(mapH > 0 && mapW > 0 && data.size >= mapH * mapW) { "The text detector returned an invalid map" }
            val scoreMap = Mat(mapH, mapW, CvType.CV_32FC1)
            scoreMap.put(0, 0, data.copyOf(mapH * mapW))
            val binary = Mat()
            Imgproc.threshold(scoreMap, binary, .3, 255.0, Imgproc.THRESH_BINARY)
            binary.convertTo(binary, CvType.CV_8UC1)
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(binary, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val boxes = contours.take(1000).mapNotNull { contour ->
                val area = Imgproc.contourArea(contour)
                if (area < 3.0) return@mapNotNull null
                val scoreMask = Mat.zeros(mapH, mapW, CvType.CV_8UC1)
                Imgproc.drawContours(scoreMask, listOf(contour), -1, Scalar(255.0), -1)
                val score = Core.mean(scoreMap, scoreMask).`val`[0]
                scoreMask.release()
                if (score < .6) return@mapNotNull null
                val rect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
                val width = rect.size.width
                val height = rect.size.height
                if (min(width, height) < 3.0) return@mapNotNull null
                val points = arrayOf(Point(), Point(), Point(), Point())
                rect.points(points)
                val left = points.minOf { p -> p.x }.coerceIn(0.0, mapW.toDouble())
                val top = points.minOf { p -> p.y }.coerceIn(0.0, mapH.toDouble())
                val right = points.maxOf { p -> p.x }.coerceIn(0.0, mapW.toDouble())
                val bottom = points.maxOf { p -> p.y }.coerceIn(0.0, mapH.toDouble())
                if ((right - left) * (bottom - top) < 16.0) null else {
                    val ratioX = bitmap.width.toFloat() / mapW
                    val ratioY = bitmap.height.toFloat() / mapH
                val unclipDistance = ((right - left) * (bottom - top) * 1.5) / (2.0 * ((right - left) + (bottom - top)))
                val padX = unclipDistance
                val padY = unclipDistance
                    Rect(((left - padX) * ratioX).toInt().coerceAtLeast(0), ((top - padY) * ratioY).toInt().coerceAtLeast(0), ((right - left + 2 * padX) * ratioX).toInt().coerceAtLeast(1), ((bottom - top + 2 * padY) * ratioY).toInt().coerceAtLeast(1))
                }
            }.distinctBy { listOf(it.x / 3, it.y / 3, it.width / 3, it.height / 3) }
                .sortedWith(compareBy<Rect> { it.y / max(12, it.height) }.thenBy { it.x })
            contours.forEach { it.release() }; scoreMap.release(); binary.release()
            return boxes
        }
    }

    private fun recognize(bounds: Bitmap): Pair<String, Float>? {
        val height = 48
        val ratio = bounds.width.toFloat() / bounds.height
        val maxWidth = recShape[3].takeIf { it > 0 }?.toInt() ?: 3200
        val width = min(maxWidth, max(1, ceil(height * ratio).toInt()))
        val resized = Bitmap.createScaledBitmap(bounds, width, height, true)
        val pixels = IntArray(width * height); resized.getPixels(pixels, 0, width, 0, 0, width, height)
        if (resized !== bounds) resized.recycle()
        val targetW = recShape[3].takeIf { it > 0 }?.toInt() ?: width
        val input = FloatArray(3 * height * targetW) // PP RecResizeImg pads with black before [-1,1] normalization.
        val plane = height * targetW
        for (y in 0 until height) for (x in 0 until width) {
            val c = pixels[y * width + x]
            val i = y * targetW + x
            input[i] = (((c and 0xff) / 255f) - .5f) / .5f
            input[plane + i] = ((((c shr 8) and 0xff) / 255f) - .5f) / .5f
            input[2 * plane + i] = ((((c shr 16) and 0xff) / 255f) - .5f) / .5f
        }
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, height.toLong(), targetW.toLong()))
        val result = try { rec.run(mapOf(recName to tensor)) } finally { tensor.close() }
        result.use {
            val output = it[0] as? OnnxTensor ?: return null
            val shape = output.info.shape
            require(shape.size in 2..3) { "The text recognizer returned an unsupported result" }
            val timeSteps = shape[shape.size - 2].toInt()
            val classes = shape.last().toInt()
            require(classes == alphabet.size + 2 && timeSteps > 0) { "The OCR language file doesn't match the recognizer." }
            val values = FloatArray(timeSteps * classes); output.floatBuffer.get(values)
            val text = StringBuilder(); var last = 0; var confidence = 0f; var count = 0
            for (t in 0 until timeSteps) {
                val offset = t * classes
                var best = 0; var bestScore = Float.NEGATIVE_INFINITY
                for (c in 0 until classes) if (values[offset + c] > bestScore) { best = c; bestScore = values[offset + c] }
                if (best != 0 && best != last) {
                    val token = if (best == classes - 1) " " else alphabet.getOrNull(best - 1).orEmpty()
                    text.append(token); confidence += bestScore; count++
                }
                last = best
            }
            return text.toString().trim() to if (count == 0) 0f else confidence / count
        }
    }

    private fun bitmapToChw(bitmap: Bitmap, width: Int, height: Int, scale: Float, mean: FloatArray, std: FloatArray): FloatArray {
        val pixels = IntArray(width * height); bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val plane = width * height; val out = FloatArray(plane * 3)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16 and 255) * scale; val g = (c shr 8 and 255) * scale; val b = (c and 255) * scale
            out[i] = (b - mean[0]) / std[0]
            out[plane + i] = (g - mean[1]) / std[1]
            out[2 * plane + i] = (r - mean[2]) / std[2]
        }
        return out
    }

    private fun cropLine(source: Bitmap, rect: Rect): Bitmap {
        val x = rect.x.coerceIn(0, source.width - 1); val y = rect.y.coerceIn(0, source.height - 1)
        val w = rect.width.coerceAtMost(source.width - x).coerceAtLeast(1); val h = rect.height.coerceAtMost(source.height - y).coerceAtLeast(1)
        return Bitmap.createBitmap(source, x, y, w, h)
    }

    override fun close() { det.close(); rec.close(); options.close() }
    companion object { private fun round32(value: Int) = max(32, (value / 32f).roundToInt() * 32) }
}

object DocumentImageProcessor {
    fun detectCorners(bitmap: Bitmap): NormalizedQuad {
        val src = bitmapToMat(bitmap); val gray = Mat(); val blur = Mat(); val edge = Mat()
        return try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blur, edge, 50.0, 150.0)
            val contours = ArrayList<MatOfPoint>(); val hierarchy = Mat(); Imgproc.findContours(edge, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val quad = contours.asSequence().sortedByDescending { Imgproc.contourArea(it) }.take(20).mapNotNull { contour ->
                val curve = MatOfPoint2f(*contour.toArray()); val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(curve, approx, Imgproc.arcLength(curve, true) * .02, true)
                val pts = approx.toArray().toList(); curve.release(); approx.release()
                val polygon = MatOfPoint(*pts.toTypedArray())
                val result = if (pts.size == 4 && Imgproc.contourArea(polygon) > bitmap.width * bitmap.height * .08) orderQuad(pts) else null
                polygon.release(); result
            }.firstOrNull()
            contours.forEach { it.release() }; hierarchy.release()
            NormalizedQuad(quad?.map { (it.x / bitmap.width).toFloat().coerceIn(0f, 1f) to (it.y / bitmap.height).toFloat().coerceIn(0f, 1f) } ?: NormalizedQuad.full().points)
        } finally { src.release(); gray.release(); blur.release(); edge.release() }
    }

    fun warp(bitmap: Bitmap, quad: NormalizedQuad): Bitmap {
        require(quad.points.size == 4)
        val src = bitmapToMat(bitmap)
        val p = quad.points.map { Point((it.first * bitmap.width).toDouble(), (it.second * bitmap.height).toDouble()) }
        val w = max(1.0, max(distance(p[0], p[1]), distance(p[3], p[2])))
        val h = max(1.0, max(distance(p[0], p[3]), distance(p[1], p[2])))
        val from = MatOfPoint2f(*p.toTypedArray())
        val to = MatOfPoint2f(Point(0.0, 0.0), Point(w - 1, 0.0), Point(w - 1, h - 1), Point(0.0, h - 1))
        val transform = Imgproc.getPerspectiveTransform(from, to); val out = Mat()
        return try { Imgproc.warpPerspective(src, out, transform, Size(w, h)); matToBitmap(out) }
        finally { src.release(); from.release(); to.release(); transform.release(); out.release() }
    }

    fun filter(bitmap: Bitmap, mode: String): Bitmap {
        if (mode == "Original") return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val src = bitmapToMat(bitmap); val dst = Mat()
        try {
            when (mode) {
                "Grayscale" -> { Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGBA2GRAY); Imgproc.cvtColor(dst, dst, Imgproc.COLOR_GRAY2RGBA) }
                "Black & white" -> { Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGBA2GRAY); Imgproc.adaptiveThreshold(dst, dst, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 31, 12.0); Imgproc.cvtColor(dst, dst, Imgproc.COLOR_GRAY2RGBA) }
                "Contrast" -> {
                    val rgb = Mat()
                    Imgproc.cvtColor(src, rgb, Imgproc.COLOR_RGBA2RGB)
                    Imgproc.cvtColor(rgb, dst, Imgproc.COLOR_RGB2YCrCb)
                    rgb.release()
                    val channels = ArrayList<Mat>()
                    Core.split(dst, channels)
                    Imgproc.equalizeHist(channels[0], channels[0])
                    Core.merge(channels, dst)
                    channels.forEach { it.release() }
                    val restoredRgb = Mat()
                    Imgproc.cvtColor(dst, restoredRgb, Imgproc.COLOR_YCrCb2RGB)
                    Imgproc.cvtColor(restoredRgb, dst, Imgproc.COLOR_RGB2RGBA)
                    restoredRgb.release()
                }
            }
            return matToBitmap(dst)
        } finally { src.release(); dst.release() }
    }

    private fun bitmapToMat(bitmap: Bitmap): Mat {
        check(OpenCVLoader.initLocal()) { "Image tools couldn't start on this phone." }
        return Mat().also { Utils.bitmapToMat(bitmap, it) }
    }
    private fun matToBitmap(mat: Mat) = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(mat, it) }
    private fun distance(a: Point, b: Point) = kotlin.math.hypot(a.x - b.x, a.y - b.y)
    private fun orderQuad(points: List<Point>): List<Point> {
        val tl = points.minBy { it.x + it.y }; val br = points.maxBy { it.x + it.y }
        val tr = points.minBy { it.y - it.x }; val bl = points.maxBy { it.y - it.x }
        return listOf(tl, tr, br, bl)
    }
}
