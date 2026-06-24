package net.primal.android.b2r.p2p

import io.github.aakira.napier.Napier
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import net.primal.core.utils.serialization.CommonJsonEncodeDefaults
import net.primal.data.repository.b2r.p2p.AuthorSnapshot
import net.primal.data.repository.b2r.p2p.DiscoveryBroker
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** Wire envelope stored on the broker (carries the author watermark alongside the payload). */
@Serializable
internal data class MqttSnapshotEnvelope(
    val authorPubkey: String,
    val latestSequenceId: Long,
    val payload: String,
)

/**
 * b2r fork (point 1, stage 2): a [DiscoveryBroker] backed by public MQTT brokers
 * over WebSocket, using retained messages as an offline-tolerant mailbox — the
 * same approach as the owner's `todo` app.
 *
 * Each author's latest snapshot lives at topic `b2r/feed/<authorPubkey>` as a
 * retained message, so a peer picks it up whenever it next connects, even if the
 * author is offline. The MQTT framing is hand-rolled (see [MqttWireFormat]) to
 * avoid adding a dependency.
 *
 * NOTE: connection/replication logic compiles but is not yet validated against a
 * live broker on a device — treat as experimental.
 */
class OkHttpMqttDiscoveryBroker(
    private val okHttpClient: OkHttpClient,
    private val brokerUrls: List<String> = DEFAULT_BROKERS,
) : DiscoveryBroker {

    override suspend fun publishSnapshot(snapshot: AuthorSnapshot) {
        val envelope = MqttSnapshotEnvelope(
            authorPubkey = snapshot.authorPubkey,
            latestSequenceId = snapshot.latestSequenceId,
            payload = snapshot.payload,
        )
        val json = CommonJsonEncodeDefaults.encodeToString(MqttSnapshotEnvelope.serializer(), envelope)
        val session = openSession() ?: return
        try {
            session.webSocket.send(
                MqttWireFormat.publishRetained(topicFor(snapshot.authorPubkey), json.encodeToByteArray()),
            )
            delay(PUBLISH_FLUSH_MS)
        } finally {
            session.close()
        }
    }

    override suspend fun fetchSnapshots(authorPubkeys: List<String>): List<AuthorSnapshot> {
        if (authorPubkeys.isEmpty()) return emptyList()
        val session = openSession() ?: return emptyList()
        val collected = LinkedHashMap<String, AuthorSnapshot>()
        try {
            var packetId = 1
            authorPubkeys.forEach { pubkey ->
                session.webSocket.send(MqttWireFormat.subscribe(packetId++, topicFor(pubkey)))
            }
            withTimeoutOrNull(SUBSCRIBE_WINDOW_MS) {
                for (frame in session.incoming) {
                    val publish = MqttWireFormat.parsePublish(frame) ?: continue
                    val envelope = runCatching {
                        CommonJsonEncodeDefaults.decodeFromString(
                            MqttSnapshotEnvelope.serializer(),
                            publish.payload.decodeToString(),
                        )
                    }.getOrNull() ?: continue
                    collected[envelope.authorPubkey] = AuthorSnapshot(
                        authorPubkey = envelope.authorPubkey,
                        latestSequenceId = envelope.latestSequenceId,
                        payload = envelope.payload,
                    )
                }
            }
        } finally {
            session.close()
        }
        return collected.values.toList()
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

        private fun topicFor(authorPubkey: String) = "b2r/feed/$authorPubkey"

        /** Public, anonymous MQTT-over-WebSocket brokers (same set as the `todo` app). */
        val DEFAULT_BROKERS = listOf(
            "wss://broker.emqx.io:8084/mqtt",
            "wss://broker.hivemq.com:8884/mqtt",
            "wss://test.mosquitto.org:8081/mqtt",
        )
    }
}
