package net.primal.data.local.dao.b2r

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * b2r fork (Sprint 1.4): access to the local feed log ([B2rFeedEntry]).
 *
 * This DAO is the local source of truth the repositories read from (Step 2.1):
 * `fetchFeed()` observes these rows while the P2P layer fills them in the
 * background. Writes use REPLACE; the LWW guard (only overwrite when the
 * incoming `modifiedAt` is newer) is applied by the merge layer before calling
 * [upsert], mirroring the owner's `todo` sync semantics.
 */
@Dao
interface B2rFeedEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: B2rFeedEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<B2rFeedEntry>)

    @Query("SELECT * FROM B2rFeedEntry WHERE deleted = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun observeFeed(limit: Int): Flow<List<B2rFeedEntry>>

    @Query(
        "SELECT * FROM B2rFeedEntry WHERE authorPubkey = :authorPubkey AND deleted = 0 " +
            "ORDER BY sequenceId DESC",
    )
    fun observeAuthorFeed(authorPubkey: String): Flow<List<B2rFeedEntry>>

    /** Highest known log index for an author — the watermark the P2P layer asks brokers about. */
    @Query("SELECT MAX(sequenceId) FROM B2rFeedEntry WHERE authorPubkey = :authorPubkey")
    suspend fun latestSequenceId(authorPubkey: String): Long?

    @Query("SELECT * FROM B2rFeedEntry WHERE eventId = :eventId")
    suspend fun findById(eventId: String): B2rFeedEntry?

    /** The full ordered log for an author — used to build a snapshot for peers. */
    @Query("SELECT * FROM B2rFeedEntry WHERE authorPubkey = :authorPubkey ORDER BY sequenceId ASC")
    suspend fun getAuthorLog(authorPubkey: String): List<B2rFeedEntry>

    /** Tombstone a post (LWW): records the deletion time so it cannot be resurrected. */
    @Query("UPDATE B2rFeedEntry SET deleted = 1, modifiedAt = :modifiedAt WHERE eventId = :eventId")
    suspend fun tombstone(eventId: String, modifiedAt: Long)
}
