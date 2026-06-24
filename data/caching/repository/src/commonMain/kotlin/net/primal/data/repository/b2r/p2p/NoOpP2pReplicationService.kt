package net.primal.data.repository.b2r.p2p

import io.github.aakira.napier.Napier

/**
 * b2r fork: placeholder replication service used when no transport is wired.
 * Satisfies [P2pReplicationService] so the repository layer runs against the
 * local log only; it performs no networking.
 */
class NoOpP2pReplicationService : P2pReplicationService {

    override suspend fun pullFeed() {
        Napier.d { "b2r P2P: pullFeed stub; transport not wired, serving local cache only." }
    }

    override suspend fun publishFeed() {
        Napier.d { "b2r P2P: publishFeed stub; transport not wired." }
    }
}
