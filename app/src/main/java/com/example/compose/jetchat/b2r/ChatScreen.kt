package com.example.compose.jetchat.b2r

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.compose.jetchat.R
import com.example.compose.jetchat.components.JetchatAppBar
import com.example.compose.jetchat.conversation.Message
import com.example.compose.jetchat.conversation.UserInput
import kotlinx.coroutines.launch

/**
 * b2r: private 1:1 chat. With no peer selected it shows the contacts list plus a
 * field to start a new chat from a peer key; with a peer selected it shows that
 * conversation rendered with Jetchat's own [Message] bubbles and [UserInput] bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavIconPressed: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    val activePeer by viewModel.activePeer.collectAsState()
    if (activePeer.isBlank()) {
        ChatList(onNavIconPressed = onNavIconPressed, viewModel = viewModel)
    } else {
        ConversationPane(peer = activePeer, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatList(onNavIconPressed: () -> Unit, viewModel: ChatViewModel) {
    val conversations by viewModel.conversations.collectAsState()
    var draftKey by remember { mutableStateOf(TextFieldValue("")) }

    Scaffold(
        topBar = {
            JetchatAppBar(
                onNavIconPressed = onNavIconPressed,
                title = { Text(text = "Чат", style = MaterialTheme.typography.titleMedium) },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            NewChatBar(
                draftKey = draftKey,
                onDraftChange = { draftKey = it },
                onStart = {
                    viewModel.openConversation(draftKey.text)
                    draftKey = TextFieldValue("")
                },
            )
            HorizontalDivider()
            if (conversations.isEmpty()) {
                Text(
                    text = "Личных чатов пока нет. Вставьте ключ собеседника выше или нажмите на автора в ленте, чтобы написать лично.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(conversations.entries.toList(), key = { it.key }) { (peer, messages) ->
                        ContactRow(
                            label = peerLabel(peer, messages),
                            preview = messages.lastOrNull()?.content.orEmpty(),
                            onClick = { viewModel.openConversation(peer) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewChatBar(draftKey: TextFieldValue, onDraftChange: (TextFieldValue) -> Unit, onStart: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            OutlinedTextField(
                value = draftKey,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Ключ собеседника") },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onStart, enabled = draftKey.text.isNotBlank()) {
                Text("Начать")
            }
        }
    }
}

@Composable
private fun ContactRow(label: String, preview: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        if (preview.isNotBlank()) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationPane(peer: String, viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            JetchatAppBar(
                // The nav icon steps back to the contacts list.
                onNavIconPressed = { viewModel.closeConversation() },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = peerLabel(peer, messages), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "личный чат",
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
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "Сообщений пока нет. Напишите первым — переписку видят только вы двое.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                items(messages.asReversed(), key = { it.id }) { message ->
                    val isMine = message.sender == viewModel.myPubKey
                    Message(
                        onAuthorClick = {},
                        msg = message.toUiMessage(isMine),
                        isUserMe = isMine,
                        isFirstMessageByAuthor = true,
                        isLastMessageByAuthor = true,
                    )
                }
            }
            UserInput(
                onMessageSent = { viewModel.send(it) },
                resetScroll = { scope.launch { scrollState.scrollToItem(0) } },
                modifier = Modifier.navigationBarsPadding().imePadding(),
            )
        }
    }
}

private fun B2rChatMessage.toUiMessage(isMine: Boolean) = Message(
    author = senderName.ifBlank { shortKeyLabel(sender) },
    content = content,
    timestamp = formatTime(createdAt),
    authorImage = if (isMine) R.drawable.ali else R.drawable.someone_else,
)

/** Prefer the peer's last-seen mask; fall back to a short key label. */
private fun peerLabel(peer: String, messages: List<B2rChatMessage>): String =
    messages.lastOrNull { it.sender == peer }?.senderName?.takeIf { it.isNotBlank() }
        ?: shortKeyLabel(peer)
