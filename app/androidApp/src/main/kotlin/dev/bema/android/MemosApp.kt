package dev.bema.android

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
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
import dev.bema.shared.data.session.MemosUiController
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private val Ink = Color(0xFF050505)
private val InkElevated = Color(0xFF101010)
private val InkLine = Color(0xFF272727)
private val TextPrimary = Color(0xFFF2F2F2)
private val TextSecondary = Color(0xFF8C8C8C)
private val Accent = Color(0xFF1D9BF0)

@Composable
fun BemaMemosApp(controller: MemosUiController = remember { MemosTimelineController() }) {
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
    controller: MemosUiController? = null,
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
private fun SignInScreen(state: MemosAppState, controller: MemosUiController) {
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
private fun TimelineShell(state: MemosAppState, controller: MemosUiController) {
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
private fun TimelineHeader(state: MemosAppState, controller: MemosUiController, onAccounts: () -> Unit) {
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val account = state.activeAccount
        Box(
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Switch account" }
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onAccounts() })
                },
            contentAlignment = Alignment.Center
        ) {
            Avatar(account?.avatarUrl, account?.visibleName ?: "M", Modifier.fillMaxSize(), controller)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account?.siteTitle ?: "Memos", color = TextPrimary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.title3)
            Text(account?.visibleName ?: "@${account?.username ?: "user"}", color = TextSecondary, style = MiuixTheme.textStyles.footnote1)
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
        MiuixNavigationBarItem(selected = true, onClick = {}, icon = MiuixIcons.Home, label = "Timeline")
        MiuixNavigationBarItem(selected = false, onClick = {}, icon = MiuixIcons.Search, label = "Search")
        MiuixNavigationBarItem(selected = false, onClick = onAdd, icon = MiuixIcons.Notes, label = "Compose")
    }
}

@Composable
private fun TimelineScreen(state: MemosAppState, controller: MemosUiController, modifier: Modifier) {
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
private fun MemoTweet(memo: Memo, user: User?, controller: MemosUiController, onReact: (String) -> Unit) {
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
                MarkdownText(memo.content)
                if (memo.tags.isNotEmpty()) Text(memo.tags.joinToString("  ") { "#$it" }, color = Accent, modifier = Modifier.padding(top = 8.dp))
                if (memo.attachments.isNotEmpty()) MediaRail(memo, controller)
                TweetActions(memo, onReact)
            }
        }
    }
    MiuixHorizontalDivider(color = InkLine, thickness = 1.dp)
}

@Composable
private fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        markdown.split('\n').forEach { line ->
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("### ") -> Text(
                    text = markdownInline(line.removePrefix("### ")),
                    color = TextPrimary,
                    style = MiuixTheme.textStyles.headline1.copy(fontWeight = FontWeight.Bold)
                )
                line.startsWith("## ") -> Text(
                    text = markdownInline(line.removePrefix("## ")),
                    color = TextPrimary,
                    style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold)
                )
                line.startsWith("# ") -> Text(
                    text = markdownInline(line.removePrefix("# ")),
                    color = TextPrimary,
                    style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold)
                )
                line.startsWith("- ") || line.startsWith("* ") -> Text(
                    text = markdownInline("• ${line.drop(2)}"),
                    color = TextPrimary,
                    style = MiuixTheme.textStyles.paragraph
                )
                line.startsWith("> ") -> Text(
                    text = markdownInline(line.removePrefix("> ")),
                    color = TextSecondary,
                    style = MiuixTheme.textStyles.paragraph.copy(fontStyle = FontStyle.Italic)
                )
                else -> Text(
                    text = markdownInline(line),
                    color = TextPrimary,
                    style = MiuixTheme.textStyles.paragraph
                )
            }
        }
    }
}

