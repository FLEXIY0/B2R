package net.primal.data.repository.b2r.p2p

/**
 * b2r fork: the seam between the data layer and b2r's peer-to-peer replication.
 *
 * b2r is one global shared feed. Repositories read from the local Room log and
 * call this service to keep that log in sync with everyone else:
 *  - [pullFeed] fetches the latest global snapshot from the discovery broker and
 *    merges any newer posts into the local log;
 *  - [publishFeed] advertises our merged view of the feed back to the broker so
 *    other peers pick up our (and others') posts.
 *
 * The concrete implementation combines a public MQTT mailbox (retained messages)
 * with — later — direct WebRTC transfer. [NoOpP2pReplicationService] is used when
 * no transport is wired.
 */
interface P2pReplicationService {

    /** Pull the latest global feed snapshot from peers and merge it into the local log. */
    suspend fun pullFeed()

    /** Publish our current view of the global feed so peers can replicate it. */
    suspend fun publishFeed()
}
