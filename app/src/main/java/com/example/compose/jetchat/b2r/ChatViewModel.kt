package com.example.compose.jetchat.b2r

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * b2r: drives the private chat screen. Holds the active peer and exposes that
 * conversation's messages, reaching the manually-wired singletons via [B2rApp].
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as B2rApp

    val myPubKey: String = app.identity.pubKey
    val myNickname: StateFlow<String> = app.identity.nickname

    private val _activePeer = MutableStateFlow("")
    val activePeer: StateFlow<String> = _activePeer

    /** All conversations (peer key → messages), for the contacts list. */
    val conversations: StateFlow<Map<String, List<B2rChatMessage>>> = app.chatRepository.conversations

    /** Messages of the currently selected conversation, oldest first. */
    val messages: StateFlow<List<B2rChatMessage>> =
        combine(app.chatRepository.conversations, _activePeer) { convs, peer ->
            convs[peer].orEmpty()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    fun openConversation(peerKey: String) {
        val trimmed = peerKey.trim()
        if (trimmed.isEmpty() || trimmed == myPubKey) return
        _activePeer.value = trimmed
        refresh()
    }

    fun closeConversation() {
        _activePeer.value = ""
    }

    fun send(content: String) {
        val peer = _activePeer.value
        if (peer.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            app.chatRepository.send(peer = peer, senderName = app.identity.nickname.value, content = content)
        }
    }

    fun refresh() {
        val peer = _activePeer.value
        if (peer.isBlank()) return
        viewModelScope.launch {
            _syncing.value = true
            runCatching { app.chatRepository.refresh(peer) }
            _syncing.value = false
        }
    }
}
