package com.example.opencvsampleandroid

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class ReceiptDetectResult(
    val bitmap: Bitmap?,
    val processingBitmaps: List<Bitmap>,
)

data class ContourDetectResult(
    val contour: MatOfPoint?,
    val processingBitmaps: List<Bitmap>,
)
object ReceiptDetector {
    const val TAG = "ReceiptDetector"
    fun detect(bitmap: Bitmap, returnProcessingBitmaps: Boolean): ReceiptDetectResult {
        val processingBitmaps = mutableListOf<Bitmap>()
        if (returnProcessingBitmaps) processingBitmaps.add(bitmap)

        val width = bitmap.width
        val height = bitmap.height
        val contourThatFillsImage = OpenCVUtils.pointsToMatOfPoint(listOf(
            Point(0.0, 0.0),
            Point(width - 1.0, 0.0),
            Point(width - 1.0, height - 1.0),
            Point(0.0, height - 1.0),
            Point(0.0, 0.0),
        ))
        val areaOfContourThatFillsImage = Imgproc.contourArea(contourThatFillsImage, false)
        Log.d(TAG, "***** areaOfContourThatFillsImage: $areaOfContourThatFillsImage")
        val contourAreaThreshold = areaOfContourThatFillsImage / 10

        val resultByReceiptEdges = ReceiptContourDetectorByReceiptEdges.detect(bitmap, false, returnProcessingBitmaps)
        if (returnProcessingBitmaps) processingBitmaps.addAll(resultByReceiptEdges.processingBitmaps)
        val contour = resultByReceiptEdges.contour
        Log.d(TAG, "***** contour1 exists: ${contour != null}")
        if (contour != null) {
            val contourArea = Imgproc.contourArea(contour, false)
            Log.d(TAG, "***** contourArea1: $contourArea")
            if (contourArea > contourAreaThreshold) {
                val extractedBitmap = OpenCVUtils.extractBitmapByContour(bitmap, contour)
                if (returnProcessingBitmaps) processingBitmaps.add(extractedBitmap)
                return ReceiptDetectResult(extractedBitmap, processingBitmaps)
            }
        }

        val resultByReceiptContent = ReceiptContourDetectorByContent.detect(bitmap, returnProcessingBitmaps)
        if (returnProcessingBitmaps) processingBitmaps.addAll(resultByReceiptContent.processingBitmaps)
        val contour2 = resultByReceiptContent.contour
        Log.d(TAG, "***** contour2 exists: ${contour2 != null}")
        if (contour2 != null) {
            val contourArea = Imgproc.contourArea(contour2, false)
            Log.d(TAG, "***** contourArea2: $contourArea")
            if (contourArea > contourAreaThreshold) {
                val extractedBitmap = OpenCVUtils.extractBitmapByContour(bitmap, contour2)
                if (returnProcessingBitmaps) processingBitmaps.add(extractedBitmap)
                return ReceiptDetectResult(extractedBitmap, processingBitmaps)
            }
        }

        val resultByReceiptEdgesConvexHull = ReceiptContourDetectorByReceiptEdges.detect(bitmap, true, returnProcessingBitmaps)
        if (returnProcessingBitmaps) processingBitmaps.addAll(resultByReceiptEdgesConvexHull.processingBitmaps)
        val contour3 = resultByReceiptEdgesConvexHull.contour
        Log.d(TAG, "***** contour3 exists: ${contour3 != null}")
        if (contour3 != null) {
            val contourArea = Imgproc.contourArea(contour3, false)
            Log.d(TAG, "***** contourArea1: $contourArea")
            if (contourArea > contourAreaThreshold) {
                val extractedBitmap = OpenCVUtils.extractBitmapByContour(bitmap, contour3)
                if (returnProcessingBitmaps) processingBitmaps.add(extractedBitmap)
                return ReceiptDetectResult(extractedBitmap, processingBitmaps)
            }
        }

        return ReceiptDetectResult(null, processingBitmaps)
    }
}

object ReceiptContourDetectorByReceiptEdges {
    fun detect(bitmap: Bitmap, convexHull: Boolean, returnProcessingBitmaps: Boolean): ContourDetectResult {
        val processingBitmaps = mutableListOf<Bitmap>()

        val original = Mat()
        Utils.bitmapToMat(bitmap, original)
        val imageMat = Mat()
        original.copyTo(imageMat)

        val resizeRatio = 500.0 / imageMat.height()
        OpenCVUtils.resize(imageMat, resizeRatio)

        Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_BGR2GRAY)

        Imgproc.GaussianBlur(imageMat, imageMat, Size(5.0, 5.0), 0.0)
        if (returnProcessingBitmaps) processingBitmaps.add(OpenCVUtils.toBitmap(imageMat, bitmap))

        val kernelForDilation = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        Imgproc.dilate(imageMat, imageMat, kernelForDilation)
        if (returnProcessingBitmaps) processingBitmaps.add(OpenCVUtils.toBitmap(imageMat, bitmap))