private fun markdownInline(input: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < input.length) {
        when {
            input.startsWith("**", index) -> {
                val end = input.indexOf("**", index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(input.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append("**")
                    index += 2
                }
            }
            input[index] == '`' -> {
                val end = input.indexOf('`', index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(color = Accent, background = InkElevated)) {
                        append(input.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append('`')
                    index++
                }
            }
            input[index] == '[' -> {
                val labelEnd = input.indexOf(']', index + 1)
                val urlStart = labelEnd + 1
                val urlEnd = if (labelEnd >= 0 && input.getOrNull(urlStart) == '(') {
                    input.indexOf(')', urlStart + 1)
                } else {
                    -1
                }
                if (labelEnd > index + 1 && urlEnd > urlStart + 1) {
                    withStyle(SpanStyle(color = Accent, textDecoration = TextDecoration.Underline)) {
                        append(input.substring(index + 1, labelEnd))
                    }
                    index = urlEnd + 1
                } else {
                    append('[')
                    index++
                }
            }
            input[index] == '*' || input[index] == '_' -> {
                val marker = input[index]
                val end = input.indexOf(marker, index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(input.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(marker)
                    index++
                }
            }
            else -> {
                append(input[index])
                index++
            }
        }
    }
}

@Composable
private fun MediaRail(memo: Memo, controller: MemosUiController) {
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
private fun ComposerDialog(state: MemosAppState, controller: MemosUiController, onDismiss: () -> Unit) {
    var editor by remember { mutableStateOf(TextFieldValue()) }
    var showPreview by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(emptyList<PendingAttachment>()) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        attachments = uris.mapNotNull { uri ->
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                PendingAttachment(
                    filename = uri.lastPathSegment ?: "attachment",
                    content = bytes,
                    type = context.contentResolver.getType(uri) ?: "application/octet-stream"
                )
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
            MarkdownModeToggle(showPreview = showPreview, onPreviewChange = { showPreview = it })
            if (showPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4A4A4A))
                        .padding(16.dp)
                ) {
                    if (editor.text.isBlank()) {
                        Text("Nothing to preview", color = TextSecondary)
                    } else {
                        MarkdownText(editor.text)
                    }
                }
            } else {
                MarkdownEditor(editor, Modifier.height(180.dp)) { editor = it }
                MarkdownToolbar(editor) { editor = it }
            }

            if (attachments.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attachments) { attachment ->
                        Box {
                            if (attachment.type.startsWith("image/")) {
                                AsyncImage(
                                    attachment.content,
                                    attachment.filename,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(78.dp).clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(78.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(InkLine)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(attachment.filename, color = TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            MiuixIconButton(
                                onClick = { attachments = attachments.filter { it != attachment } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Text("×", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.clickable { picker.launch("*/*") }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixIcon(MiuixIcons.Photos, contentDescription = "Add attachments", tint = Accent)
                Spacer(Modifier.width(8.dp))
                Text("Add attachments", color = Accent)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                MiuixTextButton(
                    text = if (state.isPublishing) "Posting" else "Post",
                    enabled = (editor.text.isNotBlank() || attachments.isNotEmpty()) && !state.isPublishing,
                    onClick = {
                        scope.launch {
                            controller.publish(editor.text, pendingAttachments = attachments)
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
private fun MarkdownEditor(
    value: TextFieldValue,
    modifier: Modifier = Modifier,
    onValueChange: (TextFieldValue) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MiuixTheme.textStyles.paragraph.copy(color = TextPrimary),
        cursorBrush = SolidColor(Accent),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF4A4A4A))
            .padding(16.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.text.isBlank()) {
                    Text("Write your memo in Markdown", color = TextSecondary)
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun MarkdownModeToggle(showPreview: Boolean, onPreviewChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarkdownModeButton("Write", !showPreview) { onPreviewChange(false) }
        MarkdownModeButton("Preview", showPreview) { onPreviewChange(true) }
    }
}

@Composable
private fun MarkdownModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent else InkLine)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else TextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MarkdownToolbar(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { MarkdownButton("B", "Bold") { onValueChange(insertMarkdown(value, "**")) } }
        item { MarkdownButton("I", "Italic") { onValueChange(insertMarkdown(value, "*")) } }
        item { MarkdownButton("</>", "Code") { onValueChange(insertMarkdown(value, "`")) } }
        item { MarkdownButton("🔗", "Link") { onValueChange(insertMarkdown(value, "[", "]()")) } }
        item { MarkdownButton("#", "Heading") { onValueChange(insertLinePrefix(value, "## ")) } }
        item { MarkdownButton("-", "List") { onValueChange(insertLinePrefix(value, "- ")) } }
    }
}

private fun insertMarkdown(value: TextFieldValue, prefix: String, suffix: String = prefix): TextFieldValue {
    val start = min(value.selection.start, value.selection.end)
    val end = max(value.selection.start, value.selection.end)
    val selected = value.text.substring(start, end)
    val replacement = prefix + selected + suffix
    val cursor = if (selected.isEmpty()) start + prefix.length else start + replacement.length
    return TextFieldValue(value.text.replaceRange(start, end, replacement), TextRange(cursor))
}

private fun insertLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val start = min(value.selection.start, value.selection.end)
    val lineStart = value.text.lastIndexOf('\n', start - 1) + 1
    val updated = value.text.substring(0, lineStart) + prefix + value.text.substring(lineStart)
    val cursor = value.selection.start + prefix.length
    return TextFieldValue(updated, TextRange(cursor))
}

@Composable
private fun MarkdownButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = description }
            .clip(RoundedCornerShape(8.dp))
            .background(InkLine)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = TextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AccountSheet(state: MemosAppState, controller: MemosUiController, onDismiss: () -> Unit) {
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
private fun AccountRow(account: MemosAccount, selected: Boolean, controller: MemosUiController) {
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
private fun MemoDetailScreen(state: MemosAppState, controller: MemosUiController, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var comment by remember { mutableStateOf(TextFieldValue()) }
    var showCommentPreview by remember { mutableStateOf(false) }
    var showCommentBox by remember { mutableStateOf(false) }
    val memo = state.selectedMemo ?: return
    PredictiveBackHandler { controller.closeMemo() }
    
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().background(Ink),
            contentPadding = PaddingValues(bottom = if (showCommentBox) 300.dp else 28.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ink)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiuixIconButton(onClick = { controller.closeMemo() }) {
                        MiuixIcon(MiuixIcons.Back, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Memo", color = TextPrimary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.title3)
                }
            }
            
            item { MemoTweet(memo, state.userProfiles[memo.creator.substringAfterLast('/')], controller) { scope.launch { controller.react(memo, it) } } }
            
            if (state.selectedComments.isNotEmpty()) {
                item {
                    Text(
                        "${state.selectedComments.size} ${if (state.selectedComments.size == 1) "reply" else "replies"}",
                        color = TextSecondary,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
            
            items(state.selectedComments, key = { it.name }) { reply ->
                MemoTweet(reply, state.userProfiles[reply.creator.substringAfterLast('/')], controller) { }
            }
        }
        
        if (showCommentBox) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(InkElevated)
                    .padding(16.dp)
            ) {
                MarkdownModeToggle(showPreview = showCommentPreview, onPreviewChange = { showCommentPreview = it })
                Spacer(Modifier.height(8.dp))
                if (showCommentPreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF4A4A4A))
                            .padding(16.dp)
                    ) {
                        if (comment.text.isBlank()) Text("Nothing to preview", color = TextSecondary)
                        else MarkdownText(comment.text)
                    }
                } else {
                    MarkdownEditor(comment, Modifier.height(120.dp)) { comment = it }
                    MarkdownToolbar(comment) { comment = it }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixTextButton(
                        text = "Cancel",
                        onClick = {
                            comment = TextFieldValue()
                            showCommentPreview = false
                            showCommentBox = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    MiuixTextButton(
                        text = "Reply",
                        enabled = comment.text.isNotBlank(),
                        onClick = {
                            val body = comment.text
                            comment = TextFieldValue()
                            showCommentPreview = false
                            showCommentBox = false
                            scope.launch { controller.comment(body) }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
        
        if (!showCommentBox) {
            MiuixFloatingActionButton(
                onClick = { showCommentBox = true },
                containerColor = Accent,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            ) {
                MiuixIcon(MiuixIcons.Reply, contentDescription = "Reply", tint = Color.White)
            }
        }
    }
}

@Composable private fun LoadingLine() { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Text("Loading…", color = TextSecondary) } }
private fun formatTime(raw: String?): String = raw?.substringAfter('T')?.substringBefore('.')?.removeSuffix("Z")?.take(5).orEmpty()
