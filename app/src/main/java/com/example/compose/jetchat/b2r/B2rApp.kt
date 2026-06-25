package com.example.compose.jetchat.b2r

import android.app.Application

/**
 * b2r: application entry point with manual dependency wiring (no DI framework —
 * the app is small). Holds the singletons the feed needs.
 */
class B2rApp : Application() {

    lateinit var identity: B2rIdentity
        private set

    lateinit var feedRepository: B2rFeedRepository
        private set

    lateinit var chatRepository: B2rChatRepository
        private set

    override fun onCreate() {
        super.onCreate()
        identity = B2rIdentity(this)
        val broker = B2rMqttBroker()
        feedRepository = B2rFeedRepository(
            store = B2rStore(this),
            broker = broker,
        )
        chatRepository = B2rChatRepository(
            store = B2rChatStore(this),
            broker = broker,
            myKey = identity.pubKey,
        )
    }
}
