package com.cuarzopolar.companion.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

object UdpDiscovery {

    private const val TAG = "UdpDiscovery"
    const val BEACON_PORT = 8766
    private const val BUFFER_SIZE = 512

    data class Beacon(val ip: String, val port: Int)

    // Listens for a Qt beacon and returns when one is found.
    // Responds correctly to coroutine cancellation: soTimeout=3000 lets
    // the loop wake up periodically so isActive checks can fire, and the
    // finally block closes the socket so the port is released immediately.
    suspend fun awaitBeacon(): Beacon = withContext(Dispatchers.IO) {
        val socket = DatagramSocket(BEACON_PORT)
        try {
            socket.broadcast = true
            socket.soTimeout = 3000  // wake up every 3 s to check isActive
            Log.d(TAG, "Listening for beacon on UDP $BEACON_PORT")
            val buf = ByteArray(BUFFER_SIZE)
            val packet = DatagramPacket(buf, buf.size)
            while (isActive) {
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue  // just re-check isActive
                }
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
                } catch (_: Exception) {
                    Log.w(TAG, "Malformed beacon: $msg")
                }
            }
            throw CancellationException()
        } finally {
            socket.close()  // always release the port, even on cancellation
            Log.d(TAG, "Socket closed")
        }
    }
}
