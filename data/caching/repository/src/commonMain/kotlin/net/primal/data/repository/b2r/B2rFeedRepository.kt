package net.primal.data.repository.b2r

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import net.primal.core.utils.coroutines.DispatcherProvider
import net.primal.data.local.dao.b2r.B2rFeedEntry
import net.primal.data.local.db.PrimalDatabase
import net.primal.data.repository.b2r.p2p.P2pReplicationService

/**
 * b2r fork (Sprint 2.1): the feed repository, reimplemented for b2r.
 *
 * This is the heart-swap from the spec. Where Primal's feed repository fetched
 * pages over a WebSocket from the Primal cache server, this one:
 *  - serves the feed strictly from the local Room log ([observeFeed]), and
 *  - in parallel pings the P2P layer ([syncSubscriptions]) so it checks the
 *    discovery brokers for new per-author `sequenceId`s from followed authors and
 *    pulls them directly into the cache.
 *
 * The UI thinks it is talking to a cloud feed; in reality it reads local data
 * that is replicated phone-to-phone.
 */
class B2rFeedRepository(
    private val database: PrimalDatabase,
    private val dispatcherProvider: DispatcherProvider,
    private val p2pReplicationService: P2pReplicationService,
) {

    /** Observe the local feed log as UI posts. Pure local read — no network. */
    fun observeFeed(limit: Int = DEFAULT_FEED_LIMIT): Flow<List<B2rPost>> =
        database.b2rFeed()
            .observeFeed(limit = limit)
            .map { entries -> entries.map { it.toB2rPost() } }
            .flowOn(dispatcherProvider.io())

    /** Observe a single author's log as UI posts, ordered by their sequence index. */
    fun observeAuthorFeed(authorPubkey: String): Flow<List<B2rPost>> =
        database.b2rFeed()
            .observeAuthorFeed(authorPubkey = authorPubkey)
            .map { entries -> entries.map { it.toB2rPost() } }
            .flowOn(dispatcherProvider.io())

    /**
     * Append a new post to the local log and advertise it to peers.
     *
     * Assigns the next per-author [sequenceId] (append-only), writes it to the
     * Room log, then publishes the author's snapshot to the discovery broker so
     * peers can replicate it. The post is signed by the account secp256k1 key in
     * a later pass (the [B2rFeedEntry.signature] hook is left null for now).
     */
    suspend fun createPost(authorPubkey: String, content: String): B2rPost =
        withContext(dispatcherProvider.io()) {
            val dao = database.b2rFeed()
            val nextSequenceId = (dao.latestSequenceId(authorPubkey = authorPubkey) ?: NO_ENTRIES_WATERMARK) + 1
            val now = Clock.System.now().toEpochMilliseconds()
            val entry = B2rFeedEntry(
                // Deterministic per log position; production should hash the signed payload.
                eventId = "$authorPubkey:$nextSequenceId",
                authorPubkey = authorPubkey,
                sequenceId = nextSequenceId,
                content = content,
                createdAt = now,
                modifiedAt = now,
                deleted = false,
                signature = null,
            )
            dao.upsert(entry)
            p2pReplicationService.publishLocalSnapshot(authorPubkey = authorPubkey)
            entry.toB2rPost()
        }

    /**
     * Ask the P2P layer to reconcile the given authors against peers. Computes the
     * local watermark (highest known sequenceId per author) and hands it to the
     * replication service, which fills the local cache with anything newer.
     */
    suspend fun syncSubscriptions(authorPubkeys: List<String>) =
        withContext(dispatcherProvider.io()) {
            val watermarks = authorPubkeys.associateWith { pubkey ->
                database.b2rFeed().latestSequenceId(authorPubkey = pubkey) ?: NO_ENTRIES_WATERMARK
            }
            p2pReplicationService.requestUpdates(authorWatermarks = watermarks)
        }

    companion object {
        const val DEFAULT_FEED_LIMIT = 100

        /** Watermark for an author we have no posts from yet. */
        const val NO_ENTRIES_WATERMARK = -1L
    }
}

private fun B2rFeedEntry.toB2rPost() =
    B2rPost(
        eventId = eventId,
        authorPubkey = authorPubkey,
        sequenceId = sequenceId,
        content = content,
        createdAt = createdAt,
    )
