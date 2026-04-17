package com.cuarzopolar.companion.network

import com.cuarzopolar.companion.commands.CommandHandler
import org.json.JSONObject

class MessageDispatcher(private val commandHandler: CommandHandler) {
    fun dispatch(json: String) {
        runCatching {
            val obj = JSONObject(json)
            when (obj.optString("type")) {
                "command" -> commandHandler.handle(
                    action   = obj.optString("action"),
                    targetId = obj.optString("targetId")
                )
            }
        }
    }
}
