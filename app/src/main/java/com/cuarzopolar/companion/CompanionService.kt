package com.cuarzopolar.companion

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cuarzopolar.companion.capture.CameraCapture
import com.cuarzopolar.companion.capture.SpeechManager
import com.cuarzopolar.companion.capture.VideoStreamManager
import com.cuarzopolar.companion.commands.CommandHandler
import com.cuarzopolar.companion.network.ConnectionState
import com.cuarzopolar.companion.network.MessageDispatcher
import com.cuarzopolar.companion.network.UdpDiscovery
import com.cuarzopolar.companion.network.WebSocketManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "CompanionService"
private const val CHANNEL_ID = "companion_channel"
private const val NOTIF_ID = 1

class CompanionService : LifecycleService() {

    inner class LocalBinder : Binder() {
        fun getService(): CompanionService = this@CompanionService
    }

    private val binder = LocalBinder()

    val wsManager = WebSocketManager()
    val connectionState: StateFlow<ConnectionState> get() = wsManager.connectionState

    lateinit var commandHandler: CommandHandler
    private lateinit var dispatcher: MessageDispatcher
    private lateinit var speechManager: SpeechManager
    lateinit var cameraCapture: CameraCapture
    private lateinit var videoStreamManager: VideoStreamManager

    private var photoCallback: ((ByteArray) -> Unit)? = null
    private var discoveryJob: Job? = null
    private var microphoneActive = false
    private var shuttingDownFromTaskRemoval = false

    fun setPhotoCallback(cb: (ByteArray) -> Unit) { photoCallback = cb }
    fun clearPhotoCallback() { photoCallback = null }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        updateForegroundService("SIN ENLACE")

        speechManager = SpeechManager(this) { transcript ->
            wsManager.sendText("""{"type":"transcript","text":"${transcript.replace("\"", "\\\"")}"}""")
        }

        videoStreamManager = VideoStreamManager { jpegBytes ->
            if (wsManager.connectionState.value == ConnectionState.CONNECTED) {
                wsManager.sendBinary(jpegBytes)
            }
        }

        cameraCapture = CameraCapture(this, this)

        commandHandler = CommandHandler(applicationContext)
        commandHandler.setMicCallbacks(
            start = {
                microphoneActive = true
                updateForegroundService(connectionStatusText())
                speechManager.start()
            },
            stop  = {
                speechManager.stop()
                microphoneActive = false
                updateForegroundService(connectionStatusText())
            }
        )
        commandHandler.setPhotoCallback {
            cameraCapture.takePicture(
                onResult = { bytes ->
                    wsManager.sendText("""{"type":"photo_ready"}""")
                    wsManager.sendBinary(bytes)
                },
                onError = { msg -> Log.e(TAG, "takePhoto failed: $msg") }
            )
        }

        dispatcher = MessageDispatcher(commandHandler)

        wsManager.onBinaryMessage = { bytes ->
            // Incoming binaries from Qt are always transformed photos
            photoCallback?.invoke(bytes)
        }

        wsManager.onTextMessage = { msg ->
            try {
                val obj = org.json.JSONObject(msg)
                if (obj.optString("type") == "command") {
                    when (obj.optString("action")) {
                        "startStream" -> {
                            videoStreamManager.startStreaming()
                            wsManager.sendText("""{"type":"stream_start"}""")
                        }
                        "stopStream" -> {
                            videoStreamManager.stopStreaming()
                            wsManager.sendText("""{"type":"stream_stop"}""")
                        }
                        else -> dispatcher.dispatch(msg)
                    }
                } else {
                    dispatcher.dispatch(msg)
                }
            } catch (_: Exception) {
                dispatcher.dispatch(msg)
            }
        }

        // Bind camera if permission already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            cameraCapture.bindCamera(videoStreamManager.imageAnalysis) {
                Log.d(TAG, "Camera bound")
            }
        }

        // Restore saved IP and auto-connect
        val prefs = getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("last_ip", null)
        if (!savedIp.isNullOrEmpty()) {
            wsManager.connect(savedIp)
        } else {
            startDiscovery()
        }

        // Update notification and re-attempt discovery on disconnect
        lifecycleScope.launch {
            wsManager.connectionState.collect { state ->
                val text = connectionStatusText(state)
                if (state == ConnectionState.DISCONNECTED) {
                    resetLiveEffectsOnDisconnect()
                }
                updateNotification(text)
                if (state == ConnectionState.DISCONNECTED && !shuttingDownFromTaskRemoval) startDiscovery()
                if (state == ConnectionState.CONNECTED)    stopDiscovery()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
        wsManager.disconnect()
        speechManager.stop()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdownFromUserExit()
        super.onTaskRemoved(rootIntent)
    }

    fun shutdownFromUserExit() {
        shuttingDownFromTaskRemoval = true
        getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
            .edit().remove("last_ip").apply()
        stopDiscovery()
        resetLiveEffectsOnDisconnect()
        wsManager.disconnect()
        stopSelf()
    }

    fun connect(ip: String) {
        getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
            .edit().putString("last_ip", ip).apply()
        wsManager.connect(ip)
    }

    fun disconnect() {
        getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
            .edit().remove("last_ip").apply()
        wsManager.disconnect()
    }

    fun bindCameraIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            cameraCapture.bindCamera(videoStreamManager.imageAnalysis) {
                Log.d(TAG, "Camera bound (deferred)")
            }
        }
    }

    fun isDeviceAdmin(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val comp = ComponentName(this, CompanionDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(comp)
    }

    private fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = lifecycleScope.launch {
            try {
                val beacon = UdpDiscovery.awaitBeacon()
                if (wsManager.connectionState.value == ConnectionState.DISCONNECTED) {
                    connect(beacon.ip)
                }
            } catch (_: Exception) { /* cancelled or socket error */ }
        }
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    private fun resetLiveEffectsOnDisconnect() {
        Log.d(TAG, "Connection lost; returning companion to normal mode")
        speechManager.stop()
        microphoneActive = false
        videoStreamManager.stopStreaming()
        commandHandler.handle("hideRedScreen", "")
        updateForegroundService(connectionStatusText(ConnectionState.DISCONNECTED))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "CuarzoPolar Companion",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            description = "Mantiene la conexión con la consola del operador"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CuarzoPolar")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun connectionStatusText(state: ConnectionState = wsManager.connectionState.value): String =
        when (state) {
            ConnectionState.CONNECTED    -> "ENLACE ACTIVO"
            ConnectionState.CONNECTING   -> "CONECTANDO\u2026"
            ConnectionState.DISCONNECTED -> "SIN ENLACE"
        }

    private fun updateForegroundService(statusText: String) {
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                if (microphoneActive) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(statusText), types)
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(statusText))
    }
}
