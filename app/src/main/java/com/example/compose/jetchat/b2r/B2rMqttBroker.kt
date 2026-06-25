package com.example.compose.jetchat.b2r

import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * b2r: a retained-message mailbox over public MQTT brokers (WebSocket). Used to
 * replicate the global feed: publish a snapshot to a topic, fetch the latest
 * retained snapshot from it. Best-effort — failures are swallowed so the local
 * feed always works regardless of connectivity.
 */
class B2rMqttBroker(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val brokerUrls: List<String> = DEFAULT_BROKERS,
) {

    suspend fun publish(topic: String, payload: String) {
        val session = openSession() ?: return
        try {
            session.webSocket.send(MqttWireFormat.publishRetained(topic, payload.encodeToByteArray()))
            delay(PUBLISH_FLUSH_MS)
        } finally {
            session.close()
        }
    }

    suspend fun fetch(topic: String): String? {
        val session = openSession() ?: return null
        return try {
            session.webSocket.send(MqttWireFormat.subscribe(packetId = 1, topicFilter = topic))
            withTimeoutOrNull(SUBSCRIBE_WINDOW_MS) {
                for (frame in session.incoming) {
                    val publish = MqttWireFormat.parsePublish(frame) ?: continue
                    if (publish.topic == topic) return@withTimeoutOrNull publish.payload.decodeToString()
                }
                null
            }
        } finally {
            session.close()
        }
    }

    private suspend fun openSession(): Session? {
        for (url in brokerUrls) {
            val session = tryConnect(url)
            if (session != null) return session
        }
        return null
    }

    private suspend fun tryConnect(url: String): Session? {
        val incoming = Channel<ByteString>(Channel.UNLIMITED)
        val connack = CompletableDeferred<Boolean>()
        val request = Request.Builder().url(url).addHeader("Sec-WebSocket-Protocol", "mqtt").build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(MqttWireFormat.connect("b2r-" + Random.nextLong().toString(HEX_RADIX)))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val type = bytes.toByteArray().firstOrNull()?.toInt()?.and(TYPE_MASK)
                if (type == CONNACK_TYPE) connack.complete(true) else incoming.trySend(bytes)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!connack.isCompleted) connack.complete(false)
                incoming.close()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                incoming.close()
            }
        }

        val webSocket = okHttpClient.newWebSocket(request, listener)
        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connack.await() } ?: false
        return if (connected) {
            Session(webSocket, incoming)
        } else {
            webSocket.cancel()
            incoming.close()
            null
        }
    }

    private class Session(val webSocket: WebSocket, val incoming: Channel<ByteString>) {
        fun close() {
            runCatching { webSocket.send(MqttWireFormat.disconnect()) }
            runCatching { webSocket.close(NORMAL_CLOSURE, null) }
            incoming.close()
        }

        private companion object {
            const val NORMAL_CLOSURE = 1000
        }
    }

    companion object {
        private const val CONNACK_TYPE = 0x20
        private const val TYPE_MASK = 0xF0
        private const val HEX_RADIX = 16
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val SUBSCRIBE_WINDOW_MS = 2_500L
        private const val PUBLISH_FLUSH_MS = 400L

        val DEFAULT_BROKERS = listOf(
            "wss://broker.emqx.io:8084/mqtt",
            "wss://broker.hivemq.com:8884/mqtt",
            "wss://test.mosquitto.org:8081/mqtt",
        )
    }
}
