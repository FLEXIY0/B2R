package net.primal.data.repository.b2r.p2p

/**
 * b2r fork (Sprint 2.3 / point 1): a snapshot of one author's log as advertised
 * on a discovery broker's retained "mailbox".
 *
 * Mirrors the owner's `todo` model: a peer publishes an (optionally encrypted)
 * snapshot to a public broker as a retained message; other peers pick it up
 * whenever they come online, even if the author is offline.
 *
 * @param authorPubkey the b2r author public key the snapshot belongs to.
 * @param latestSequenceId the highest log index contained in [payload].
 * @param payload serialized [net.primal.data.local.dao.b2r.B2rFeedEntry] list
 * (JSON), as produced/consumed by [P2pSnapshotCodec].
 */
data class AuthorSnapshot(
    val authorPubkey: String,
    val latestSequenceId: Long,
    val payload: String,
)

/**
 * b2r fork (point 1): the "discovery broker" leg of replication.
 *
 * Concretely backed by public MQTT brokers over WebSocket (retained messages as
 * a mailbox), matching `todo`'s transport. Kept as an interface so the data
 * layer stays platform-agnostic — the Android implementation lives in the app
 * module and is injected in.
 */
interface DiscoveryBroker {

    /** Publish our snapshot for an author as a retained message on the broker. */
    suspend fun publishSnapshot(snapshot: AuthorSnapshot)

    /**
     * Fetch the latest retained snapshots advertised for the given authors.
     * Implementations should return quickly with whatever is currently available.
     */
    suspend fun fetchSnapshots(authorPubkeys: List<String>): List<AuthorSnapshot>

    /** Release broker connections. */
    fun close()
}
