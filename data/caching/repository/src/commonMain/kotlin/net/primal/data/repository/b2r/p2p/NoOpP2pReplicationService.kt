package net.primal.data.repository.b2r.p2p

import io.github.aakira.napier.Napier

/**
 * b2r fork (Sprint 2.3): placeholder replication service.
 *
 * Satisfies [P2pReplicationService] so the repository layer (2.1) compiles and
 * runs end-to-end while reading from the local log. It performs no networking;
 * the MQTT discovery + WebRTC transfer implementation replaces it later.
 */
class NoOpP2pReplicationService : P2pReplicationService {

    override suspend fun requestUpdates(authorWatermarks: Map<String, Long>) {
        Napier.d {
            "b2r P2P: requestUpdates stub for ${authorWatermarks.size} author(s); " +
                "transport not wired yet, serving local cache only."
        }
    }
}
