package net.primal.data.repository.b2r

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.primal.core.utils.coroutines.DispatcherProvider
import net.primal.data.local.dao.b2r.B2rFeedEntry
import net.primal.data.local.db.PrimalDatabase
import net.primal.data.repository.b2r.p2p.P2pReplicationService

/**
 * b2r fork: the feed repository for the single global shared space.
 *
 * This is the heart-swap from the spec. Where Primal's feed repository fetched
 * pages over a WebSocket from the Primal cache server, this one:
 *  - serves the feed strictly from the local Room log ([observeFeed]), and
 *  - syncs that log with everyone else over the P2P layer ([refresh] pulls the
 *    global snapshot; [createPost] appends locally and re-publishes it).
 *
 * Every install shares one feed: there are no rooms or follows. The UI thinks it
 * is talking to a cloud feed; it is actually reading local data replicated
 * phone-to-phone.
 */
class B2rFeedRepository(
    private val database: PrimalDatabase,
    private val dispatcherProvider: DispatcherProvider,
    private val p2pReplicationService: P2pReplicationService,
) {

    /** Observe the global feed as UI posts. Pure local read — no network. */
    fun observeFeed(limit: Int = DEFAULT_FEED_LIMIT): Flow<List<B2rPost>> =
        database.b2rFeed()
            .observeFeed(limit = limit)
            .map { entries -> entries.map { it.toB2rPost() } }
            .flowOn(dispatcherProvider.io())

    /**
     * Append a post to the local log and advertise the updated feed to peers.
     *
     * Assigns the next per-author [sequenceId] (append-only), writes the entry,
     * then republishes the whole feed snapshot so other installs replicate it.
     * The post is signed by the account secp256k1 key in a later pass (the
     * [B2rFeedEntry.signature] hook is left null for now).
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
            p2pReplicationService.publishFeed()
            entry.toB2rPost()
        }

    /** Pull the latest global feed from peers and merge it into the local log. */
    suspend fun refresh() = p2pReplicationService.pullFeed()

    companion object {
        const val DEFAULT_FEED_LIMIT = 200

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
