package com.cuarzopolar.companion

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
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
    private val alertHandler = Handler(Looper.getMainLooper())
    private var pulseAnimator: ObjectAnimator? = null
    private var baseScale = 1f

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as CompanionService.LocalBinder).getService()
            service = svc
            serviceBound = true
            observeConnectionState()
            svc.onCommandReceived = { runOnUiThread { showAlertState() } }
            svc.onShowRedScreen  = { runOnUiThread { showLaserGrid() } }
            svc.onHideRedScreen  = { runOnUiThread { hideLaserGrid() } }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service?.onCommandReceived = null
            service?.onShowRedScreen  = null
            service?.onHideRedScreen  = null
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

        val svcIntent = Intent(this, CompanionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent)
        } else {
            startService(svcIntent)
        }
        bindService(svcIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        binding.ivCuarzito.setOnClickListener { onCuarzitoTapped() }
        startPulseAnimation()
        binding.ivCuarzito.post { applyFillScale(binding.ivCuarzito, 0.85f) }

        requestInitialPermissions()
        promptDeviceAdminIfNeeded()
    }

    private fun observeConnectionState() {
        val svc = service ?: return
        lifecycleScope.launch {
            svc.connectionState.collectLatest { state ->
                alertHandler.removeCallbacksAndMessages(null)
                binding.ivCuarzito.setImageResource(
                    when (state) {
                        ConnectionState.CONNECTED    -> R.drawable.cuarzito_green
                        ConnectionState.CONNECTING   -> R.drawable.cuarzito_amber
                        ConnectionState.DISCONNECTED -> R.drawable.cuarzito_blue
                    }
                )
            }
        }
    }

    private fun showLaserGrid() {
        binding.laserGrid.visibility = View.VISIBLE
        binding.laserGrid.animateIn()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.ivCuarzito.setImageResource(R.drawable.cuarzito_red)
    }

    private fun hideLaserGrid() {
        binding.laserGrid.animateOut()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (service?.connectionState?.value == ConnectionState.CONNECTED) {
            binding.ivCuarzito.setImageResource(R.drawable.cuarzito_green)
        }
    }

    private fun showAlertState() {
        alertHandler.removeCallbacksAndMessages(null)
        binding.ivCuarzito.setImageResource(R.drawable.cuarzito_amber)
        alertHandler.postDelayed({
            if (service?.connectionState?.value == ConnectionState.CONNECTED) {
                binding.ivCuarzito.setImageResource(R.drawable.cuarzito_green)
            }
        }, 2000)
    }

    private fun applyFillScale(iv: android.widget.ImageView, targetHeightFraction: Float) {
        val vw = iv.width.toFloat()
        val vh = iv.height.toFloat()
        val d = iv.drawable ?: return
        val imgW = d.intrinsicWidth.toFloat()
        val imgH = d.intrinsicHeight.toFloat()
        if (imgW <= 0f || imgH <= 0f) return
        val fitScale = minOf(vw / imgW, vh / imgH)
        val renderedH = imgH * fitScale
        val scale = (vh * targetHeightFraction) / renderedH
        baseScale = scale
        iv.scaleX = scale
        iv.scaleY = scale
        iv.translationX = vw * 0.08f
        pulseAnimator?.cancel()
        startPulseAnimation()
    }

    private fun startPulseAnimation() {
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.ivCuarzito,
            PropertyValuesHolder.ofFloat("scaleX", baseScale, baseScale * 1.06f),
            PropertyValuesHolder.ofFloat("scaleY", baseScale, baseScale * 1.06f)
        ).apply {
            duration = 3000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun onCuarzitoTapped() {
        val svc = service ?: return
        if (svc.connectionState.value == ConnectionState.CONNECTED) return
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
        pulseAnimator?.cancel()
        alertHandler.removeCallbacksAndMessages(null)
        if (isFinishing) service?.shutdownFromUserExit()
        if (serviceBound) {
            service?.onCommandReceived = null
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
