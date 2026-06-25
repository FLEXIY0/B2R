package net.primal.android.b2r.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import net.primal.data.repository.b2r.chat.B2rChatMessageUi
import net.primal.data.repository.b2r.chat.B2rChatRepository

/**
 * b2r fork: drives a private 1:1 chat with a chosen peer.
 *
 * Call [open] with the local user's key and the peer's key (e.g. found via
 * search) to start observing the conversation and pull the latest snapshot.
 * Messages are end-to-end encrypted by the repository before they hit the broker.
 */
@HiltViewModel
class B2rChatViewModel @Inject constructor(
    private val chatRepository: B2rChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private fun setState(reducer: UiState.() -> UiState) = _state.getAndUpdate { it.reducer() }

    private var myPubkey: String? = null
    private var peerPubkey: String? = null

    /** Bind this view model to a conversation between [myPubkey] and [peerPubkey]. */
    fun open(myPubkey: String, peerPubkey: String) {
        this.myPubkey = myPubkey
        this.peerPubkey = peerPubkey
        setState { copy(peerPubkey = peerPubkey) }
        observeConversation(myPubkey, peerPubkey)
        sync()
    }

    private fun observeConversation(myPubkey: String, peerPubkey: String) =
        viewModelScope.launch {
            chatRepository.observeConversation(myPubkey, peerPubkey).collect { messages ->
                setState { copy(messages = messages, loading = false) }
            }
        }

    /** Pull the latest conversation snapshot from the broker. */
    fun sync() =
        viewModelScope.launch {
            val me = myPubkey ?: return@launch
            val peer = peerPubkey ?: return@launch
            setState { copy(syncing = true) }
            runCatching { chatRepository.syncConversation(me, peer) }
            setState { copy(syncing = false) }
        }

    /** Send a message to the peer. */
    fun send(text: String) =
        viewModelScope.launch {
            val me = myPubkey ?: return@launch
            val peer = peerPubkey ?: return@launch
            if (text.isBlank()) return@launch
            setState { copy(sending = true) }
            runCatching { chatRepository.sendMessage(me, peer, text) }
            setState { copy(sending = false) }
        }

    data class UiState(
        val peerPubkey: String? = null,
        val messages: List<B2rChatMessageUi> = emptyList(),
        val loading: Boolean = true,
        val syncing: Boolean = false,
        val sending: Boolean = false,
    )
}
