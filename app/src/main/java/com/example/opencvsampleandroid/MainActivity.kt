package com.example.opencvsampleandroid

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader


class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainAct"
    }
    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")
                    configureImages()
                }

                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        // https://github.com/opencv/opencv/blob/b3d3acf75f007e7ca10a43309ada6360105e970e/samples/android/tutorial-1-camerapreview/src/org/opencv/samples/tutorial1/Tutorial1Activity.java#L75
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private fun configureImages() {
        // ref: https://techbooster.org/android/multimedia/15681/

        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.receipt_light)

        /*
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
        // > グレスケールを行ったままのMatの状態ではBitmapに変換することは不可能なため、この処理が必要になります。
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_GRAY2BGR, 4)

        val newBitmap = bitmap.copy(bitmap.config, true)
        Utils.matToBitmap(mat, newBitmap)
         */

        findViewById<ImageView>(R.id.image1).setImageBitmap(bitmap)
        val newBitmap = RectangleDetector.detectRectangle(bitmap)
        if (newBitmap != null) {
            findViewById<ImageView>(R.id.image2).setImageBitmap(newBitmap)
        }
    }
}