package com.example.opencvsampleandroid

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
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

object RectangleDetector {
    fun detectRectangle(bitmap: Bitmap): Bitmap? {
        val original = Mat()
        Utils.bitmapToMat(bitmap, original)
        val imageMat = Mat()
        original.copyTo(imageMat)

        val resizeRatio = 500.0 / imageMat.height()
        resize(imageMat, resizeRatio)

        Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_BGR2GRAY)

        Imgproc.GaussianBlur(imageMat, imageMat, Size(5.0, 5.0), 0.0)

        val kernelForDilation = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        Imgproc.dilate(imageMat, imageMat, kernelForDilation)

        val edgesMat = Mat()
        Imgproc.Canny(imageMat, edgesMat, 100.0, 200.0, 3)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edgesMat, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE)

        val sortedContours: List<Mat> = contours.sortedByDescending { Imgproc.contourArea(it, false) }
        val largestContours = sortedContours.subList(0, min(sortedContours.size, 10))
        val receiptContour: Mat? = largestContours.firstOrNull {
            val approx = approximateContour(it)
            return@firstOrNull approx.total() == 4L
        }

        if (receiptContour == null) {
            Log.w("RectangleDetector", "receiptContour is null")
            return null
        }

        /*
        // 検証: receiptContourの表示
        val imageWithContoursMat = imageWithContourMat(original, listOf(receiptContour), 1.0 / resizeRatio)
        val newBitmap2 = Bitmap.createScaledBitmap(bitmap.copy(bitmap.config, true), imageWithContoursMat.width(), imageWithContoursMat.height(), false);
        Utils.matToBitmap(imageWithContoursMat, newBitmap2)
        return newBitmap2
         */

        val receiptRect = contourToRect(receiptContour)
        val receiptRectInOriginal = Mat(4, 1, CvType.CV_32FC2).also { receiptRectInOriginal ->
            (0 until receiptRect.total().toInt()).forEach {
                val point = receiptRect.get(it, 0)
                receiptRectInOriginal.put(it, 0, floatArrayOf(
                    (point[0] * (1.0 / resizeRatio)).toFloat(),
                    (point[1] * (1.0 / resizeRatio)).toFloat(),
                ))
            }
        }
        val transformedMat = warpPerspective(original, receiptRectInOriginal)

        val grayScaledMat = Mat().also { Imgproc.cvtColor(transformedMat, it, Imgproc.COLOR_BGR2GRAY) }
        Imgproc.adaptiveThreshold(grayScaledMat, grayScaledMat, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 21, 5.0)

        val newBitmap = Bitmap.createScaledBitmap(bitmap.copy(bitmap.config, true), grayScaledMat.width(), grayScaledMat.height(), false);
        Utils.matToBitmap(grayScaledMat, newBitmap)
        return newBitmap
    }

    private fun resize(mat: Mat, resizeRatio: Double) {
        Imgproc.resize(mat, mat, Size(
            mat.width() * resizeRatio,
            mat.height() * resizeRatio,
        ))
    }

    private fun approximateContour(contour: Mat): Mat {
        val contour2f = MatOfPoint2f()
        contour.convertTo(contour2f, CvType.CV_32FC2)
        val perimeterLength = Imgproc.arcLength(contour2f, true)
        val approxContour = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approxContour, 0.032 * perimeterLength, true)
        return approxContour
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

    private fun imageWithContourMat(mat: Mat, contours: List<Mat>, contourScale: Double): Mat {
        return Mat().also { imageWithContours ->
            mat.copyTo(imageWithContours)
            Imgproc.drawContours(
                imageWithContours,
                contours.map { contourMat -> MatOfPoint(
                    *(0 until contourMat.total().toInt()).map { index ->
                        val point = contourMat.get(index, 0)
                        Point(
                            point[0] * contourScale,
                            point[1] * contourScale,
                        )
                    }.toTypedArray()
                )},
                -1,
                Scalar(0.0, 255.0, 0.0, 255.0),
                24,
            )
        }
    }
}