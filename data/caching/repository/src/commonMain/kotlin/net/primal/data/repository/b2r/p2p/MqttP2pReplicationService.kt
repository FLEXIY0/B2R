package net.primal.data.repository.b2r.p2p

import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import net.primal.core.utils.coroutines.DispatcherProvider
import net.primal.data.local.db.PrimalDatabase

/**
 * b2r fork (point 1): real replication service backed by a [DiscoveryBroker]
 * (public MQTT mailbox).
 *
 * For each subscribed author it asks the broker for the latest retained snapshot;
 * if the snapshot advertises a higher `sequenceId` than what we hold locally, it
 * decodes the posts and merges the new ones into the Room log using
 * union + last-write-wins (an incoming copy only overwrites a local one when its
 * `modifiedAt` is newer, and tombstones are respected by [B2rFeedEntry.deleted]).
 *
 * Direct phone-to-phone transfer over WebRTC ([DirectReplicator]) is layered on
 * top later; the MQTT mailbox alone already gives asynchronous, offline-tolerant
 * replication, matching the owner's `todo` design.
 */
class MqttP2pReplicationService(
    private val database: PrimalDatabase,
    private val discoveryBroker: DiscoveryBroker,
    private val dispatcherProvider: DispatcherProvider,
) : P2pReplicationService {

    override suspend fun requestUpdates(authorWatermarks: Map<String, Long>) {
        if (authorWatermarks.isEmpty()) return

        withContext(dispatcherProvider.io()) {
            val snapshots = try {
                discoveryBroker.fetchSnapshots(authorWatermarks.keys.toList())
            } catch (error: Exception) {
                Napier.w(error) { "b2r P2P: discovery broker fetch failed" }
                emptyList()
            }

            val dao = database.b2rFeed()
            snapshots.forEach { snapshot ->
                val knownSequenceId = authorWatermarks[snapshot.authorPubkey] ?: NO_ENTRIES
                if (snapshot.latestSequenceId <= knownSequenceId) return@forEach

                val incoming = try {
                    P2pSnapshotCodec.decode(snapshot.payload)
                } catch (error: Exception) {
                    Napier.w(error) { "b2r P2P: invalid snapshot for ${snapshot.authorPubkey}" }
                    return@forEach
                }

                val toWrite = incoming
                    .filter { it.sequenceId > knownSequenceId }
                    .filter { entry ->
                        // LWW guard: keep whichever copy is newer by modifiedAt.
                        val existing = dao.findById(entry.eventId)
                        existing == null || entry.modifiedAt >= existing.modifiedAt
                    }

                if (toWrite.isNotEmpty()) {
                    dao.upsertAll(toWrite)
                    Napier.d { "b2r P2P: merged ${toWrite.size} post(s) for ${snapshot.authorPubkey}" }
                }
            }
        }
    }

    override suspend fun publishLocalSnapshot(authorPubkey: String) {
        withContext(dispatcherProvider.io()) {
            val entries = database.b2rFeed().getAuthorLog(authorPubkey)
            if (entries.isEmpty()) return@withContext

            val snapshot = AuthorSnapshot(
                authorPubkey = authorPubkey,
                latestSequenceId = entries.maxOf { it.sequenceId },
                payload = P2pSnapshotCodec.encode(entries),
            )
            try {
                discoveryBroker.publishSnapshot(snapshot)
                Napier.d { "b2r P2P: published snapshot for $authorPubkey (seq ${snapshot.latestSequenceId})" }
            } catch (error: Exception) {
                Napier.w(error) { "b2r P2P: publishSnapshot failed for $authorPubkey" }
            }
        }
    }

    private companion object {
        const val NO_ENTRIES = -1L
    }
}
