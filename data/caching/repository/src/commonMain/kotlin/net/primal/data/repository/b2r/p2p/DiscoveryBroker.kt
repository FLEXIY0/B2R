package net.primal.data.repository.b2r.p2p

/**
 * b2r fork (point 1, global feed): the "discovery broker" leg of replication.
 *
 * b2r is one global shared space: everyone who installs the app reads and writes
 * the same feed. The broker exposes a single global mailbox — concretely a
 * retained message on a public MQTT topic — that holds a snapshot of the feed.
 * Any peer publishes the merged snapshot it knows; any peer can fetch the latest
 * one, even while others are offline.
 *
 * Kept as an interface so the data layer stays platform-agnostic; the Android
 * MQTT-over-WebSocket implementation lives in the app module and is injected in.
 */
interface DiscoveryBroker {

    /** Publish a snapshot of the global feed as the retained mailbox message. */
    suspend fun publishGlobalSnapshot(payload: String)

    /** Fetch the latest retained global-feed snapshot, or null if none is available yet. */
    suspend fun fetchGlobalSnapshot(): String?

    /** Release broker connections. */
    fun close()
}
