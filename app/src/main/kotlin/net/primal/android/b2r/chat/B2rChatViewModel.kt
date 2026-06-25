package net.primal.android.b2r.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import net.primal.android.b2r.identity.B2rIdentityStore
import net.primal.data.repository.b2r.chat.B2rChatMessageUi
import net.primal.data.repository.b2r.chat.B2rChatRepository

/**
 * b2r fork: drives a private 1:1 chat with a chosen peer.
 *
 * Call [open] with the peer's key (e.g. found via search); the active account's
 * key is the local side. Messages are end-to-end encrypted by the repository
 * before they hit the broker.
 */
@HiltViewModel
class B2rChatViewModel @Inject constructor(
    private val chatRepository: B2rChatRepository,
    private val identityStore: B2rIdentityStore,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private fun setState(reducer: UiState.() -> UiState) = _state.getAndUpdate { it.reducer() }

    private val myPubkey: String get() = identityStore.pubKey
    private val myPrivkey: String get() = identityStore.privKey
    private var peerPubkey: String? = null

    /** Bind this view model to a conversation with [peerPubkey]. */
    fun open(peerPubkey: String) {
        this.peerPubkey = peerPubkey
        setState { copy(peerPubkey = peerPubkey) }
        observeConversation(peerPubkey)
        sync()
    }

    private fun observeConversation(peerPubkey: String) =
        viewModelScope.launch {
            val me = myPubkey
            if (me.isBlank()) return@launch
            chatRepository.observeConversation(me, peerPubkey).collect { messages ->
                setState { copy(messages = messages, loading = false) }
            }
        }

    /** Pull the latest conversation snapshot from the broker. */
    fun sync() =
        viewModelScope.launch {
            val me = myPubkey
            val peer = peerPubkey ?: return@launch
            if (me.isBlank()) return@launch
            setState { copy(syncing = true) }
            runCatching { chatRepository.syncConversation(myPrivkey, me, peer) }
            setState { copy(syncing = false) }
        }

    /** Send a message to the peer. */
    fun send(text: String) =
        viewModelScope.launch {
            val me = myPubkey
            val peer = peerPubkey ?: return@launch
            if (me.isBlank() || text.isBlank()) return@launch
            setState { copy(sending = true) }
            runCatching { chatRepository.sendMessage(myPrivkey, me, peer, text) }
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
