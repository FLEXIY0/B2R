package net.primal.android.b2r.p2p

import io.github.aakira.napier.Napier
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import net.primal.data.repository.b2r.p2p.DiscoveryBroker
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * b2r fork (global feed): a [DiscoveryBroker] backed by public MQTT brokers over
 * WebSocket, using one retained message as the global mailbox — the same
 * approach as the owner's `todo` app.
 *
 * The whole network shares a single topic ([GLOBAL_TOPIC]); the retained message
 * there holds a snapshot of the global feed, so any install picks it up whenever
 * it connects, even if others are offline. The MQTT framing is hand-rolled (see
 * [MqttWireFormat]) to avoid a dependency.
 *
 * NOTE: connection/replication logic compiles but is not yet validated against a
 * live broker on a device — treat as experimental.
 */
class OkHttpMqttDiscoveryBroker(
    private val okHttpClient: OkHttpClient,
    private val brokerUrls: List<String> = DEFAULT_BROKERS,
) : DiscoveryBroker {

    override suspend fun publishSnapshot(topic: String, payload: String) {
        val session = openSession() ?: return
        try {
            session.webSocket.send(MqttWireFormat.publishRetained(topic, payload.encodeToByteArray()))
            delay(PUBLISH_FLUSH_MS)
        } finally {
            session.close()
        }
    }

    override suspend fun fetchSnapshot(topic: String): String? {
        val session = openSession() ?: return null
        return try {
            session.webSocket.send(MqttWireFormat.subscribe(packetId = 1, topicFilter = topic))
            withTimeoutOrNull(SUBSCRIBE_WINDOW_MS) {
                for (frame in session.incoming) {
                    val publish = MqttWireFormat.parsePublish(frame) ?: continue
                    if (publish.topic == topic) {
                        return@withTimeoutOrNull publish.payload.decodeToString()
                    }
                }
                null
            }
        } finally {
            session.close()
        }
    }

    override fun close() = Unit

    private suspend fun openSession(): MqttSession? {
        for (url in brokerUrls) {
            val session = tryConnect(url)
            if (session != null) return session
        }
        Napier.w { "b2r P2P: no MQTT broker reachable" }
        return null
    }

    private suspend fun tryConnect(url: String): MqttSession? {
        val incoming = Channel<ByteString>(Channel.UNLIMITED)
        val connack = CompletableDeferred<Boolean>()
        val request = Request.Builder()
            .url(url)
            .addHeader("Sec-WebSocket-Protocol", "mqtt")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(MqttWireFormat.connect(clientId = "b2r-" + Random.nextLong().toString(HEX_RADIX)))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val packetType = bytes.toByteArray().firstOrNull()?.toInt()?.and(TYPE_MASK)
                if (packetType == CONNACK_TYPE) {
                    connack.complete(true)
                } else {
                    incoming.trySend(bytes)
                }
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
            MqttSession(webSocket, incoming)
        } else {
            webSocket.cancel()
            incoming.close()
            null
        }
    }

    private class MqttSession(
        val webSocket: WebSocket,
        val incoming: Channel<ByteString>,
    ) {
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

        /** Public, anonymous MQTT-over-WebSocket brokers (same set as the `todo` app). */
        val DEFAULT_BROKERS = listOf(
            "wss://broker.emqx.io:8084/mqtt",
            "wss://broker.hivemq.com:8884/mqtt",
            "wss://test.mosquitto.org:8081/mqtt",
        )
    }
}
