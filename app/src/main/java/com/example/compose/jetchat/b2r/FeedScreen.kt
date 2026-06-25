package com.example.compose.jetchat.b2r

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.compose.jetchat.components.JetchatAppBar

/**
 * b2r: the global shared feed, in the Jetchat visual style — themed Scaffold +
 * app bar (the nav icon opens the side drawer), posts rendered as cards tied to
 * the author, and a compose bar to publish anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavIconPressed: () -> Unit,
    viewModel: FeedViewModel = viewModel(),
) {
    val posts by viewModel.posts.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    var draft by remember { mutableStateOf(TextFieldValue("")) }

    Scaffold(
        topBar = {
            JetchatAppBar(
                onNavIconPressed = onNavIconPressed,
                title = {
                    Column {
                        Text(text = "b2r", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Лента · вы ${viewModel.myDisplayKey}…",
                            style = MaterialTheme.typography.labelSmall,
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
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.ime),
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (posts.isEmpty()) {
                    item { EmptyHint() }
                }
                items(posts) { post ->
                    PostItem(post = post, isMine = post.author == viewModel.myPubKey)
                }
            }

            Surface(tonalElevation = 2.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Напишите что угодно…") },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.publish(draft.text)
                            draft = TextFieldValue("")
                        },
                        enabled = draft.text.isNotBlank(),
                    ) {
                        Text("Пост")
                    }
                }
            }
        }
    }
}

@Composable
private fun PostItem(post: B2rPost, isMine: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = if (isMine) "вы" else "b2r_pub" + post.author.take(8) + "…",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Surface(
            color = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    Text(
        text = "Пока пусто. Напишите первый пост — его увидят все, у кого установлен b2r.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}