        val edgesMat = Mat()
        Imgproc.Canny(imageMat, edgesMat, 100.0, 200.0, 3)
        if (returnProcessingBitmaps) processingBitmaps.add(OpenCVUtils.toBitmap(edgesMat, bitmap))

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edgesMat, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE)

        val sortedContours: List<MatOfPoint> = contours.sortedByDescending { Imgproc.contourArea(it, false) }
        var largestContours = sortedContours.subList(0, min(sortedContours.size, 10))
        if (returnProcessingBitmaps) processingBitmaps.add(OpenCVUtils.toBitmap(OpenCVUtils.imageWithContourMat(original, largestContours, 1.0 / resizeRatio), bitmap))

        if (convexHull) {
            largestContours = largestContours.map {
                val indicesOfPointsWhichComposeHull = MatOfInt()
                Imgproc.convexHull(it, indicesOfPointsWhichComposeHull)
                val convexHullAppliedLargestContour = mutableListOf<Point>()
                for (i in 0 until indicesOfPointsWhichComposeHull.total()) {
                    val doubleArray = it.get(indicesOfPointsWhichComposeHull.get(i.toInt(), 0)[0].toInt(), 0)
                    val point = Point(doubleArray[0], doubleArray[1])
                    convexHullAppliedLargestContour.add(point)
                }
                val points: Array<Point> = convexHullAppliedLargestContour.toTypedArray()
                return@map MatOfPoint(*points)
            }
            if (returnProcessingBitmaps) processingBitmaps.add(OpenCVUtils.toBitmap(OpenCVUtils.imageWithContourMat(original, largestContours, 1.0 / resizeRatio), bitmap))
        }

        val receiptContour: Mat? = largestContours.firstOrNull {
            val approx = OpenCVUtils.approximateContour(it)
            return@firstOrNull approx.total() == 4L
        }
        val sizeRestoredReceiptContour = receiptContour?.let { OpenCVUtils.scaleContour(it, 1.0 / resizeRatio) }
        return ContourDetectResult(
            sizeRestoredReceiptContour,
            processingBitmaps,
        )
    }
}

object ReceiptContourDetectorByContent {
    fun detect(bitmap: Bitmap, returnProcessingBitmaps: Boolean): ContourDetectResult {
        val processingBitmaps = mutableListOf<Bitmap>()

        val original = Mat()
        Utils.bitmapToMat(bitmap, original)
        val imageMat = Mat()
        original.copyTo(imageMat)

        val resizeRatio = 500.0 / imageMat.height()
        OpenCVUtils.resize(imageMat, resizeRatio)

        Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_BGR2GRAY)

        Imgproc.GaussianBlur(imageMat, imageMat, Size(25.0, 25.0), 0.0) // 可能な限りノイズを除去。かつ文字を残す(存在するのがわかる程度)。
        if (returnProcessingBitmaps) processingBitmaps.add(OpenCVUtils.toBitmap(imageMat, bitmap))

        Imgproc.adaptiveThreshold(imageMat, imageMat, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0)
        if (returnProcessingBitmaps) processingBitmaps.add(OpenCVUtils.toBitmap(imageMat, bitmap))

        val kernelForErosion = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(61.0, 61.0))
        Imgproc.erode(imageMat, imageMat, kernelForErosion)
        if (returnProcessingBitmaps) processingBitmaps.add(OpenCVUtils.toBitmap(imageMat, bitmap))

        Core.bitwise_not(imageMat, imageMat) // findContoursのため反転。

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(imageMat, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE)

        val sortedContours: List<MatOfPoint> = contours.sortedByDescending { Imgproc.contourArea(it, false) }
        val largestContours = sortedContours.subList(0, min(sortedContours.size, 10))
        if (returnProcessingBitmaps) processingBitmaps.add(OpenCVUtils.toBitmap(OpenCVUtils.imageWithContourMat(original, largestContours, 1.0 / resizeRatio), bitmap))

        val convexHullAppliedLargestContours: List<MatOfPoint> = largestContours.map {
            val indicesOfPointsWhichComposeHull = MatOfInt()
            Imgproc.convexHull(it, indicesOfPointsWhichComposeHull)
            val convexHullAppliedLargestContour = mutableListOf<Point>()
            for (i in 0 until indicesOfPointsWhichComposeHull.total()) {
                val doubleArray = it.get(indicesOfPointsWhichComposeHull.get(i.toInt(), 0)[0].toInt(), 0)
                val point = Point(doubleArray[0], doubleArray[1])
                convexHullAppliedLargestContour.add(point)
            }
            OpenCVUtils.pointsToMatOfPoint(convexHullAppliedLargestContour)
        }
        if (returnProcessingBitmaps) processingBitmaps.add(OpenCVUtils.toBitmap(OpenCVUtils.imageWithContourMat(original, convexHullAppliedLargestContours, 1.0 / resizeRatio), bitmap))

        val receiptContour: Mat? = largestContours.firstOrNull {
            val approx = OpenCVUtils.approximateContour(it)
            return@firstOrNull approx.total() == 4L
        }
        val sizeRestoredReceiptContour = receiptContour?.let { OpenCVUtils.scaleContour(it, 1.0 / resizeRatio) }
        return ContourDetectResult(
            sizeRestoredReceiptContour,
            processingBitmaps,
        )
    }
}

