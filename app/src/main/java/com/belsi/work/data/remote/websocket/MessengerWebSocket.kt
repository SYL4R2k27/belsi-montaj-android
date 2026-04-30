package com.belsi.work.data.remote.websocket

import android.util.Log
import com.belsi.work.BuildConfig
import com.belsi.work.data.local.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket events received from server
 */
sealed class WsEvent {
    data class NewMessage(
        val threadId: String,
        val messageJson: JsonObject
    ) : WsEvent()

    data class ThreadUpdated(val threadId: String) : WsEvent()

    data class Typing(
        val threadId: String,
        val userId: String,
        val userName: String
    ) : WsEvent()

    data class Read(
        val threadId: String,
        val userId: String
    ) : WsEvent()

    object Connected : WsEvent()
    object Disconnected : WsEvent()
}

enum class WsConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}

/**
 * Singleton WebSocket manager for the messenger.
 * - Auto-connects when token is available
 * - Auto-reconnects with exponential backoff
 * - Sends heartbeat (ping) every 30s
 * - Emits events via SharedFlow
 */
@Singleton
class MessengerWebSocket @Inject constructor(
    private val tokenManager: TokenManager,
    private val json: Json
) {
    companion object {
        private const val TAG = "MessengerWS"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private var shouldConnect = false

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // no timeout for WS
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    /**
     * Start the WebSocket connection. Call when user enters messenger.
     */
    fun connect() {
        if (shouldConnect && _connectionState.value == WsConnectionState.CONNECTED) return
        shouldConnect = true
        reconnectDelay = INITIAL_RECONNECT_DELAY_MS
        doConnect()
    }

    /**
     * Disconnect WebSocket. Call when user leaves messenger or logs out.
     */
    fun disconnect() {
        shouldConnect = false
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    /**
     * Send typing indicator for a thread.
     */
    fun sendTyping(threadId: String) {
        sendJson("""{"type":"typing","thread_id":"$threadId"}""")
    }

    /**
     * Send read receipt for a thread.
     */
    fun sendRead(threadId: String) {
        sendJson("""{"type":"read","thread_id":"$threadId"}""")
    }

    private fun doConnect() {
        if (!shouldConnect) return
        _connectionState.value = WsConnectionState.CONNECTING

        scope.launch {
            val token = tokenManager.getToken()
            if (token == null) {
                Log.w(TAG, "No token, cannot connect WS")
                _connectionState.value = WsConnectionState.DISCONNECTED
                return@launch
            }

            val baseUrl = BuildConfig.API_BASE_URL
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                .trimEnd('/')
            val wsUrl = "$baseUrl/ws/messenger?token=$token"

            Log.d(TAG, "Connecting to $wsUrl")

            val request = Request.Builder().url(wsUrl).build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Connected")
                    _connectionState.value = WsConnectionState.CONNECTED
                    reconnectDelay = INITIAL_RECONNECT_DELAY_MS
                    _events.tryEmit(WsEvent.Connected)
                    startHeartbeat()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Closing: $code $reason")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Closed: $code $reason")
                    onDisconnected()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code
                    Log.e(TAG, "Failure: ${t.message}, code=$code")
                    if (code == 403 || code == 401) {
                        // Auth failure — don't reconnect with same token, wait longer
                        Log.w(TAG, "Auth failure ($code), backing off to max delay")
                        reconnectDelay = MAX_RECONNECT_DELAY_MS
                    }
                    onDisconnected()
                }
            })
        }
    }

    private fun handleMessage(text: String) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: return

            when (type) {
                "new_message" -> {
                    val threadId = obj["thread_id"]?.jsonPrimitive?.content ?: return
                    val message = obj["message"]?.jsonObject ?: return
                    _events.tryEmit(WsEvent.NewMessage(threadId, message))
                }
                "thread_updated" -> {
                    val threadId = obj["thread_id"]?.jsonPrimitive?.content ?: return
                    _events.tryEmit(WsEvent.ThreadUpdated(threadId))
                }
                "typing" -> {
                    val threadId = obj["thread_id"]?.jsonPrimitive?.content ?: return
                    val userId = obj["user_id"]?.jsonPrimitive?.content ?: return
                    val userName = obj["user_name"]?.jsonPrimitive?.content ?: ""
                    _events.tryEmit(WsEvent.Typing(threadId, userId, userName))
                }
                "read" -> {
                    val threadId = obj["thread_id"]?.jsonPrimitive?.content ?: return
                    val userId = obj["user_id"]?.jsonPrimitive?.content ?: return
                    _events.tryEmit(WsEvent.Read(threadId, userId))
                }
                "pong" -> { /* heartbeat response, ignore */ }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    private fun onDisconnected() {
        heartbeatJob?.cancel()
        _connectionState.value = WsConnectionState.DISCONNECTED
        _events.tryEmit(WsEvent.Disconnected)

        if (shouldConnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.d(TAG, "Reconnecting in ${reconnectDelay}ms")
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            doConnect()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendJson("""{"type":"ping"}""")
            }
        }
    }

    private fun sendJson(text: String) {
        try {
            webSocket?.send(text)
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")
        }
    }
}
