package com.cuarzopolar.companion.capture

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class VideoStreamManager(
    private val onFrame: (ByteArray) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var streaming = false
    private val frameIntervalMs = 66L   // ~15 fps
    private var lastFrameMs = 0L

    val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setTargetResolution(Size(320, 240))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also { analysis ->
            analysis.setAnalyzer(executor) { image ->
                try {
                    if (streaming) {
                        val now = System.currentTimeMillis()
                        if (now - lastFrameMs >= frameIntervalMs) {
                            lastFrameMs = now
                            val jpeg = toJpeg(image)
                            onFrame(jpeg)
                        }
                    }
                } finally {
                    image.close()
                }
            }
        }

    fun startStreaming() {
        streaming = true
        Log.d("VideoStream", "Streaming started")
    }

    fun stopStreaming() {
        streaming = false
        Log.d("VideoStream", "Streaming stopped")
    }

    fun isStreaming() = streaming

    private fun toJpeg(image: ImageProxy): ByteArray {
        val plane = image.planes[0]
        val bmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(plane.buffer)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 50, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
