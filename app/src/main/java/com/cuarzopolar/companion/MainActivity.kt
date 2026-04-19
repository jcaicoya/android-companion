package com.cuarzopolar.companion

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cuarzopolar.companion.databinding.ActivityMainBinding
import com.cuarzopolar.companion.network.ConnectionState
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var service: CompanionService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as CompanionService.LocalBinder).getService()
            serviceBound = true
            observeConnectionState()
            service?.setPhotoCallback { bytes ->
                runOnUiThread { showTransformedPhoto(bytes) }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            service = null
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.CAMERA] == true) {
            service?.bindCameraIfNeeded()
        }
    }

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled silently — user may have declined */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start service (keeps running even when activity is gone) and bind
        val svcIntent = Intent(this, CompanionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent)
        } else {
            startService(svcIntent)
        }
        bindService(svcIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Tap dot or status label → open connect sheet when disconnected
        binding.tvDot.setOnClickListener    { onStatusTapped() }
        binding.tvStatus.setOnClickListener { onStatusTapped() }

        // Tap photo overlay to dismiss
        binding.ivPhoto.setOnClickListener { binding.ivPhoto.visibility = View.GONE }

        requestInitialPermissions()
        promptDeviceAdminIfNeeded()
    }

    private fun observeConnectionState() {
        val svc = service ?: return
        lifecycleScope.launch {
            svc.connectionState.collectLatest { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        binding.tvDot.setTextColor(Color.parseColor("#00FF00"))
                        binding.tvStatus.text = getString(R.string.connected)
                        binding.tvStatus.setTextColor(Color.parseColor("#00FF00"))
                    }
                    ConnectionState.CONNECTING -> {
                        binding.tvDot.setTextColor(Color.parseColor("#FFAA00"))
                        binding.tvStatus.text = getString(R.string.connecting)
                        binding.tvStatus.setTextColor(Color.parseColor("#FFAA00"))
                    }
                    ConnectionState.DISCONNECTED -> {
                        binding.tvDot.setTextColor(Color.parseColor("#FF4444"))
                        binding.tvStatus.text = getString(R.string.disconnected)
                        binding.tvStatus.setTextColor(Color.parseColor("#666666"))
                    }
                }
            }
        }
    }

    private fun onStatusTapped() {
        val svc = service ?: return
        if (svc.connectionState.value != ConnectionState.DISCONNECTED) return
        showConnectBottomSheet()
    }

    private fun showConnectBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_connect, null)
        sheet.setContentView(sheetView)

        val etIp = sheetView.findViewById<EditText>(R.id.etIp)
        val prefs = getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
        etIp.setText(prefs.getString("last_ip", ""))

        sheetView.findViewById<View>(R.id.btnConnect).setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                service?.connect(ip)
                sheet.dismiss()
            }
        }

        sheet.show()
    }

    private fun showTransformedPhoto(bytes: ByteArray) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        binding.ivPhoto.setImageBitmap(bitmap)
        binding.ivPhoto.visibility = View.VISIBLE
    }

    private fun requestInitialPermissions() {
        val needed = mutableListOf<String>()
        fun needs(p: String) =
            ContextCompat.checkSelfPermission(this, p) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needs(Manifest.permission.CAMERA))       needed.add(Manifest.permission.CAMERA)
        if (needs(Manifest.permission.RECORD_AUDIO)) needed.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            needs(Manifest.permission.POST_NOTIFICATIONS)) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
    }

    private fun promptDeviceAdminIfNeeded() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val comp = ComponentName(this, CompanionDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(comp)) {
            val prefs = getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("admin_prompted", false)) {
                prefs.edit().putBoolean("admin_prompted", true).apply()
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Permite bloquear el teléfono durante la escena de ataque.")
                }
                deviceAdminLauncher.launch(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            service?.shutdownFromUserExit()
        }
        if (serviceBound) {
            service?.clearPhotoCallback()
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
