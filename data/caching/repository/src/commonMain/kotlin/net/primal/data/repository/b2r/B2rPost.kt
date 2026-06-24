package net.primal.data.repository.b2r

/**
 * b2r fork (Sprint 2.1 / point 2): the UI-facing feed post.
 *
 * A small public model exposed by [B2rFeedRepository] so the app layer never
 * sees the Room entity ([net.primal.data.local.dao.b2r.B2rFeedEntry], which is
 * internal to the data layer). Tombstoned posts are filtered out before mapping.
 */
data class B2rPost(
    val eventId: String,
    val authorPubkey: String,
    val sequenceId: Long,
    val content: String,
    val createdAt: Long,
)
