/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.compose.jetchat.profile

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.compose.jetchat.b2r.B2rApp
import com.example.compose.jetchat.b2r.shortKeyLabel

/**
 * b2r: backs the profile screen with the real local identity. The "me" profile is
 * the device's own identity (its editable mask + key); any other id is shown as a
 * read-only peer.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val identity = (application as B2rApp).identity
    private var userId: String = ME

    fun setUserId(newUserId: String?) {
        userId = newUserId ?: ME
        emit()
    }

    /** Persist a new mask for the local identity and refresh the screen. */
    fun updateNickname(newName: String) {
        identity.setNickname(newName)
        emit()
    }

    private fun emit() {
        _userData.value = if (userId == ME) meState() else peerState(userId)
    }

    private fun meState() = ProfileScreenState(
        userId = ME,
        photo = null,
        name = identity.nickname.value,
        status = "Online",
        displayName = identity.keyLabel,
        position = "Ваш локальный профиль b2r",
        twitter = "",
        timeZone = null,
        commonChannels = null,
    )

    private fun peerState(key: String) = ProfileScreenState(
        userId = key,
        photo = null,
        name = shortKeyLabel(key),
        status = "Собеседник b2r",
        displayName = key,
        position = "Личный чат через брокер",
        twitter = "",
        timeZone = "",
        commonChannels = "",
    )

    private val _userData = MutableLiveData<ProfileScreenState>()
    val userData: LiveData<ProfileScreenState> = _userData

    private companion object {
        const val ME = "me"
    }
}

@Immutable
data class ProfileScreenState(
    val userId: String,
    @param:DrawableRes val photo: Int?,
    val name: String,
    val status: String,
    val displayName: String,
    val position: String,
    val twitter: String = "",
    val timeZone: String?, // Null if me
    val commonChannels: String?, // Null if me
) {
    fun isMe() = userId == "me"
}
