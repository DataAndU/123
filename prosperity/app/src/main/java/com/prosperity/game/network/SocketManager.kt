package com.prosperity.game.network

import com.google.gson.Gson
import com.prosperity.game.data.TokenStore
import com.prosperity.game.network.dto.BusinessBankruptedEvent
import com.prosperity.game.network.dto.ChatMessage
import com.prosperity.game.network.dto.EconomyState
import com.prosperity.game.network.dto.MarketOrder
import com.prosperity.game.network.dto.MarketSnapshot
import com.prosperity.game.network.dto.PresenceUpdate
import com.prosperity.game.network.dto.TickCompleteEvent
import com.prosperity.game.network.dto.TradeBroadcast
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Thin wrapper over socket.io-client for the shared, real-time parts of
 * Prosperity Online: the world clock's tick broadcasts, live trade prints,
 * chat, presence, and marketplace order updates. REST is still used for
 * everything else (see [ApiService]) — sockets only carry what genuinely
 * needs to be pushed rather than polled.
 */
class SocketManager(private val tokenStore: TokenStore) {

    private var socket: Socket? = null
    private val gson = Gson()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _economyUpdates = MutableSharedFlow<EconomyState>(extraBufferCapacity = 4)
    val economyUpdates: SharedFlow<EconomyState> = _economyUpdates.asSharedFlow()

    private val _marketUpdates = MutableSharedFlow<MarketSnapshot>(extraBufferCapacity = 4)
    val marketUpdates: SharedFlow<MarketSnapshot> = _marketUpdates.asSharedFlow()

    private val _tradeExecuted = MutableSharedFlow<TradeBroadcast>(extraBufferCapacity = 16)
    val tradeExecuted: SharedFlow<TradeBroadcast> = _tradeExecuted.asSharedFlow()

    private val _chatMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 32)
    val chatMessages: SharedFlow<ChatMessage> = _chatMessages.asSharedFlow()

    private val _presence = MutableSharedFlow<PresenceUpdate>(extraBufferCapacity = 4)
    val presence: SharedFlow<PresenceUpdate> = _presence.asSharedFlow()

    private val _businessBankrupted = MutableSharedFlow<BusinessBankruptedEvent>(extraBufferCapacity = 4)
    val businessBankrupted: SharedFlow<BusinessBankruptedEvent> = _businessBankrupted.asSharedFlow()

    private val _tickComplete = MutableSharedFlow<TickCompleteEvent>(extraBufferCapacity = 4)
    val tickComplete: SharedFlow<TickCompleteEvent> = _tickComplete.asSharedFlow()

    private val _orderUpdates = MutableSharedFlow<MarketOrder>(extraBufferCapacity = 16)
    val orderUpdates: SharedFlow<MarketOrder> = _orderUpdates.asSharedFlow()

    fun connect() {
        disconnect()
        val token = tokenStore.getToken() ?: return
        val baseUrl = tokenStore.getServerUrl().removeSuffix("/")
        try {
            val opts = IO.Options()
            opts.query = "token=$token"
            opts.reconnection = true
            opts.forceNew = true
            val s = IO.socket(baseUrl, opts)
            socket = s

            s.on(Socket.EVENT_CONNECT) { _connected.tryEmit(true) }
            s.on(Socket.EVENT_DISCONNECT) { _connected.tryEmit(false) }
            s.on("economy:update") { args -> emitJson(args, _economyUpdates, EconomyState::class.java) }
            s.on("market:update") { args -> emitJson(args, _marketUpdates, MarketSnapshot::class.java) }
            s.on("trade:executed") { args -> emitJson(args, _tradeExecuted, TradeBroadcast::class.java) }
            s.on("chat:message") { args -> emitJson(args, _chatMessages, ChatMessage::class.java) }
            s.on("presence:update") { args -> emitJson(args, _presence, PresenceUpdate::class.java) }
            s.on("business:bankrupted") { args -> emitJson(args, _businessBankrupted, BusinessBankruptedEvent::class.java) }
            s.on("tick:complete") { args -> emitJson(args, _tickComplete, TickCompleteEvent::class.java) }
            s.on("marketplace:order_placed") { args -> emitJson(args, _orderUpdates, MarketOrder::class.java) }
            s.on("marketplace:order_updated") { args -> emitJson(args, _orderUpdates, MarketOrder::class.java) }

            s.connect()
        } catch (e: Exception) {
            _connected.tryEmit(false)
        }
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
        _connected.tryEmit(false)
    }

    fun joinChannel(channel: String) {
        socket?.emit("chat:join", channel)
    }

    fun sendChat(channel: String, body: String) {
        val payload = JSONObject().put("channel", channel).put("body", body)
        socket?.emit("chat:send", payload)
    }

    private fun <T> emitJson(args: Array<Any>, flow: MutableSharedFlow<T>, clazz: Class<T>) {
        val raw = args.getOrNull(0) as? JSONObject ?: return
        try {
            flow.tryEmit(gson.fromJson(raw.toString(), clazz))
        } catch (_: Exception) {
            // Malformed/unexpected payload shape — drop it rather than crash the socket loop.
        }
    }
}
