package net.primal.android.b2r.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import net.primal.android.user.accounts.active.ActiveAccountStore
import net.primal.data.repository.b2r.chat.B2rChatRepository
import net.primal.data.repository.b2r.chat.B2rConversationPreview

/**
 * b2r fork: the chat list — latest message of every conversation the local user
 * is part of.
 */
@HiltViewModel
class B2rChatListViewModel @Inject constructor(
    private val chatRepository: B2rChatRepository,
    private val activeAccountStore: ActiveAccountStore,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private fun setState(reducer: UiState.() -> UiState) = _state.getAndUpdate { it.reducer() }

    init {
        observeConversations()
    }

    private fun observeConversations() =
        viewModelScope.launch {
            val me = activeAccountStore.activeUserId()
            if (me.isBlank()) {
                setState { copy(loading = false) }
                return@launch
            }
            chatRepository.observeConversations(me).collect { conversations ->
                setState { copy(conversations = conversations, loading = false) }
            }
        }

    data class UiState(
        val conversations: List<B2rConversationPreview> = emptyList(),
        val loading: Boolean = true,
    )
}
