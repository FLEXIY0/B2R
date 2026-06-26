package com.example.compose.jetchat.b2r

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.compose.jetchat.profile.ProfileScreen
import com.example.compose.jetchat.profile.ProfileScreenState
import com.example.compose.jetchat.theme.JetchatTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real b2r screens to PNG on the JVM (Robolectric + Roborazzi) — no
 * emulator, no APK install. Run `./gradlew recordRoborazziDebug` to (re)generate
 * the snapshots under app/build/outputs/roborazzi. This is the fast, faithful UI
 * loop: tweak the composable, re-record, look at the PNG.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class B2rScreenshotTest {

    private val me = "me00deadbeefcafe"
    private val peer = "peerkira9f3a1c20"

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            Box(Modifier.size(411.dp, 891.dp)) {
                JetchatTheme(isDarkTheme = true, isDynamicColor = false) {
                    content()
                }
            }
        }
    }

    @Test
    fun feed() = shot("b2r_feed") {
        FeedContent(
            posts = listOf(
                B2rPost("p3", "max22aa", "красиво, в тему сборки 🔥", 1_750_000_180_000, "max"),
                B2rPost("p2", me, "да, и можно тапнуть автора → личный чат", 1_750_000_120_000, "b2r-3f9ac2"),
                B2rPost("p1", "kira9f3a", "всем привет 👋 кто уже поставил b2r?", 1_750_000_060_000, "kira"),
            ),
            myPubKey = me,
            myNickname = "b2r-3f9ac2",
            syncing = false,
            onRefresh = {},
            onPublish = {},
            onNavIconPressed = {},
            onOpenChatWith = {},
        )
    }

    @Test
    fun chat() = shot("b2r_chat") {
        ConversationPaneContent(
            peer = peer,
            messages = listOf(
                B2rChatMessage("m1", peer, "kira", "привет! увидела твой пост в ленте", 1_750_000_060_000),
                B2rChatMessage("m2", me, "b2r-3f9ac2", "привет 🙂 это уже только между нами", 1_750_000_120_000),
                B2rChatMessage("m3", peer, "kira", "шифруется через брокер, никто не прочитает?", 1_750_000_180_000),
                B2rChatMessage("m4", me, "b2r-3f9ac2", "ага, топик и ключ из пары — только мы двое", 1_750_000_240_000),
            ),
            myPubKey = me,
            syncing = false,
            onBack = {},
            onRefresh = {},
            onSend = {},
        )
    }

    @Test
    fun chatList() = shot("b2r_chat_list") {
        ChatListContent(
            conversations = mapOf(
                peer to listOf(
                    B2rChatMessage("m3", peer, "kira", "шифруется через брокер, никто не прочитает?", 1_750_000_180_000),
                ),
                "peermax22aa00" to listOf(
                    B2rChatMessage("x1", "peermax22aa00", "max", "скинь ключ, добавлю тебя", 1_750_000_090_000),
                ),
            ),
            onOpenConversation = {},
            onNavIconPressed = {},
        )
    }

    @Test
    fun profile() = shot("b2r_profile") {
        ProfileScreen(
            userData = ProfileScreenState(
                userId = "me",
                photo = null,
                name = "b2r-3f9ac2",
                status = "Online",
                displayName = "b2r_pub3f9ac2e1b8…",
                position = "Ваш локальный профиль b2r",
                twitter = "",
                timeZone = null,
                commonChannels = null,
            ),
        )
    }
}
