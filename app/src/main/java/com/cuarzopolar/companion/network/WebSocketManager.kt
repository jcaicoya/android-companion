package com.cuarzopolar.companion.network

import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

class WebSocketManager {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _state

    var onTextMessage: ((String) -> Unit)? = null
    var onBinaryMessage: ((ByteArray) -> Unit)? = null

    private var reconnectJob: Job? = null
    private var reconnectDelay = 1000L
    private var shouldReconnect = false

    fun connect(ip: String, port: Int = 8765) {
        shouldReconnect = true
        reconnectDelay = 1000L
        doConnect(ip, port)
    }

    private fun doConnect(ip: String, port: Int) {
        _state.value = ConnectionState.CONNECTING
        val request = Request.Builder().url("ws://$ip:$port").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _state.value = ConnectionState.CONNECTED
                reconnectDelay = 1000L
                val deviceName = Build.MODEL
                ws.send("""{"type":"status","deviceName":"$deviceName"}""")
            }
            override fun onMessage(ws: WebSocket, text: String) {
                val json = JSONObject(text)
                if (json.optString("type") == "ping") {
                    ws.send("""{"type":"pong"}""")
                } else {
                    onTextMessage?.invoke(text)
                }
            }
            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                onBinaryMessage?.invoke(bytes.toByteArray())
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _state.value = ConnectionState.DISCONNECTED
                scheduleReconnect(ip, port)
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _state.value = ConnectionState.DISCONNECTED
                scheduleReconnect(ip, port)
            }
        })
    }

    private fun scheduleReconnect(ip: String, port: Int) {
        if (!shouldReconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelay)
            reconnectDelay = minOf(reconnectDelay * 2, 30_000L)
            doConnect(ip, port)
        }
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    fun sendText(json: String) { webSocket?.send(json) }
    fun sendBinary(data: ByteArray) { webSocket?.send(okio.ByteString.of(*data)) }
}
