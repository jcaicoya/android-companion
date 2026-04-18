package com.cuarzopolar.companion.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket

object UdpDiscovery {

    private const val TAG = "UdpDiscovery"
    const val BEACON_PORT = 8766
    private const val BUFFER_SIZE = 512

    data class Beacon(val ip: String, val port: Int)

    // Blocks until a beacon is received or the socket is closed.
    // Call from a coroutine; cancel the coroutine to stop listening.
    suspend fun awaitBeacon(): Beacon = withContext(Dispatchers.IO) {
        DatagramSocket(BEACON_PORT).use { socket ->
            socket.broadcast = true
            socket.soTimeout = 0  // wait indefinitely
            Log.d(TAG, "Listening for beacon on UDP $BEACON_PORT")
            val buf = ByteArray(BUFFER_SIZE)
            val packet = DatagramPacket(buf, buf.size)
            while (true) {
                socket.receive(packet)
                val msg = String(packet.data, 0, packet.length)
                Log.d(TAG, "Received: $msg")
                try {
                    val obj = JSONObject(msg)
                    if (obj.optString("type") == "beacon") {
                        val ip   = obj.getString("ip")
                        val port = obj.getInt("port")
                        Log.d(TAG, "Beacon from $ip:$port")
                        return@withContext Beacon(ip, port)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Malformed beacon: $msg")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    }
}
