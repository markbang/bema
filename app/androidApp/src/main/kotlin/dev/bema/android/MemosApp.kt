package dev.bema.android

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reply
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.window.WindowDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.User
import dev.bema.shared.data.model.Visibility
import dev.bema.shared.data.session.MemosAccount
import dev.bema.shared.data.session.PendingAttachment
import dev.bema.shared.data.session.MemosAppState
import dev.bema.shared.data.session.MemosTimelineController
import kotlinx.coroutines.launch

private val Ink = Color(0xFF050505)
private val InkElevated = Color(0xFF101010)
private val InkLine = Color(0xFF272727)
private val TextPrimary = Color(0xFFF2F2F2)
private val TextSecondary = Color(0xFF8C8C8C)
private val Accent = Color(0xFF1D9BF0)

@Composable
fun BemaMemosApp(controller: MemosTimelineController = remember { MemosTimelineController() }) {
    val state by controller.state.collectAsState()
    val darkColors = darkColorScheme(
        primary = Accent,
        background = Ink,
        onBackground = TextPrimary,
        surface = Ink,
        onSurface = TextPrimary,
        surfaceVariant = InkElevated,
        outline = InkLine,
        dividerLine = InkLine,
        onSurfaceVariantSummary = TextSecondary,
        onSurfaceVariantActions = TextSecondary
    )
    MiuixTheme(colors = darkColors) {
        MiuixSurface(color = Ink, modifier = Modifier.fillMaxSize()) {
            if (state.activeAccount == null) SignInScreen(state, controller)
            else TimelineShell(state, controller)
        }
    }
}

@Composable
private fun Avatar(
    url: String?,
    label: String,
    modifier: Modifier = Modifier,
    controller: MemosTimelineController? = null,
    user: User? = null
) {
    val bytes by produceState<ByteArray?>(initialValue = null, key1 = user?.username) {
        value = if (controller != null && user != null) controller.avatarBytes(user) else null
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF202B35)),
        contentAlignment = Alignment.Center
    ) {
        when {
            !url.isNullOrBlank() -> AsyncImage(url, label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            bytes != null -> AsyncImage(bytes, label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Text(label.trim().firstOrNull()?.uppercase() ?: "M", color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SignInScreen(state: MemosAppState, controller: MemosTimelineController) {
    val scope = rememberCoroutineScope()
    var instance by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Ink).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("bema", color = TextPrimary, style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Black)
        Text("Your Memos, in one timeline.", color = TextSecondary, style = MiuixTheme.textStyles.headline1)
        Spacer(Modifier.height(36.dp))
        DarkField(instance, { instance = it }, "Memos instance", "memos.example.com")
        Spacer(Modifier.height(12.dp))
        DarkField(username, { username = it }, "Username")
        Spacer(Modifier.height(12.dp))
        DarkField(password, { password = it }, "Password", secure = true)
        Spacer(Modifier.height(20.dp))
        MiuixButton(
            enabled = !state.isLoading,
            onClick = { scope.launch { controller.addAccount(instance, username, password) } },
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp
        ) { Text(if (state.isLoading) "Connecting…" else "Sign in") }
        state.error?.let { Text(it, color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 14.dp)) }
    }
}

@Composable
private fun DarkField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String = "", secure: Boolean = false) {
    MiuixTextField(
        value = value,
        onValueChange = onValueChange,
        label = if (placeholder.isBlank()) label else placeholder,
        useLabelAsPlaceholder = placeholder.isNotBlank(),
        singleLine = true,
        visualTransformation = if (secure) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TimelineShell(state: MemosAppState, controller: MemosTimelineController) {
    val scope = rememberCoroutineScope()
    var showComposer by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    MiuixScaffold(
        containerColor = Ink,
        topBar = {
            TimelineHeader(state, controller) { showAccounts = true }
        },
        bottomBar = { BottomNav(onAdd = { showComposer = true }) },
        floatingActionButton = {
            MiuixFloatingActionButton(onClick = { showComposer = true }, containerColor = Accent, shape = CircleShape) {
                MiuixIcon(MiuixIcons.Add, contentDescription = "New memo", tint = Color.White)
            }
        }
    ) { padding ->
        if (state.selectedMemo != null) MemoDetailScreen(state, controller, Modifier.padding(padding))
        else TimelineScreen(state, controller, Modifier.padding(padding))
    }
    if (showComposer) ComposerDialog(state, controller) { showComposer = false }
    if (showAccounts) AccountSheet(state, controller) { showAccounts = false }
}

@Composable
private fun TimelineHeader(state: MemosAppState, controller: MemosTimelineController, onAccounts: () -> Unit) {
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val account = state.activeAccount
        Avatar(account?.avatarUrl, account?.visibleName ?: "M", Modifier.size(36.dp), controller)
        Spacer(Modifier.width(10.dp))
        Avatar(controller.siteLogoUrl().ifBlank { null }, account?.siteTitle ?: "M", Modifier.size(24.dp), controller)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("For you", color = TextPrimary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.title3)
            Text(account?.siteTitle ?: "Memos", color = TextSecondary, style = MiuixTheme.textStyles.footnote1)
        }
        MiuixIconButton(onClick = onAccounts) {
            MiuixIcon(MiuixIcons.ExpandMore, contentDescription = "Switch account", tint = TextPrimary)
        }
        MiuixIconButton(onClick = { scope.launch { controller.refreshTimeline() } }) {
            MiuixIcon(MiuixIcons.Refresh, contentDescription = "Refresh", tint = TextPrimary)
        }
    }
}

@Composable
private fun BottomNav(onAdd: () -> Unit) {
    MiuixNavigationBar(
        color = Ink.copy(alpha = .98f),
        showDivider = true,
        mode = NavigationBarDisplayMode.IconOnly
    ) {
        MiuixNavigationBarItem(selected = true, onClick = {}, icon = MiuixIcons.Home, label = "Home")
        MiuixNavigationBarItem(selected = false, onClick = {}, icon = MiuixIcons.Search, label = "Search")
        MiuixNavigationBarItem(selected = false, onClick = onAdd, icon = MiuixIcons.Notes, label = "Compose")
        MiuixNavigationBarItem(selected = false, onClick = {}, icon = MiuixIcons.Messages, label = "Notifications")
        MiuixNavigationBarItem(selected = false, onClick = {}, icon = MiuixIcons.Favorites, label = "Favorites")
    }
}

@Composable
private fun TimelineScreen(state: MemosAppState, controller: MemosTimelineController, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val shouldLoadMore by remember { derivedStateOf {
        val info = listState.layoutInfo
        state.canLoadMore && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
    } }
    LaunchedEffect(Unit) { if (state.timeline.isEmpty()) controller.refreshTimeline() }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) controller.loadMore() }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item { if (state.isLoading && state.timeline.isEmpty()) LoadingLine() }
        state.error?.let { message -> item { Text(message, color = Color(0xFFFF6B6B), modifier = Modifier.padding(18.dp)) } }
        items(state.timeline, key = { it.name }) { memo ->
            MemoTweet(memo, state.userProfiles[memo.creator.substringAfterLast('/')], controller) { reaction -> scope.launch { controller.react(memo, reaction) } }
        }
        if (state.isLoadingMore) item { LoadingLine() }
    }
}

