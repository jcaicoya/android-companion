package com.cuarzopolar.companion.capture

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class CameraCapture(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var imageCapture: ImageCapture? = null
    private var bound = false

    fun bindCamera(analysis: ImageAnalysis? = null, onReady: () -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture
            try {
                provider.unbindAll()
                val useCases = buildList<UseCase> {
                    add(capture)
                    if (analysis != null) add(analysis)
                }
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    *useCases.toTypedArray()
                )
                bound = true
                Log.d("CameraCapture", "Camera bound (front)")
                onReady()
            } catch (e: Exception) {
                Log.e("CameraCapture", "Failed to bind camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePicture(onResult: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onError("Camera not ready")
            return
        }
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    Log.d("CameraCapture", "Photo captured")
                    val bytes = imageProxyToJpeg(image)
                    image.close()
                    onResult(bytes)
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraCapture", "Capture failed: ${exc.message}")
                    onError(exc.message ?: "Capture failed")
                }
            }
        )
    }

    private fun imageProxyToJpeg(image: ImageProxy): ByteArray {
        // ImageProxy from ImageCapture is always JPEG format
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }
}
