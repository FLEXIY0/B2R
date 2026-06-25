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

package com.example.compose.jetchat.conversation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.compose.jetchat.MainViewModel
import com.example.compose.jetchat.b2r.ChatScreen
import com.example.compose.jetchat.b2r.ChatViewModel
import com.example.compose.jetchat.theme.JetchatTheme

/** b2r: hosts the private chat ("Чат" tab). */
class ConversationFragment : Fragment() {

    private val activityViewModel: MainViewModel by activityViewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

            // When opened from a feed author tap, jump straight into that conversation.
            val peerKey = arguments?.getString("peerKey")

            setContent {
                JetchatTheme {
                    LaunchedEffect(peerKey) {
                        if (!peerKey.isNullOrBlank()) chatViewModel.openConversation(peerKey)
                    }
                    ChatScreen(
                        onNavIconPressed = { activityViewModel.openDrawer() },
                        viewModel = chatViewModel,
                    )
                }
            }
        }
}