@Composable
private fun MemoTweet(memo: Memo, user: User?, controller: MemosTimelineController, onReact: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val name = user?.visibleName ?: memo.creator.substringAfterLast('/').ifBlank { "Memos" }
    Column(Modifier.fillMaxWidth().clickable { scope.launch { controller.openMemo(memo.name) } }.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(user?.avatarUrl, name, Modifier.size(46.dp), controller, user)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Text("@${user?.username ?: memo.creator.substringAfterLast('/')}", color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Text("· ${formatTime(memo.createTime?.toString())}", color = TextSecondary, maxLines = 1)
                }
                Spacer(Modifier.height(5.dp))
                Text(memo.content, color = TextPrimary, style = MiuixTheme.textStyles.paragraph)
                if (memo.tags.isNotEmpty()) Text(memo.tags.joinToString("  ") { "#$it" }, color = Accent, modifier = Modifier.padding(top = 8.dp))
                if (memo.attachments.isNotEmpty()) MediaRail(memo, controller)
                TweetActions(memo, onReact)
            }
        }
    }
    MiuixHorizontalDivider(color = InkLine, thickness = 1.dp)
}

@Composable
private fun MediaRail(memo: Memo, controller: MemosTimelineController) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        items(memo.attachments.filter { it.isImage }, key = { it.name }) { attachment ->
            val bytes by produceState<ByteArray?>(null, attachment.name) { value = controller.attachmentBytes(attachment, thumbnail = true) }
            AsyncImage(
                model = bytes,
                contentDescription = attachment.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 260.dp, height = 210.dp).clip(RoundedCornerShape(16.dp)).background(InkElevated)
            )
        }
    }
}

