package dev.bema.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.bema.shared.data.model.Attachment
import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.Visibility
import dev.bema.shared.data.session.MemosAccount
import dev.bema.shared.data.session.MemosAppState
import dev.bema.shared.data.session.MemosTimelineController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun BemaMemosApp(controller: MemosTimelineController = remember { MemosTimelineController() }) {
    val state by controller.state.collectAsState()
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            if (state.activeAccount == null) {
                SignInScreen(state = state, controller = controller)
            } else {
                TimelineShell(state = state, controller = controller)
            }
        }
    }
}

@Composable
private fun SignInScreen(state: MemosAppState, controller: MemosTimelineController) {
    val scope = rememberCoroutineScope()
    var instanceUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Bema", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Native Memos timeline", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = instanceUrl,
            onValueChange = { instanceUrl = it },
            label = { Text("Memos instance") },
            placeholder = { Text("https://memos.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !state.isLoading,
            onClick = { scope.launch { controller.addAccount(instanceUrl, username, password) } },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sign in")
        }
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineShell(state: MemosAppState, controller: MemosTimelineController) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.activeAccount?.instanceUrl.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            state.activeAccount?.visibleName.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { scope.launch { controller.refreshTimeline() } }) { Text("Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (state.selectedMemo != null) {
            MemoDetailScreen(
                state = state,
                controller = controller,
                modifier = Modifier.padding(padding)
            )
        } else {
            TimelineScreen(
                state = state,
                controller = controller,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineScreen(state: MemosAppState, controller: MemosTimelineController, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var composer by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(Visibility.PRIVATE) }
    var showAddAccount by remember { mutableStateOf(false) }
    val shouldLoadMore by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.canLoadMore && lastVisible >= layout.totalItemsCount - 4
        }
    }

    LaunchedEffect(Unit) {
        if (state.timeline.isEmpty()) controller.refreshTimeline()
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) controller.loadMore()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "accounts") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.forEach { account ->
                    FilterChip(
                        selected = account.id == state.activeAccountId,
                        onClick = { scope.launch { controller.selectAccount(account.id) } },
                        label = { Text(accountLabel(account), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
                AssistChip(onClick = { showAddAccount = !showAddAccount }, label = { Text("Add") })
            }
        }
        if (showAddAccount) {
            item(key = "add-account") { InlineSignInCard(controller = controller) }
        }
        item(key = "composer") {
            Card(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = composer,
                        onValueChange = { composer = it },
                        label = { Text("What is happening?") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VisibilityPicker(visibility = visibility, onVisibility = { visibility = it })
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = composer.isNotBlank() && !state.isPublishing,
                            onClick = {
                                val content = composer
                                composer = ""
                                scope.launch { controller.publish(content, visibility) }
                            }
                        ) { Text(if (state.isPublishing) "Posting" else "Post") }
                    }
                }
            }
        }
        state.error?.let { item(key = "error") { ErrorStrip(it) } }
        if (state.isLoading && state.timeline.isEmpty()) {
            item(key = "loading") { LoadingRow() }
        }
        items(state.timeline, key = { it.name }) { memo ->
            MemoCard(
                memo = memo,
                controller = controller,
                onOpen = { scope.launch { controller.openMemo(memo.name) } },
                onReact = { reaction -> scope.launch { controller.react(memo, reaction) } }
            )
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") { LoadingRow() }
        }
    }
}

@Composable
private fun InlineSignInCard(controller: MemosTimelineController) {
    val scope = rememberCoroutineScope()
    var instanceUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(instanceUrl, { instanceUrl = it }, label = { Text("Instance") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = { scope.launch { controller.addAccount(instanceUrl, username, password) } }, modifier = Modifier.align(Alignment.End)) { Text("Add account") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisibilityPicker(visibility: Visibility, onVisibility: (Visibility) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Visibility.PRIVATE, Visibility.PROTECTED, Visibility.PUBLIC).forEach { option ->
            FilterChip(selected = visibility == option, onClick = { onVisibility(option) }, label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoCard(memo: Memo, controller: MemosTimelineController, onOpen: () -> Unit, onReact: (String) -> Unit) {
    Card(
        onClick = onOpen,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(memo.creator.ifBlank { "Memos" }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(formatTime(memo.createTime?.toString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text(memo.content, style = MaterialTheme.typography.bodyLarge)
            if (memo.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    memo.tags.forEach { tag -> AssistChip(onClick = {}, label = { Text("#$tag") }) }
                }
            }
            if (memo.attachments.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AttachmentStrip(memo = memo, controller = controller)
            }
            Spacer(Modifier.height(12.dp))
            ReactionRow(memo = memo, onReact = onReact)
        }
    }
}

@Composable
private fun AttachmentStrip(memo: Memo, controller: MemosTimelineController) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        memo.attachments.forEach { attachment ->
            if (attachment.isImage) {
                val imageBytes by produceState<ByteArray?>(initialValue = null, key1 = attachment.name) {
                    value = controller.attachmentBytes(attachment, thumbnail = true)
                }
                AsyncImage(
                    model = imageBytes,
                    contentDescription = attachment.filename,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                AttachmentRow(attachment)
            }
        }
    }
}

@Composable
private fun AttachmentRow(attachment: Attachment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("File", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(attachment.filename, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(attachment.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReactionRow(memo: Memo, onReact: (String) -> Unit) {
    val grouped = memo.reactions.groupingBy { it.reactionType }.eachCount()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE04", "\uD83D\uDE80").forEach { reaction ->
            OutlinedButton(onClick = { onReact(reaction) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                Text(reaction + grouped[reaction].let { if (it == null) "" else " $it" })
            }
        }
        if (memo.parent != null) Text("Reply", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemoDetailScreen(state: MemosAppState, controller: MemosTimelineController, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var comment by remember { mutableStateOf("") }
    val memo = state.selectedMemo ?: return

    androidx.activity.compose.PredictiveBackHandler { progress ->
        progress.collect()
        controller.closeMemo()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "back") {
            TextButton(onClick = { controller.closeMemo() }) { Text("Back") }
        }
        item(key = "memo") {
            MemoCard(
                memo = memo,
                controller = controller,
                onOpen = {},
                onReact = { reaction -> scope.launch { controller.react(memo, reaction) } }
            )
        }
        item(key = "comment-box") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Comments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(comment, { comment = it }, label = { Text("Write a reply") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Button(
                        enabled = comment.isNotBlank(),
                        onClick = {
                            val body = comment
                            comment = ""
                            scope.launch { controller.comment(body) }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Reply") }
                }
            }
        }
        items(state.selectedComments, key = { it.name }) { reply ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Row {
                        Text(reply.creator.ifBlank { "Memos" }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(formatTime(reply.createTime?.toString()), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(reply.content)
                }
            }
        }
    }
}

@Composable
private fun ErrorStrip(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp)
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun LoadingRow() {
    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

private fun accountLabel(account: MemosAccount): String = "${account.visibleName} @ ${account.instanceUrl.removePrefix("https://").removePrefix("http://") }"

private fun formatTime(raw: String?): String = raw
    ?.replace('T', ' ')
    ?.substringBefore('.')
    ?.removeSuffix("Z")
    .orEmpty()
