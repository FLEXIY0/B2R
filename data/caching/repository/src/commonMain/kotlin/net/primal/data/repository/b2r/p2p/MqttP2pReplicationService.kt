package net.primal.data.repository.b2r.p2p

import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import net.primal.core.utils.coroutines.DispatcherProvider
import net.primal.data.local.db.PrimalDatabase

/**
 * b2r fork (global feed): replication service backed by a [DiscoveryBroker]
 * (public MQTT mailbox).
 *
 * b2r is one shared space, so there is a single global snapshot rather than a
 * per-author one. [pullFeed] fetches that snapshot and merges any newer posts
 * into the local Room log using union + last-write-wins (an incoming copy only
 * overwrites a local one when its `modifiedAt` is newer; tombstones via
 * `deleted` are respected). [publishFeed] sends our full merged view back so
 * peers converge on the same feed.
 */
class MqttP2pReplicationService(
    private val database: PrimalDatabase,
    private val discoveryBroker: DiscoveryBroker,
    private val dispatcherProvider: DispatcherProvider,
) : P2pReplicationService {

    override suspend fun pullFeed() {
        withContext(dispatcherProvider.io()) {
            val payload = try {
                discoveryBroker.fetchSnapshot(GLOBAL_FEED_TOPIC)
            } catch (error: Exception) {
                Napier.w(error) { "b2r P2P: global snapshot fetch failed" }
                null
            } ?: return@withContext

            val incoming = try {
                P2pSnapshotCodec.decode(payload)
            } catch (error: Exception) {
                Napier.w(error) { "b2r P2P: invalid global snapshot payload" }
                return@withContext
            }

            val dao = database.b2rFeed()
            val toWrite = incoming.filter { entry ->
                // LWW guard: keep whichever copy is newer by modifiedAt.
                val existing = dao.findById(entry.eventId)
                existing == null || entry.modifiedAt >= existing.modifiedAt
            }
            if (toWrite.isNotEmpty()) {
                dao.upsertAll(toWrite)
                Napier.d { "b2r P2P: merged ${toWrite.size} post(s) from the global feed" }
            }
        }
    }

    override suspend fun publishFeed() {
        withContext(dispatcherProvider.io()) {
            val allPosts = database.b2rFeed().getAllPosts()
            if (allPosts.isEmpty()) return@withContext
            try {
                discoveryBroker.publishSnapshot(GLOBAL_FEED_TOPIC, P2pSnapshotCodec.encode(allPosts))
                Napier.d { "b2r P2P: published global feed snapshot (${allPosts.size} post(s))" }
            } catch (error: Exception) {
                Napier.w(error) { "b2r P2P: publish global feed failed" }
            }
        }
    }

    private companion object {
        /** Public, well-known topic for the global feed (plaintext snapshots). */
        const val GLOBAL_FEED_TOPIC = "b2r/global/v1"
    }
}