@Composable
private fun TweetActions(memo: Memo, onReact: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Action(MiuixIcons.Reply, "Reply", memo.relations.count { it.type.name == "COMMENT" }.toString())
        Action(MiuixIcons.Link, "Copy link", "")
        Action(MiuixIcons.Favorites, "React", memo.reactions.size.toString(), onClick = { onReact("❤️") })
        Action(MiuixIcons.Share, "Share", "")
        Action(MiuixIcons.More, "More", "")
    }
}

@Composable
private fun Action(icon: ImageVector, description: String, count: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.clickable(enabled = onClick != null) { onClick?.invoke() }, verticalAlignment = Alignment.CenterVertically) {
        MiuixIcon(icon, contentDescription = description, tint = TextSecondary, modifier = Modifier.size(20.dp))
        if (count.isNotBlank()) Text("  $count", color = TextSecondary, style = MiuixTheme.textStyles.footnote2)
    }
}

@Composable
private fun ComposerDialog(state: MemosAppState, controller: MemosTimelineController, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(emptyList<PendingAttachment>()) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        attachments = uris.mapNotNull { uri ->
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                PendingAttachment(uri.lastPathSegment ?: "image.jpg", bytes, context.contentResolver.getType(uri) ?: "image/jpeg")
            }.getOrNull()
        }
    }
    WindowDialog(
        show = true,
        title = "New memo",
        backgroundColor = InkElevated,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DarkField(text, { text = it }, "Say something", secure = false)
            if (attachments.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attachments) { attachment ->
                        AsyncImage(
                            attachment.content,
                            attachment.filename,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(78.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.clickable { picker.launch("image/*") }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixIcon(MiuixIcons.Photos, contentDescription = "Add photos", tint = Accent)
                Spacer(Modifier.width(8.dp))
                Text("Add photos", color = Accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                MiuixTextButton(
                    text = if (state.isPublishing) "Posting" else "Post",
                    enabled = (text.isNotBlank() || attachments.isNotEmpty()) && !state.isPublishing,
                    onClick = {
                        scope.launch {
                            controller.publish(text, pendingAttachments = attachments)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun AccountSheet(state: MemosAppState, controller: MemosTimelineController, onDismiss: () -> Unit) {
    WindowDialog(
        show = true,
        title = "Accounts",
        backgroundColor = InkElevated,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            state.accounts.forEach { account -> AccountRow(account, account.id == state.activeAccountId, controller) }
            MiuixTextButton(
                text = "Done",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun AccountRow(account: MemosAccount, selected: Boolean, controller: MemosTimelineController) {
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().clickable { scope.launch { controller.selectAccount(account.id) } }, verticalAlignment = Alignment.CenterVertically) {
        Avatar(controller.accountLogoUrl(account).ifBlank { account.avatarUrl }, account.siteTitle, Modifier.size(38.dp), controller)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account.siteTitle, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(account.visibleName, color = TextSecondary)
        }
        if (selected) MiuixIcon(MiuixIcons.Ok, contentDescription = "Selected", tint = Accent)
    }
}

@Composable
private fun MemoDetailScreen(state: MemosAppState, controller: MemosTimelineController, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var comment by remember { mutableStateOf("") }
    val memo = state.selectedMemo ?: return
    PredictiveBackHandler { controller.closeMemo() }
    LazyColumn(modifier.fillMaxSize().background(Ink), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(
                modifier = Modifier.clickable { controller.closeMemo() }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixIcon(MiuixIcons.Back, contentDescription = "Back", tint = Accent)
                Spacer(Modifier.width(8.dp))
                Text("Back", color = Accent)
            }
        }
        item { MemoTweet(memo, state.userProfiles[memo.creator.substringAfterLast('/')], controller) { scope.launch { controller.react(memo, it) } } }
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Replies", color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                DarkField(comment, { comment = it }, "Write a reply")
                MiuixTextButton(
                    text = "Reply",
                    enabled = comment.isNotBlank(),
                    onClick = {
                        val body = comment
                        comment = ""
                        scope.launch { controller.comment(body) }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
        items(state.selectedComments, key = { it.name }) { reply ->
            MemoTweet(reply, state.userProfiles[reply.creator.substringAfterLast('/')], controller) { }
        }
    }
}

@Composable private fun LoadingLine() { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Text("Loading…", color = TextSecondary) } }
private fun formatTime(raw: String?): String = raw?.substringAfter('T')?.substringBefore('.')?.removeSuffix("Z")?.take(5).orEmpty()
