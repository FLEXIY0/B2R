package net.primal.data.repository.b2r.p2p

/**
 * b2r fork: the "discovery broker" leg of replication — a topic-addressed mailbox
 * over a public MQTT broker (retained messages).
 *
 * Everything in b2r flows through one shared broker; what makes a stream public
 * or private is the topic and whether its payload is encrypted:
 *  - the global feed uses a well-known public topic with plaintext snapshots;
 *  - a 1:1 chat uses an opaque per-conversation topic with end-to-end encrypted
 *    payloads, so other peers on the same broker only ever see ciphertext they
 *    cannot read ("limited by visibility").
 *
 * Kept as an interface so the data layer stays platform-agnostic; the Android
 * MQTT-over-WebSocket implementation lives in the app module and is injected in.
 */
interface DiscoveryBroker {

    /** Publish [payload] as the retained mailbox message on [topic]. */
    suspend fun publishSnapshot(topic: String, payload: String)

    /** Fetch the latest retained message on [topic], or null if none is available yet. */
    suspend fun fetchSnapshot(topic: String): String?

    /** Release broker connections. */
    fun close()
}
