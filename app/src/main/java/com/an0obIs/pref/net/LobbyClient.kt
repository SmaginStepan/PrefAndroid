package com.an0obIs.pref.net

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class ConnState { Disconnected, Connecting, Connected }

/**
 * Thin WebSocket wrapper around the pref-server protocol.
 * Incoming messages are published on [messages]; connection state on [state].
 */
class LobbyClient(private val url: String = DEFAULT_URL) {

    companion object {
        const val DEFAULT_URL = "wss://preferansmaster.com/ws"
        private const val TAG = "PrefNet"
    }

    private val http = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null

    private val _state = MutableStateFlow(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state

    private val _messages = MutableSharedFlow<ServerMsg>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<ServerMsg> = _messages

    fun connect(playerId: String, name: String) {
        if (_state.value != ConnState.Disconnected) return
        _state.value = ConnState.Connecting
        val request = Request.Builder().url(url).build()
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = ConnState.Connected
                send(ClientMsg.Hello(playerId = playerId, name = name))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = protocolJson.decodeFromString(ServerMsg.serializer(), text)
                    _messages.tryEmit(msg)
                } catch (e: Exception) {
                    Log.w(TAG, "unparseable server message: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "ws failure", t)
                _state.value = ConnState.Disconnected
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = ConnState.Disconnected
            }
        })
    }

    fun send(msg: ClientMsg): Boolean {
        val socket = ws ?: return false
        return socket.send(protocolJson.encodeToString(ClientMsg.serializer(), msg))
    }

    fun disconnect() {
        ws?.close(1000, null)
        ws = null
        _state.value = ConnState.Disconnected
    }
}
