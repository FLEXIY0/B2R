package net.primal.android.b2r.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import net.primal.data.repository.b2r.B2rFeedRepository
import net.primal.data.repository.b2r.B2rPost

/**
 * b2r fork (point 2): UI-layer delegation to the b2r data path.
 *
 * Where a Primal feed ViewModel pulls pages from the cache server, this one
 * binds to [B2rFeedRepository]: it renders the local Room log via [observeFeed]
 * and asks the P2P layer to reconcile subscribed authors via [syncWithPeers].
 * The view thinks it is showing a cloud feed; it is actually showing local data
 * replicated phone-to-phone. A Compose screen can bind to [state] directly.
 */
@HiltViewModel
class B2rFeedViewModel @Inject constructor(
    private val b2rFeedRepository: B2rFeedRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private fun setState(reducer: UiState.() -> UiState) = _state.getAndUpdate { it.reducer() }

    init {
        observeLocalFeed()
    }

    private fun observeLocalFeed() =
        viewModelScope.launch {
            b2rFeedRepository.observeFeed().collect { posts ->
                setState { copy(posts = posts, loading = false) }
            }
        }

    /** Ask the P2P layer to reconcile the given subscribed authors against peers. */
    fun syncWithPeers(authorPubkeys: List<String>) =
        viewModelScope.launch {
            setState { copy(syncing = true) }
            runCatching { b2rFeedRepository.syncSubscriptions(authorPubkeys) }
            setState { copy(syncing = false) }
        }

    data class UiState(
        val posts: List<B2rPost> = emptyList(),
        val loading: Boolean = true,
        val syncing: Boolean = false,
    )
}
