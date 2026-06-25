package com.example.compose.jetchat.b2r

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.compose.jetchat.R
import com.example.compose.jetchat.theme.JetchatTheme

/** b2r: hosts the global feed screen (the app's home / "Лента" tab). */
class FeedFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setContent {
                JetchatTheme {
                    FeedScreen(
                        onOpenChat = { findNavController().navigate(R.id.nav_home) },
                    )
                }
            }
        }
}
