package com.example.compose.jetchat.b2r

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * b2r: drives the feed screen. Plain [AndroidViewModel] (no DI framework) — it
 * reaches the manually-wired singletons through [B2rApp].
 */
class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as B2rApp

    val posts: StateFlow<List<B2rPost>> = app.feedRepository.posts
    val myPubKey: String = app.identity.pubKey
    val myDisplayKey: String = app.identity.displayKey

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    init {
        refresh()
    }

    fun publish(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch { app.feedRepository.createPost(app.identity.pubKey, content) }
    }

    fun refresh() {
        viewModelScope.launch {
            _syncing.value = true
            runCatching { app.feedRepository.refresh() }
            _syncing.value = false
        }
    }
}
