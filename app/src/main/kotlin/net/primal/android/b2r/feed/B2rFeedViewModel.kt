package net.primal.android.b2r.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import net.primal.android.b2r.identity.B2rIdentityStore
import net.primal.data.repository.b2r.B2rFeedRepository
import net.primal.data.repository.b2r.B2rPost

/**
 * b2r fork: drives the single global shared feed.
 *
 * Renders the local Room log via [observeFeed], pulls the latest global snapshot
 * from peers on open and on [refresh], and appends posts via [publishPost] under
 * the active account's key. Everyone who installs the app shares this one feed.
 */
@HiltViewModel
class B2rFeedViewModel @Inject constructor(
    private val b2rFeedRepository: B2rFeedRepository,
    private val identityStore: B2rIdentityStore,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private fun setState(reducer: UiState.() -> UiState) = _state.getAndUpdate { it.reducer() }

    init {
        setState { copy(myPubkey = identityStore.pubKey) }
        observeLocalFeed()
        refresh()
    }

    private fun observeLocalFeed() =
        viewModelScope.launch {
            b2rFeedRepository.observeFeed().collect { posts ->
                setState { copy(posts = posts, loading = false) }
            }
        }

    /** Pull the latest global feed from peers. */
    fun refresh() =
        viewModelScope.launch {
            setState { copy(syncing = true) }
            runCatching { b2rFeedRepository.refresh() }
            setState { copy(syncing = false) }
        }

    /** Append a post to the global feed under the active account's key. */
    fun publishPost(content: String) =
        viewModelScope.launch {
            val author = identityStore.pubKey
            if (author.isBlank() || content.isBlank()) return@launch
            setState { copy(publishing = true) }
            runCatching { b2rFeedRepository.createPost(authorPubkey = author, content = content) }
            setState { copy(publishing = false) }
        }

    data class UiState(
        val myPubkey: String = "",
        val posts: List<B2rPost> = emptyList(),
        val loading: Boolean = true,
        val syncing: Boolean = false,
        val publishing: Boolean = false,
    )
}
