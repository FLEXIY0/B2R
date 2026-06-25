package net.primal.android.b2r

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import net.primal.android.b2r.chat.B2rChatListScreen
import net.primal.android.b2r.chat.B2rChatScreen
import net.primal.android.b2r.feed.B2rFeedScreen
import net.primal.android.theme.PrimalTheme
import net.primal.android.theme.findThemeOrDefault

/**
 * b2r fork: self-contained entry point for the b2r experience (global feed +
 * private chats), isolated from Primal's navigation graph. Launched from its own
 * home-screen icon.
 */
@AndroidEntryPoint
class B2rActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrimalTheme(primalTheme = findThemeOrDefault(isDark = isSystemInDarkTheme())) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    B2rApp()
                }
            }
        }
    }
}

private const val ROUTE_FEED = "feed"
private const val ROUTE_CHATS = "chats"
private const val ROUTE_CHAT = "chat"
private const val ARG_PEER = "peer"

@Composable
private fun B2rApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_FEED) {
        composable(ROUTE_FEED) {
            B2rFeedScreen(onOpenChats = { navController.navigate(ROUTE_CHATS) })
        }
        composable(ROUTE_CHATS) {
            B2rChatListScreen(
                onOpenConversation = { peer ->
                    if (peer.isNotBlank()) navController.navigate("$ROUTE_CHAT/$peer")
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable("$ROUTE_CHAT/{$ARG_PEER}") { entry ->
            B2rChatScreen(
                peerPubkey = entry.arguments?.getString(ARG_PEER).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
