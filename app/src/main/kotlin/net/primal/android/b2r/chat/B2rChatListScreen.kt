package net.primal.android.b2r.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.primal.android.b2r.ui.normalizePeerKeyToHex
import net.primal.android.b2r.ui.toB2rDisplayKey

/**
 * b2r fork: the chat list. Paste a person's key (b2r_pub… or hex) to start a
 * private conversation, or tap an existing one.
 */
@Composable
fun B2rChatListScreen(
    onOpenConversation: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: B2rChatListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var peerInput by remember { mutableStateOf(TextFieldValue("")) }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "b2r — чаты",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text("Лента") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = peerInput,
                onValueChange = { peerInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ключ собеседника (b2r_pub…)") },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onOpenConversation(peerInput.text.normalizePeerKeyToHex()) },
                enabled = peerInput.text.isNotBlank(),
            ) {
                Text("Написать")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.conversations) { conversation ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenConversation(conversation.peerPubkey) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = conversation.peerPubkey.toB2rDisplayKey(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = conversation.lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
