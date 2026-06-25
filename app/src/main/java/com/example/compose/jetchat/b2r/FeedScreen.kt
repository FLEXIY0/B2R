package com.example.compose.jetchat.b2r

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.compose.jetchat.R
import com.example.compose.jetchat.components.JetchatAppBar
import com.example.compose.jetchat.conversation.Message
import com.example.compose.jetchat.conversation.UserInput
import kotlinx.coroutines.launch

/**
 * b2r: the global shared feed, built from Jetchat's own chat components — the same
 * [Message] bubbles and [UserInput] bar used by the conversation screen, so a post
 * reads like a message tied to its author's mask. The app-bar nav icon opens the
 * side drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavIconPressed: () -> Unit,
    onOpenChatWith: (String) -> Unit = {},
    viewModel: FeedViewModel = viewModel(),
) {
    val posts by viewModel.posts.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val myNickname by viewModel.myNickname.collectAsState()
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            JetchatAppBar(
                onNavIconPressed = onNavIconPressed,
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Лента", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "вы: $myNickname",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text(if (syncing) "…" else "Обновить")
                    }
                },
            )
        },
        // UserInput supplies its own navigation-bar + ime padding.
        contentWindowInsets = ScaffoldDefaults
            .contentWindowInsets
            .exclude(WindowInsets.navigationBars)
            .exclude(WindowInsets.ime),
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                state = scrollState,
                reverseLayout = true,
                modifier = Modifier.weight(1f),
            ) {
                if (posts.isEmpty()) {
                    item { EmptyHint() }
                }
                items(posts, key = { it.id }) { post ->
                    val isMine = post.author == viewModel.myPubKey
                    val authorLabel = when {
                        isMine -> myNickname
                        post.authorName.isNotBlank() -> post.authorName
                        else -> shortKeyLabel(post.author)
                    }
                    Message(
                        // Tapping someone else's post starts a private chat with them.
                        onAuthorClick = { if (!isMine) onOpenChatWith(post.author) },
                        msg = post.toUiMessage(authorLabel, isMine),
                        isUserMe = isMine,
                        isFirstMessageByAuthor = true,
                        isLastMessageByAuthor = true,
                    )
                }
            }
            UserInput(
                onMessageSent = { viewModel.publish(it) },
                resetScroll = { scope.launch { scrollState.scrollToItem(0) } },
                modifier = Modifier.navigationBarsPadding().imePadding(),
            )
        }
    }
}

private fun B2rPost.toUiMessage(authorLabel: String, isMine: Boolean) = Message(
    author = authorLabel,
    content = content,
    timestamp = formatTime(createdAt),
    authorImage = if (isMine) R.drawable.ali else R.drawable.someone_else,
)

@Composable
private fun EmptyHint() {
    Text(
        text = "Пока пусто. Напишите первый пост — его увидят все, у кого установлен b2r.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}
