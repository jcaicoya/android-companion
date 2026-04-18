package com.cuarzopolar.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cuarzopolar.companion.capture.CameraCapture
import com.cuarzopolar.companion.capture.SpeechManager
import com.cuarzopolar.companion.capture.VideoStreamManager
import com.cuarzopolar.companion.commands.CommandHandler
import com.cuarzopolar.companion.databinding.ActivityMainBinding
import com.cuarzopolar.companion.network.ConnectionState
import com.cuarzopolar.companion.network.MessageDispatcher
import com.cuarzopolar.companion.network.WebSocketManager
import com.cuarzopolar.companion.network.UdpDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val wsManager = WebSocketManager()
    private lateinit var commandHandler: CommandHandler
    private lateinit var dispatcher: MessageDispatcher
    private lateinit var speechManager: SpeechManager
    private lateinit var cameraCapture: CameraCapture
    private lateinit var videoStreamManager: VideoStreamManager

    private var micActive = false
    private var awaitingPhoto = false
    private var discoveryJob: Job? = null

    // Permission launchers
    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggleMic() else toast("Permiso de micrófono denegado")
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraCapture.bindCamera(videoStreamManager.imageAnalysis) { takePhoto() }
        else toast("Permiso de cámara denegado")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        commandHandler = CommandHandler(applicationContext)
        dispatcher     = MessageDispatcher(commandHandler)

        speechManager = SpeechManager(this) { transcript ->
            // Send to Qt over WebSocket
            wsManager.sendText("""{"type":"transcript","text":"${transcript.replace("\"", "\\\"")}"}""")
            // Also show locally
            runOnUiThread { binding.tvTranscript.text = transcript }
        }

        videoStreamManager = VideoStreamManager { jpegBytes ->
            if (wsManager.connectionState.value == ConnectionState.CONNECTED) {
                wsManager.sendBinary(jpegBytes)
            }
        }
        cameraCapture = CameraCapture(this, this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            cameraCapture.bindCamera(videoStreamManager.imageAnalysis) {}
        }

        // Handle incoming binary frames (transformed photo from Qt)
        wsManager.onBinaryMessage = { bytes ->
            runOnUiThread {
                if (awaitingPhoto) {
                    awaitingPhoto = false
                    showTransformedPhoto(bytes)
                }
            }
        }

        // Restore saved IP
        val prefs = getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
        binding.etIp.setText(prefs.getString("last_ip", ""))

        // Route incoming text messages, intercepting stream commands
        wsManager.onTextMessage = { msg ->
            try {
                val obj = org.json.JSONObject(msg)
                if (obj.optString("type") == "command") {
                    when (obj.optString("action")) {
                        "startStream" -> runOnUiThread { activateStream() }
                        "stopStream"  -> runOnUiThread { deactivateStream() }
                        else          -> dispatcher.dispatch(msg)
                    }
                } else {
                    dispatcher.dispatch(msg)
                }
            } catch (_: Exception) {
                dispatcher.dispatch(msg)
            }
        }

        // Connection button
        binding.btnConnect.setOnClickListener {
            val ip = binding.etIp.text.toString().trim()
            if (wsManager.connectionState.value == ConnectionState.CONNECTED) {
                wsManager.disconnect()
            } else if (ip.isNotEmpty()) {
                prefs.edit().putString("last_ip", ip).apply()
                wsManager.connect(ip)
            }
        }

        // Rehearsal test buttons
        binding.btnTestVibrate.setOnClickListener   { commandHandler.testVibrate()   }
        binding.btnTestSound.setOnClickListener     { commandHandler.testSound()     }
        binding.btnTestRedScreen.setOnClickListener { commandHandler.testRedScreen() }

        // Microphone toggle
        binding.btnMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
                toggleMic()
            } else {
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Camera / photo
        binding.btnPhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                takePhoto()
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        // Video stream toggle
        binding.btnVideo.setOnClickListener {
            if (videoStreamManager.isStreaming()) deactivateStream() else activateStream()
        }

        // Dismiss transformed photo on tap
        binding.ivPhoto.setOnClickListener {
            binding.ivPhoto.visibility = View.GONE
        }

        // Observe connection state
        lifecycleScope.launch {
            wsManager.connectionState.collectLatest { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        binding.tvDot.setTextColor(Color.parseColor("#00FF00"))
                        binding.tvStatus.text = getString(R.string.connected)
                        binding.btnConnect.text = getString(R.string.btn_disconnect)
                        binding.btnMic.isEnabled = true
                        binding.btnPhoto.isEnabled = true
                        binding.btnVideo.isEnabled = true
                        discoveryJob?.cancel()  // stop looking once connected
                    }
                    ConnectionState.CONNECTING -> {
                        binding.tvDot.setTextColor(Color.parseColor("#FFAA00"))
                        binding.tvStatus.text = getString(R.string.connecting)
                        binding.btnConnect.text = getString(R.string.btn_connect)
                    }
                    ConnectionState.DISCONNECTED -> {
                        binding.tvDot.setTextColor(Color.parseColor("#FF4444"))
                        binding.tvStatus.text = getString(R.string.disconnected)
                        binding.btnConnect.text = getString(R.string.btn_connect)
                        binding.btnMic.isEnabled = false
                        binding.btnPhoto.isEnabled = false
                        binding.btnVideo.isEnabled = false
                        if (videoStreamManager.isStreaming()) deactivateStream()
                        if (micActive) { micActive = false; speechManager.stop() }
                        startDiscovery()  // resume looking after a drop
                    }
                }
            }
        }
    }

    private fun startDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = lifecycleScope.launch {
            try {
                val beacon = UdpDiscovery.awaitBeacon()
                // Only auto-connect if not already connected
                if (wsManager.connectionState.value == ConnectionState.DISCONNECTED) {
                    binding.etIp.setText(beacon.ip)
                    getSharedPreferences("companion_prefs", MODE_PRIVATE)
                        .edit().putString("last_ip", beacon.ip).apply()
                    wsManager.connect(beacon.ip)
                }
            } catch (_: Exception) {
                // Cancelled or socket error — silently ignore
            }
        }
    }

    private fun toggleMic() {
        micActive = !micActive
        if (micActive) {
            speechManager.start()
            binding.btnMic.text = "■ MICRÓFONO"
            binding.btnMic.setTextColor(Color.parseColor("#00FF00"))
        } else {
            speechManager.stop()
            binding.tvTranscript.text = ""
            binding.btnMic.text = "MICRÓFONO"
            binding.btnMic.setTextColor(Color.parseColor("#666666"))
        }
    }

    private fun takePhoto() {
        binding.btnPhoto.isEnabled = false
        cameraCapture.takePicture(
            onResult = { bytes ->
                // Signal Qt that photo is coming, then send binary
                wsManager.sendText("""{"type":"photo_ready"}""")
                wsManager.sendBinary(bytes)
                awaitingPhoto = true
                binding.btnPhoto.isEnabled = true
                toast("Foto enviada…")
            },
            onError = { msg ->
                binding.btnPhoto.isEnabled = true
                toast("Error: $msg")
            }
        )
    }

    private fun activateStream() {
        videoStreamManager.startStreaming()
        wsManager.sendText("""{"type":"stream_start"}""")
        binding.btnVideo.text = "■ VIDEO"
        binding.btnVideo.setTextColor(Color.parseColor("#AA44FF"))
    }

    private fun deactivateStream() {
        videoStreamManager.stopStreaming()
        wsManager.sendText("""{"type":"stream_stop"}""")
        binding.btnVideo.text = "VIDEO"
        binding.btnVideo.setTextColor(Color.parseColor("#666666"))
    }

    private fun showTransformedPhoto(bytes: ByteArray) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        binding.ivPhoto.setImageBitmap(bitmap)
        binding.ivPhoto.visibility = View.VISIBLE
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        if (videoStreamManager.isStreaming()) videoStreamManager.stopStreaming()
        speechManager.stop()
        wsManager.disconnect()
    }
}
