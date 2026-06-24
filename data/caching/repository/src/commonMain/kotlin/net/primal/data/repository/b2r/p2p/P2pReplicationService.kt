package net.primal.data.repository.b2r.p2p

/**
 * b2r fork (Sprint 2.3): the seam between the data layer and b2r's peer-to-peer
 * replication engine.
 *
 * Repositories never talk to a server. Instead they read from the local Room log
 * and call this service, which (once the transport lands) asks the discovery
 * brokers whether subscribed authors have advanced their per-author `sequenceId`
 * and pulls any missing posts directly from peers into the local cache.
 *
 * The concrete implementation will combine, mirroring the owner's `todo` sync:
 *  - a discovery broker over public MQTT (retained "mailbox" messages), and
 *  - direct phone-to-phone transfer over WebRTC.
 *
 * For now only [NoOpP2pReplicationService] exists so the repository wiring can be
 * built and compiled ahead of the transport.
 */
interface P2pReplicationService {

    /**
     * Reconcile the local log against peers.
     *
     * @param authorWatermarks map of subscribed author public key -> highest
     * locally known [sequenceId] (or -1 if the author is new). The service asks
     * brokers for anything newer and replicates it into the local cache.
     */
    suspend fun requestUpdates(authorWatermarks: Map<String, Long>)

    /**
     * Advertise our local log for an author to peers by publishing a retained
     * snapshot to the discovery broker. Called after a local post is appended so
     * other peers can pick it up when they next reconcile.
     */
    suspend fun publishLocalSnapshot(authorPubkey: String)
}