object OpenCVUtils {

    fun resize(mat: Mat, resizeRatio: Double) {
        Imgproc.resize(mat, mat, Size(
            mat.width() * resizeRatio,
            mat.height() * resizeRatio,
        ))
    }

    fun toBitmap(mat: Mat, baseBitmap: Bitmap): Bitmap {
        val newBitmap = Bitmap.createScaledBitmap(baseBitmap.copy(baseBitmap.config, true), mat.width(), mat.height(), false)
        Utils.matToBitmap(mat, newBitmap)
        return newBitmap
    }

    fun approximateContour(contour: Mat): Mat {
        val contour2f = MatOfPoint2f()
        contour.convertTo(contour2f, CvType.CV_32FC2)
        val perimeterLength = Imgproc.arcLength(contour2f, true)
        val approxContour = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approxContour, 0.032 * perimeterLength, true)
        return approxContour
    }

    fun scaleContour(contour: Mat, scale: Double): MatOfPoint {
        return pointsToMatOfPoint((0 until contour.total().toInt()).map { index ->
            val point = contour.get(index, 0)
            Point(
                point[0] * scale,
                point[1] * scale,
            )
        })
    }

    fun pointsToMatOfPoint(points: List<Point>): MatOfPoint {
        return MatOfPoint(*(points.toTypedArray()))
    }

    fun extractBitmapByContour(bitmap: Bitmap, receiptContour: MatOfPoint): Bitmap {
        val original = Mat()
        Utils.bitmapToMat(bitmap, original)
        val receiptRect = contourToRect(receiptContour)
        val transformedMat = warpPerspective(original, receiptRect)

        val grayScaledMat = Mat().also { Imgproc.cvtColor(transformedMat, it, Imgproc.COLOR_BGR2GRAY) }
        Imgproc.adaptiveThreshold(grayScaledMat, grayScaledMat, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 21, 5.0)

        val newBitmap = Bitmap.createScaledBitmap(bitmap.copy(bitmap.config, true), grayScaledMat.width(), grayScaledMat.height(), false);
        Utils.matToBitmap(grayScaledMat, newBitmap)
        return newBitmap
    }

    private fun contourToRect(contour: Mat): Mat {
        val points = (0 until contour.total().toInt()).map { Point(contour.get(it, 0)) }
        val pointsSortedAscendingBySum: List<Point> = points.sortedBy { it.x + it.y }
        val pointsSortedAscendingByDiff: List<Point> = points.sortedBy { it.y - it.x }
        val pointsRepresentingRect = listOf(
            pointsSortedAscendingBySum.first(),
            pointsSortedAscendingByDiff.first(),
            pointsSortedAscendingBySum.last(),
            pointsSortedAscendingByDiff.last(),
        )
        return Mat(4, 1, CvType.CV_32FC2).also {
            for (i in pointsRepresentingRect.indices) {
                val point = pointsRepresentingRect[i]
                it.put(i, 0, floatArrayOf(point.x.toFloat(), point.y.toFloat()))
            }
        }
    }

    private fun warpPerspective(mat: Mat, rect: Mat): Mat {
        val topLeft = rect.get(0, 0)
        val topRight = rect.get(1, 0)
        val bottomRight = rect.get(2, 0)
        val bottomLeft = rect.get(3, 0)

        val widthA = distance(bottomRight, bottomLeft)
        val widthB = distance(topRight, topLeft)
        val heightA = distance(topRight, bottomRight)
        val heightB = distance(topLeft, bottomLeft)

        val maxWidth = max(widthA, widthB)
        val maxHeight = max(heightA, heightB)

        val transformMat = Imgproc.getPerspectiveTransform(
            rect,
            Mat(4, 1, CvType.CV_32FC2).also {
                it.put(0, 0, floatArrayOf(0f, 0f))
                it.put(1, 0, floatArrayOf(maxWidth - 1f, 0f))
                it.put(2, 0, floatArrayOf(maxWidth - 1f, maxHeight - 1f))
                it.put(3, 0, floatArrayOf(0f, maxHeight - 1f))
            }
        )

        return Mat().also {
            Imgproc.warpPerspective(mat, it, transformMat, Size(maxWidth.toDouble(), maxHeight.toDouble()))
        }
    }

    private fun distance(p1: DoubleArray, p2: DoubleArray): Int {
        return (sqrt((p1[0] - p2[0]).pow(2.0) + (p1[1] - p2[1]).pow(2.0))).toInt()
    }

    // debug用
    fun imageWithContourMat(mat: Mat, contours: List<Mat>, contourScale: Double): Mat {
        return Mat().also { imageWithContours ->
            mat.copyTo(imageWithContours)
            Imgproc.drawContours(
                imageWithContours,
                contours.map { contourMat -> scaleContour(contourMat, contourScale) },
                -1,
                Scalar(0.0, 255.0, 0.0, 255.0),
                24,
            )
        }
    }
}