package com.cuarzopolar.companion

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cuarzopolar.companion.commands.CommandHandler
import com.cuarzopolar.companion.databinding.ActivityMainBinding
import com.cuarzopolar.companion.network.ConnectionState
import com.cuarzopolar.companion.network.MessageDispatcher
import com.cuarzopolar.companion.network.WebSocketManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val wsManager = WebSocketManager()
    private lateinit var commandHandler: CommandHandler
    private lateinit var dispatcher: MessageDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        commandHandler = CommandHandler(applicationContext)
        dispatcher     = MessageDispatcher(commandHandler)

        // Restore saved IP
        val prefs = getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
        binding.etIp.setText(prefs.getString("last_ip", ""))

        // Route incoming messages
        wsManager.onTextMessage = { dispatcher.dispatch(it) }

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

        // Observe connection state
        lifecycleScope.launch {
            wsManager.connectionState.collectLatest { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        binding.tvDot.setTextColor(Color.parseColor("#00FF00"))
                        binding.tvStatus.text = getString(R.string.connected)
                        binding.btnConnect.text = getString(R.string.btn_disconnect)
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
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsManager.disconnect()
    }
}
