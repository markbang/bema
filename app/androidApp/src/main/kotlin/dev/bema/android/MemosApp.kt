package dev.bema.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reply
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Unpin
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.FileProvider
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
import dev.bema.shared.data.model.CustomProfile
import dev.bema.shared.data.model.GeneralSetting
import dev.bema.shared.data.model.InstanceSetting
import dev.bema.shared.data.model.Memo
import dev.bema.shared.data.model.User
import dev.bema.shared.data.model.Visibility
import dev.bema.shared.data.model.StorageSetting
import dev.bema.shared.data.session.SettingOption
import dev.bema.shared.data.session.ThemeMode
import dev.bema.shared.data.session.ThemeModeOptions
import dev.bema.shared.data.session.canonicalThemeMode
import dev.bema.shared.data.session.themeModeLabel
import dev.bema.shared.data.session.themeModeValue
import dev.bema.shared.data.session.uploadSizeLabel
import dev.bema.shared.data.session.uploadSizeOptionsFor
import dev.bema.shared.data.session.HEART_REACTION
import dev.bema.shared.data.session.MemosAccount
import dev.bema.shared.data.session.PendingAttachment
import dev.bema.shared.data.session.MemosAppState
import dev.bema.shared.data.session.ActivityStats
import dev.bema.shared.data.session.CalendarDays
import dev.bema.shared.data.session.TagCount
import dev.bema.shared.data.session.todayEpochDay
import dev.bema.shared.data.session.MemosTimelineController
import dev.bema.shared.data.session.MemosUiController
import dev.bema.shared.data.session.formatMemoTime
import dev.bema.shared.data.session.isLikedBy
import dev.bema.shared.data.update.AvailableUpdate
import dev.bema.shared.data.update.UpdateDownload
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.min

/**
 * App colour tokens. The two palettes mirror each other so call sites name a
 * token instead of a literal, and [LocalBemaPalette] lets [ThemeMode] switch
 * every one of them at once. iOS mirrors these in `Design/Theme.swift`.
 */
private data class BemaPalette(
    val ink: Color,
    val inkElevated: Color,
    val inkLine: Color,
    val editorSurface: Color,
    val avatarBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val danger: Color
)

private val DarkPalette = BemaPalette(
    ink = Color(0xFF050505),
    inkElevated = Color(0xFF101010),
    inkLine = Color(0xFF272727),
    editorSurface = Color(0xFF4A4A4A),
    avatarBackground = Color(0xFF202B35),
    textPrimary = Color(0xFFF2F2F2),
    textSecondary = Color(0xFF8C8C8C),
    accent = Color(0xFF1D9BF0),
    danger = Color(0xFFFF6B6B)
)

private val LightPalette = BemaPalette(
    ink = Color(0xFFF5F6F8),
    inkElevated = Color(0xFFFFFFFF),
    inkLine = Color(0xFFE2E4E9),
    editorSurface = Color(0xFFEDEFF3),
    avatarBackground = Color(0xFFDCE2E9),
    textPrimary = Color(0xFF16181D),
    textSecondary = Color(0xFF63676E),
    accent = Color(0xFF0A7BC0),
    danger = Color(0xFFC53B3B)
)

private val LocalBemaPalette = staticCompositionLocalOf { DarkPalette }

// Reading through the active palette keeps the existing call sites unchanged.
private val Ink: Color @Composable @ReadOnlyComposable get() = LocalBemaPalette.current.ink
private val InkElevated: Color @Composable @ReadOnlyComposable get() = LocalBemaPalette.current.inkElevated
private val InkLine: Color @Composable @ReadOnlyComposable get() = LocalBemaPalette.current.inkLine
private val EditorSurface: Color @Composable @ReadOnlyComposable get() = LocalBemaPalette.current.editorSurface
private val AvatarBackground: Color @Composable @ReadOnlyComposable get() = LocalBemaPalette.current.avatarBackground
private val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalBemaPalette.current.textPrimary
private val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalBemaPalette.current.textSecondary
private val Accent: Color @Composable @ReadOnlyComposable get() = LocalBemaPalette.current.accent
private val Danger: Color @Composable @ReadOnlyComposable get() = LocalBemaPalette.current.danger

@Composable
fun BemaMemosApp(controller: MemosUiController = remember { MemosTimelineController() }) {
    val state by controller.state.collectAsState()
    // "Later" only hides the prompt for this process; "skip" persists in the controller.
    var dismissedUpdate by remember { mutableStateOf<String?>(null) }
    // Held so cancelling the download actually stops it, rather than letting the
    // installer appear after the user backed out.
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    // Rooted at the app, not in SignInScreen: adding an account outlives the sign-in
    // screen, which the state change removes while the first timeline load is still
    // running. A scope owned by that screen cancels the load, and Compose reports
    // "rememberCoroutineScope left the composition" through the controller's error.
    val appScope = rememberCoroutineScope()
    val context = LocalContext.current
    val dark = when (state.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) DarkPalette else LightPalette
    ApplyEdgeToEdge(dark)
    CompositionLocalProvider(LocalBemaPalette provides palette) {
        MiuixTheme(colors = palette.toColorScheme(dark)) {
            MiuixSurface(
                color = palette.ink,
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.ink)
            ) {
                // Best-effort; deviceAbi() is null off Android and checkForUpdate()
                // swallows transport failures, so this never blocks or errors the UI.
                LaunchedEffect(Unit) { controller.checkForUpdate() }
                if (state.activeAccount == null) {
                    SignInScreen(state) { instance, username, password ->
                        appScope.launch { controller.addAccount(instance, username, password) }
                    }
                } else {
                    TimelineShell(state, controller)
                }

                val update = state.availableUpdate
                if (update != null && update.version != dismissedUpdate) {
                    UpdateDialog(
                        update = update,
                        download = state.updateDownload,
                        onDismiss = { dismissedUpdate = update.version },
                        onSkip = { controller.skipUpdate(update.version) },
                        onDownload = {
                            downloadJob = appScope.launch {
                                val bytes = controller.downloadUpdate() ?: return@launch
                                installUpdate(context, bytes, update.downloadUrl)
                            }
                        },
                        onCancelDownload = {
                            downloadJob?.cancel()
                            dismissedUpdate = update.version
                        }
                    )
                }
            }
        }
    }
}

private fun BemaPalette.toColorScheme(dark: Boolean): Colors = if (dark) {
    darkColorScheme(
        primary = accent,
        background = ink,
        onBackground = textPrimary,
        surface = ink,
        onSurface = textPrimary,
        surfaceVariant = inkElevated,
        outline = inkLine,
        dividerLine = inkLine,
        onSurfaceVariantSummary = textSecondary,
        onSurfaceVariantActions = textSecondary
    )
} else {
    lightColorScheme(
        primary = accent,
        background = ink,
        onBackground = textPrimary,
        surface = ink,
        onSurface = textPrimary,
        surfaceVariant = inkElevated,
        outline = inkLine,
        dividerLine = inkLine,
        onSurfaceVariantSummary = textSecondary,
        onSurfaceVariantActions = textSecondary
    )
}

@Composable
private fun ApplyEdgeToEdge(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    // Keyed so bar styling is only reapplied when the theme or activity changes,
    // not on every recomposition.
    LaunchedEffect(activity, dark) { activity.enableBemaEdgeToEdge(dark) }
}

@Composable
private fun Avatar(
    bytes: ByteArray?,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(AvatarBackground),
        contentAlignment = Alignment.Center
    ) {
        if (bytes != null) {
            AsyncImage(bytes, label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(label.trim().firstOrNull()?.uppercase() ?: "M", color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UserAvatar(user: User?, label: String, modifier: Modifier = Modifier, controller: MemosUiController) {
    val bytes by produceState<ByteArray?>(null, user?.username) {
        value = user?.let { controller.avatarBytes(it) }
    }
    Avatar(bytes, label, modifier)
}

@Composable
private fun AccountAvatar(account: MemosAccount?, label: String, modifier: Modifier = Modifier, controller: MemosUiController) {
    val bytes by produceState<ByteArray?>(null, account?.id) {
        value = account?.let { controller.accountAvatarBytes(it) }
    }
    Avatar(bytes, label, modifier)
}

@Composable
private fun SignInScreen(state: MemosAppState, onSignIn: (String, String, String) -> Unit) {
    var instance by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
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
            enabled = !state.isLoading && instance.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            onClick = { onSignIn(instance, username, password) },
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp
        ) { Text(if (state.isLoading) "Connecting…" else "Sign in") }
        state.error?.let { Text(it, color = Danger, modifier = Modifier.padding(top = 14.dp)) }
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
    var showSettings by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var commentOnOpen by remember { mutableStateOf(false) }
    var pendingTag by remember { mutableStateOf<String?>(null) }
    var imageViewer by remember { mutableStateOf<ImageViewerTarget?>(null) }
    val detailProgress = remember { Animatable(0f) }
    var backInProgress by remember { mutableStateOf(false) }
    val viewerProgress = remember { Animatable(0f) }
    var viewerBackInProgress by remember { mutableStateOf(false) }
    val headerHeightPx = remember { mutableFloatStateOf(0f) }
    val headerOffsetPx = remember { mutableFloatStateOf(0f) }
    // Activity panel: a sideways drag on the content reveals it (the timeline has no
    // horizontal gesture of its own), and it snaps open or shut when the drag ends.
    val activityOffset = remember { Animatable(0f) }
    var activityOpen by remember { mutableStateOf(false) }
    var activityWidthPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(activityOpen, activityWidthPx) {
        activityOffset.animateTo(if (activityOpen) activityWidthPx else 0f, tween(220))
    }
    LaunchedEffect(activityOpen) {
        if (activityOpen) controller.refreshActivityStats()
    }
    val activityDrag = rememberDraggableState { delta ->
        scope.launch {
            activityOffset.snapTo((activityOffset.value + delta).coerceIn(0f, activityWidthPx.coerceAtLeast(1f)))
        }
    }
    MiuixScaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            // The activity panel is modal, so the chrome underneath it steps aside
            // rather than being drawn across the panel.
            if (state.selectedMemo == null && imageViewer == null && activityOffset.value <= 0f) {
                BottomNav(
                    selectedTab = selectedTab,
                    onTabChange = { tab ->
                        selectedTab = tab
                        headerOffsetPx.floatValue = 0f
                    },
                    onSettings = { showSettings = true }
                )
            }
        },
        floatingActionButton = {
            if (state.selectedMemo == null && selectedTab == 0 && imageViewer == null && activityOffset.value <= 0f) {
                MiuixFloatingActionButton(onClick = { showComposer = true }, containerColor = Accent, shape = CircleShape) {
                    MiuixIcon(MiuixIcons.Add, contentDescription = "New memo", tint = Color.White)
                }
            }
        }
    ) { padding ->
        // Predictive back: the detail screen slides with the system gesture and the
        // tab underneath stays composed so it is revealed while swiping (and keeps
        // its scroll position). The handler lives here, not in the detail, so the
        // launching coroutine survives the screen switch.
        val openMemo: (Memo) -> Unit = { memo -> scope.launch { controller.openMemo(memo.name) } }
        val openImage: (Memo, Int) -> Unit = { memo, index -> imageViewer = ImageViewerTarget(memo, index) }

        PredictiveBackHandler(enabled = imageViewer != null) { events ->
            viewerBackInProgress = true
            try {
                events.collect { viewerProgress.snapTo(it.progress) }
                imageViewer = null
            } finally {
                viewerBackInProgress = false
            }
        }
        PredictiveBackHandler(enabled = imageViewer == null && state.selectedMemo != null) { events ->
            backInProgress = true
            try {
                events.collect { detailProgress.snapTo(it.progress) }
                controller.closeMemo()
            } finally {
                backInProgress = false
            }
        }
        // Back from the search tab returns to the timeline instead of leaving the app.
        PredictiveBackHandler(enabled = imageViewer == null && state.selectedMemo == null && selectedTab == 1) { events ->
            events.collect { }
            selectedTab = 0
        }
        LaunchedEffect(state.selectedMemo?.name, backInProgress) {
            when {
                state.selectedMemo == null -> detailProgress.snapTo(0f)
                !backInProgress -> detailProgress.animateTo(0f, tween(durationMillis = 200))
            }
        }
        LaunchedEffect(imageViewer, viewerBackInProgress) {
            when {
                imageViewer == null -> viewerProgress.snapTo(0f)
                !viewerBackInProgress -> viewerProgress.animateTo(0f, tween(durationMillis = 200))
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { activityWidthPx = it.width * ACTIVITY_PANEL_FRACTION }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = activityDrag,
                    onDragStopped = { activityOpen = activityOffset.value > activityWidthPx / 2f }
                )
        ) {
            if (selectedTab == 1) {
                SearchScreen(
                    state,
                    controller,
                    Modifier.padding(bottom = padding.calculateBottomPadding()),
                    chromeHeightPx = headerHeightPx.floatValue,
                    seedQuery = pendingTag,
                    onSeedConsumed = { pendingTag = null },
                    onOpen = openMemo,
                    onOpenImage = openImage
                )
            } else {
                TimelineScreen(
                    state,
                    controller,
                    Modifier.padding(bottom = padding.calculateBottomPadding()),
                    headerHeightPx,
                    headerOffsetPx,
                    openMemo,
                    { memo ->
                        commentOnOpen = true
                        openMemo(memo)
                    },
                    openImage
                )
            }
            if (state.selectedMemo == null) {
                TopChrome(
                    offsetPx = if (selectedTab == 0) headerOffsetPx.floatValue else 0f,
                    onHeight = { headerHeightPx.floatValue = it }
                ) {
                    if (selectedTab == 1) SearchHeader(state)
                    else TimelineHeader(
                        state,
                        controller,
                        onSearch = { selectedTab = 1 },
                        onAccounts = { showAccounts = true }
                    )
                }
                StatusBarVeil()
            }
            if (state.selectedMemo != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.6f * (1f - detailProgress.value) }
                        .background(Ink)
                        .pointerInput(Unit) { detectTapGestures { } }
                )
                MemoDetailScreen(
                    state,
                    controller,
                    Modifier.padding(bottom = padding.calculateBottomPadding()),
                    backProgress = { detailProgress.value },
                    startComment = commentOnOpen,
                    onCommentConsumed = { commentOnOpen = false },
                    onOpenImage = openImage
                )
            }

            // The activity panel is drawn over everything, chrome and tabs included.
            if (activityOffset.value > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(
                                alpha = 0.45f * (activityOffset.value / activityWidthPx.coerceAtLeast(1f))
                            )
                        )
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = activityDrag,
                            onDragStopped = { activityOpen = activityOffset.value > activityWidthPx / 2f }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { activityOpen = false }
                )
            }
            ActivityPanel(
                activity = state.activity,
                onTagClick = { tag ->
                    activityOpen = false
                    pendingTag = "#$tag"
                    selectedTab = 1
                },
                onDayClick = { day ->
                    activityOpen = false
                    scope.launch { controller.showDay(day) }
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(LocalDensity.current) { (activityWidthPx / density).dp })
                    .offset { IntOffset((activityOffset.value - activityWidthPx).roundToInt(), 0) }
            )
        }
    }
    imageViewer?.let { target ->
        ImageViewer(
            target = target,
            controller = controller,
            backProgress = { viewerProgress.value },
            onClose = { imageViewer = null }
        )
    }
    if (showComposer) ComposerDialog(state, controller) { showComposer = false }
    if (showAccounts) AccountSheet(state, controller) { showAccounts = false }
    if (showSettings) SettingsSheet(state, controller) { showSettings = false }
}

@Composable
private fun TimelineHeader(
    state: MemosAppState,
    controller: MemosUiController,
    onSearch: () -> Unit,
    onAccounts: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val account = state.activeAccount
        Box(
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Switch account" }
                .clickable(onClick = onAccounts),
            contentAlignment = Alignment.Center
        ) {
            AccountAvatar(account, account?.visibleName ?: "M", Modifier.fillMaxSize(), controller)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account?.siteTitle ?: "Memos", color = TextPrimary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.title3)
            Text(account?.visibleName ?: "@${account?.username ?: "user"}", color = TextSecondary, style = MiuixTheme.textStyles.footnote1)
        }
        MiuixIconButton(onClick = onSearch) {
            MiuixIcon(MiuixIcons.Search, contentDescription = "Open search", tint = TextPrimary)
        }
        MiuixIconButton(onClick = { scope.launch { controller.refreshTimeline() } }) {
            MiuixIcon(MiuixIcons.Refresh, contentDescription = "Refresh", tint = TextPrimary)
        }
    }
}

@Composable
private fun SearchHeader(state: MemosAppState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Search", color = TextPrimary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.title3)
            Text("in ${state.activeAccount?.siteTitle ?: "Memos"}", color = TextSecondary, style = MiuixTheme.textStyles.footnote1)
        }
    }
}

@Composable
private fun TopChrome(
    offsetPx: Float,
    onHeight: (Float) -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offsetPx
                val height = size.height
                alpha = if (height <= 0f) 1f else (1f + offsetPx / height * 1.15f).coerceIn(0f, 1f)
            }
            .onGloballyPositioned { onHeight(it.size.height.toFloat()) }
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Ink.copy(alpha = 0.88f),
                        0.55f to Ink.copy(alpha = 0.42f),
                        1f to Color.Transparent
                    )
                )
        )
        Box(Modifier.statusBarsPadding()) { content() }
    }
}

@Composable
private fun StatusBarVeil() {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier
            .fillMaxWidth()
            .height(top + 18.dp)
            .background(
                Brush.verticalGradient(
                    0f to Ink.copy(alpha = 0.78f),
                    1f to Color.Transparent
                )
            )
    )
}

@Composable
private fun BottomNav(selectedTab: Int, onTabChange: (Int) -> Unit, onSettings: () -> Unit) {
    MiuixNavigationBar(
        color = Ink.copy(alpha = .92f),
        showDivider = false,
        mode = NavigationBarDisplayMode.IconOnly,
        modifier = Modifier.navigationBarsPadding()
    ) {
        MiuixNavigationBarItem(selected = selectedTab == 0, onClick = { onTabChange(0) }, icon = MiuixIcons.Home, label = "Timeline")
        MiuixNavigationBarItem(selected = selectedTab == 1, onClick = { onTabChange(1) }, icon = MiuixIcons.Search, label = "Search")
        MiuixNavigationBarItem(selected = false, onClick = onSettings, icon = MiuixIcons.Settings, label = "Settings")
    }
}

@Composable
private fun TimelineScreen(
    state: MemosAppState,
    controller: MemosUiController,
    modifier: Modifier,
    headerHeightPx: MutableFloatState,
    headerOffsetPx: MutableFloatState,
    onOpen: (Memo) -> Unit,
    onReply: (Memo) -> Unit,
    onOpenImage: (Memo, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atRest = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (atRest && !listState.canScrollForward) return Offset.Zero
                val height = headerHeightPx.floatValue
                if (height <= 0f) return Offset.Zero
                val next = (headerOffsetPx.floatValue + available.y).coerceIn(-height, 0f)
                headerOffsetPx.floatValue = next
                return Offset.Zero
            }
        }
    }
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(Unit) {
        if (state.timeline.isEmpty()) controller.refreshTimeline() else controller.revalidateTimeline()
    }
    LaunchedEffect(controller, listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            latestState.nextPageToken to (latestState.canLoadMore && latestState.error == null &&
                (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4)
        }.collect { (_, shouldLoadMore) ->
            if (shouldLoadMore) controller.loadMore()
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .collect { atTop -> if (atTop) headerOffsetPx.floatValue = 0f }
    }
    val topPad = with(density) {
        headerHeightPx.floatValue.toDp().coerceAtLeast(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().nestedScroll(nestedScroll).background(Ink),
        contentPadding = PaddingValues(top = topPad, bottom = 20.dp)
    ) {
        item { if (state.isLoading && state.timeline.isEmpty()) LoadingLine() }
        state.error?.let { message -> item { Text(message, color = Danger, modifier = Modifier.padding(18.dp)) } }
        state.timelineFilter?.let { filter ->
            item {
                Row(
                    Modifier
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(InkLine)
                        .clickable { scope.launch { controller.showDay(null) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(filter.label, color = Accent, style = MiuixTheme.textStyles.footnote1)
                    MiuixIcon(
                        MiuixIcons.Close,
                        contentDescription = "Clear the day filter",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        if (state.hasNewerMemos) {
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    MiuixSurface(
                        color = InkElevated,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { scope.launch { controller.refreshTimeline() } }
                    ) {
                        Text(
                            "New memos · tap to refresh",
                            color = Accent,
                            style = MiuixTheme.textStyles.footnote1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        state.timelineEmptyMessage?.let { message ->
            item {
                Text(
                    message,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        items(state.timeline, key = { it.name }) { memo ->
            MemoTweet(
                memo,
                state.userProfiles[memo.creator.substringAfterLast('/')],
                controller,
                liked = memo.isLikedBy(state.activeAccount),
                onOpen = { onOpen(memo) },
                onReply = { onReply(memo) },
                onReact = { scope.launch { controller.react(memo, HEART_REACTION) } },
                onOpenImage = { index -> onOpenImage(memo, index) }
            )
        }
        if (state.isLoadingMore) item { LoadingLine() }
    }
}

@Composable
private fun SearchScreen(
    state: MemosAppState,
    controller: MemosUiController,
    modifier: Modifier,
    chromeHeightPx: Float,
    seedQuery: String? = null,
    onSeedConsumed: () -> Unit = {},
    onOpen: (Memo) -> Unit,
    onOpenImage: (Memo, Int) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Memo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // A tag tapped in the activity panel arrives as a seed; the debounce below then
    // runs it like any typed query.
    LaunchedEffect(seedQuery) {
        val seed = seedQuery
        if (!seed.isNullOrBlank()) {
            query = seed
            onSeedConsumed()
        }
    }

    // Debounce: wait for the keyboard to settle, then hit listMemos with a CEL filter.
    LaunchedEffect(query, state.activeAccountId) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            results = emptyList()
            searchError = null
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        try {
            kotlinx.coroutines.delay(400)
            results = controller.search(trimmed)
            searchError = null
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            searchError = e.message ?: "Search failed"
        }
        isSearching = false
    }

    val density = LocalDensity.current
    val topPad = with(density) {
        chromeHeightPx.toDp().coerceAtLeast(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp)
    }
    Column(modifier.fillMaxSize().background(Ink).padding(top = topPad)) {
        MiuixTextField(
            value = query,
            onValueChange = { query = it },
            label = "Search memos",
            useLabelAsPlaceholder = true,
            singleLine = true,
            leadingIcon = {
                MiuixIcon(
                    MiuixIcons.Search,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.padding(start = 14.dp, end = 8.dp).size(20.dp)
                )
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )
        when {
            query.isBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Search your memos by keyword", color = TextSecondary)
                }
            }
            isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingLine() }
            searchError != null -> Text(searchError!!, color = Danger, modifier = Modifier.padding(18.dp))
            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No memos match \"$query\"", color = TextSecondary)
            }
            else -> {
                Text(
                    "${results.size} ${if (results.size == 1) "result" else "results"}",
                    color = TextSecondary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(results, key = { it.name }) { memo ->
                        MemoTweet(
                            memo,
                            state.userProfiles[memo.creator.substringAfterLast('/')],
                            controller,
                            liked = memo.isLikedBy(state.activeAccount),
                            onOpen = { onOpen(memo) },
                            onReply = { onOpen(memo) },
                            onReact = { scope.launch { controller.react(memo, HEART_REACTION) } },
                            onOpenImage = { index -> onOpenImage(memo, index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoTweet(
    memo: Memo,
    user: User?,
    controller: MemosUiController,
    liked: Boolean,
    onOpen: () -> Unit,
    onReply: () -> Unit,
    onReact: () -> Unit,
    onOpenImage: (Int) -> Unit
) {
    val context = LocalContext.current
    val name = user?.visibleName ?: memo.creator.substringAfterLast('/').ifBlank { "Memos" }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            UserAvatar(user, name, Modifier.size(46.dp), controller)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Column(Modifier.clickable(onClick = onOpen)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(6.dp))
                        Text("@${user?.username ?: memo.creator.substringAfterLast('/')}", color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(6.dp))
                        Text("· ${formatMemoTime(memo.createTime)}", color = TextSecondary, maxLines = 1)
                    }
                    Spacer(Modifier.height(5.dp))
                    MarkdownText(memo.content)
                    if (memo.tags.isNotEmpty()) Text(memo.tags.joinToString("  ") { "#$it" }, color = Accent, modifier = Modifier.padding(top = 8.dp))
                }
                if (memo.attachments.isNotEmpty()) MediaRail(memo, controller, onOpenImage)
                TweetActions(
                    memo,
                    liked,
                    onReply = onReply,
                    onCopyLink = { copyMemoLink(context, controller.memoUrl(memo)) },
                    onReact = onReact,
                    onShare = { shareMemo(context, memo, controller.memoUrl(memo)) }
                )
            }
        }
    }
    MiuixHorizontalDivider(color = InkLine, thickness = 1.dp)
}

@Composable
private fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    // Read the palette once here; `markdownInline` stays a pure function.
    val accent = Accent
    val codeBackground = InkElevated
    val styled = { input: String -> markdownInline(input, accent, codeBackground) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        markdown.split('\n').forEach { line ->
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("### ") -> Text(
                    text = styled(line.removePrefix("### ")),
                    color = TextPrimary,
                    style = MiuixTheme.textStyles.headline1.copy(fontWeight = FontWeight.Bold)
                )
                line.startsWith("## ") -> Text(
                    text = styled(line.removePrefix("## ")),
                    color = TextPrimary,
                    style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold)
                )
                line.startsWith("# ") -> Text(
                    text = styled(line.removePrefix("# ")),
                    color = TextPrimary,
                    style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold)
                )
                line.startsWith("- ") || line.startsWith("* ") -> Text(
                    text = styled("• ${line.drop(2)}"),
                    color = TextPrimary,
                    style = MiuixTheme.textStyles.paragraph
                )
                line.startsWith("> ") -> Text(
                    text = styled(line.removePrefix("> ")),
                    color = TextSecondary,
                    style = MiuixTheme.textStyles.paragraph.copy(fontStyle = FontStyle.Italic)
                )
                else -> Text(
                    text = styled(line),
                    color = TextPrimary,
                    style = MiuixTheme.textStyles.paragraph
                )
            }
        }
    }
}

private fun markdownInline(input: String, accent: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
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
                    withStyle(SpanStyle(color = accent, background = codeBackground)) {
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
                    withStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)) {
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
private fun MediaRail(memo: Memo, controller: MemosUiController, onOpenImage: (Int) -> Unit) {
    val images = memo.attachments.filter { it.isImage }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        items(images.size) { index ->
            val attachment = images[index]
            val bytes by produceState<ByteArray?>(null, attachment.name) { value = controller.attachmentBytes(attachment, thumbnail = true) }
            AsyncImage(
                model = bytes,
                contentDescription = attachment.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 260.dp, height = 210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(InkElevated)
                    .clickable { onOpenImage(index) }
            )
        }
    }
}

@Composable
private fun TweetActions(
    memo: Memo,
    liked: Boolean,
    onReply: () -> Unit,
    onCopyLink: () -> Unit,
    onReact: () -> Unit,
    onShare: () -> Unit
) {
    val comments = memo.relations.count { it.type.name == "COMMENT" }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Action(MiuixIcons.Reply, "Reply", comments.takeIf { it > 0 }?.toString().orEmpty(), onClick = onReply)
        Action(MiuixIcons.Link, "Copy link", "", onClick = onCopyLink)
        Action(
            icon = if (liked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
            description = "Like",
            count = memo.reactions.size.takeIf { it > 0 }?.toString().orEmpty(),
            active = liked,
            onClick = onReact
        )
        Action(MiuixIcons.Share, "Share", "", onClick = onShare)
    }
}

@Composable
private fun Action(
    icon: ImageVector,
    description: String,
    count: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (active) Accent else TextSecondary
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixIcon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
        if (count.isNotBlank()) Text("  $count", color = tint, style = MiuixTheme.textStyles.footnote2)
    }
}

@Composable
private fun ComposerDialog(state: MemosAppState, controller: MemosUiController, onDismiss: () -> Unit) {
    var editor by remember { mutableStateOf(TextFieldValue()) }
    var showPreview by remember { mutableStateOf(false) }
    var visibility by rememberSaveable { mutableStateOf(Visibility.PRIVATE.name) }
    var attachments by remember { mutableStateOf(emptyList<PendingAttachment>()) }
    var postError by remember { mutableStateOf<String?>(null) }
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
        onDismissRequest = { if (!state.isPublishing) onDismiss() }
    ) {
        Column(Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MarkdownModeToggle(showPreview = showPreview, onPreviewChange = { showPreview = it })
                if (showPreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 420.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(EditorSurface)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        if (editor.text.isBlank()) {
                            Text("Nothing to preview", color = TextSecondary)
                        } else {
                            MarkdownText(editor.text)
                        }
                    }
                } else {
                    // Keep the draft stable while its snapshot is being published.
                    MarkdownEditor(editor, Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 420.dp)) {
                        if (!state.isPublishing) editor = it
                    }
                    MarkdownToolbar(editor) { if (!state.isPublishing) editor = it }
                }

                VisibilityPicker(visibility) { if (!state.isPublishing) visibility = it }

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
                                    onClick = { if (!state.isPublishing) attachments = attachments.filter { it != attachment } },
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
                    modifier = Modifier.clickable(enabled = !state.isPublishing) { picker.launch("*/*") }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiuixIcon(MiuixIcons.Photos, contentDescription = "Add attachments", tint = Accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Add attachments", color = Accent)
                }
            }
            postError?.let { Text(it, color = Danger) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !state.isPublishing)
                MiuixTextButton(
                    text = if (state.isPublishing) "Posting" else "Post",
                    enabled = (editor.text.isNotBlank() || attachments.isNotEmpty()) && !state.isPublishing,
                    onClick = {
                        postError = null
                        scope.launch {
                            try {
                                controller.publish(
                                    editor.text,
                                    visibility = Visibility.valueOf(visibility),
                                    pendingAttachments = attachments
                                )
                                onDismiss()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                postError = e.message ?: "Publish failed. Your draft has been kept."
                            }
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
private fun VisibilityPicker(selected: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Visibility.PRIVATE to "Private",
            Visibility.PROTECTED to "Workspace",
            Visibility.PUBLIC to "Public"
        ).forEach { (value, label) ->
            MarkdownModeButton(label, selected == value.name) { onChange(value.name) }
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
            .background(EditorSurface)
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
private fun SettingsSheet(state: MemosAppState, controller: MemosUiController, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<InstanceSetting?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }

    // Only expose fields supported by the current instance settings API.
    var instanceTitle by remember { mutableStateOf("") }
    var instanceDescription by remember { mutableStateOf("") }
    var instanceLogoUrl by remember { mutableStateOf("") }
    var disallowRegistration by remember { mutableStateOf(false) }
    var disallowPasswordAuth by remember { mutableStateOf(false) }
    var maxUploadSizeMiB by remember { mutableStateOf("0") }
    var disallowChangeUsername by remember { mutableStateOf(false) }
    var disallowChangeNickname by remember { mutableStateOf(false) }
    var uploadPicker by remember { mutableStateOf(false) }
    var themePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.activeAccountId) {
        settings = null
        loadError = null
        try {
            val loaded = controller.loadInstanceSettings()
            settings = loaded
            instanceTitle = loaded.generalSetting?.customProfile?.title.orEmpty()
            instanceDescription = loaded.generalSetting?.customProfile?.description.orEmpty()
            instanceLogoUrl = loaded.generalSetting?.customProfile?.logoUrl.orEmpty()
            disallowRegistration = loaded.generalSetting?.disallowUserRegistration ?: false
            disallowPasswordAuth = loaded.generalSetting?.disallowPasswordAuth ?: false
            maxUploadSizeMiB = (loaded.storageSetting?.uploadSizeLimitMb ?: 0L).toString()
            disallowChangeUsername = loaded.generalSetting?.disallowChangeUsername ?: false
            disallowChangeNickname = loaded.generalSetting?.disallowChangeNickname ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message ?: "Failed to load settings"
        }
    }

    WindowDialog(
        show = true,
        title = "Settings",
        backgroundColor = InkElevated,
        onDismissRequest = { if (!saving) onDismiss() }
    ) {
        Column(
            Modifier.imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsSection("Appearance")
            SettingsOptionRow(
                label = "App theme",
                valueLabel = themeModeLabel(state.themeMode),
                onClick = { themePicker = true }
            )
            when {
                loadError != null -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(loadError!!, color = Danger)
                    MiuixTextButton(text = "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                }
                settings == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    InfiniteProgressIndicator(color = Accent, size = 26.dp)
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsSection("General")
                    SettingsTextField("Instance title", instanceTitle) { instanceTitle = it }
                    SettingsTextField("Description", instanceDescription) { instanceDescription = it }
                    SettingsTextField("Logo URL", instanceLogoUrl) { instanceLogoUrl = it }
                    SettingsSwitch("Disallow user registration", disallowRegistration) { disallowRegistration = it }
                    SettingsSwitch("Disallow password sign-in", disallowPasswordAuth) { disallowPasswordAuth = it }
                    SettingsSwitch("Disallow changing username", disallowChangeUsername) { disallowChangeUsername = it }
                    SettingsSwitch("Disallow changing nickname", disallowChangeNickname) { disallowChangeNickname = it }

                    SettingsSection("Storage")
                    SettingsOptionRow(
                        label = "Upload size limit",
                        valueLabel = uploadSizeLabel(maxUploadSizeMiB),
                        onClick = { uploadPicker = true }
                    )

                    saveError?.let { Text(it, color = Danger) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiuixTextButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !saving)
                        MiuixTextButton(
                            text = if (saving) "Saving" else "Save",
                            enabled = !saving,
                            onClick = { showConfirm = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }

    if (showConfirm) {
        WindowDialog(
            show = true,
            title = "Apply to instance?",
            backgroundColor = InkElevated,
            onDismissRequest = { showConfirm = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("These changes apply to ${state.activeAccount?.siteTitle ?: "the instance"} and affect every user.", color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixTextButton(text = "Cancel", onClick = { showConfirm = false }, modifier = Modifier.weight(1f))
                    MiuixTextButton(
                        text = "Apply",
                        onClick = {
                            showConfirm = false
                            saving = true
                            saveError = null
                            scope.launch {
                                try {
                                    // The API merges these editable fields into the current server resource.
                                    controller.saveInstanceSettings(
                                        general = (settings?.generalSetting ?: GeneralSetting()).copy(
                                            customProfile = (settings?.generalSetting?.customProfile ?: CustomProfile()).copy(
                                                title = instanceTitle.trim(),
                                                description = instanceDescription.trim(),
                                                logoUrl = instanceLogoUrl.trim()
                                            ),
                                            disallowUserRegistration = disallowRegistration,
                                            disallowPasswordAuth = disallowPasswordAuth,
                                            disallowChangeUsername = disallowChangeUsername,
                                            disallowChangeNickname = disallowChangeNickname
                                        ),
                                        storage = StorageSetting(uploadSizeLimitMb = maxUploadSizeMiB.toLong())
                                    )
                                    onDismiss()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    saveError = e.message ?: "Failed to save settings"
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary() 
                    )
                }
            }
        }
    }
    if (themePicker) {
        SettingsOptionPicker(
            title = "App theme",
            options = ThemeModeOptions,
            selected = themeModeValue(state.themeMode),
            onSelect = { controller.setThemeMode(canonicalThemeMode(it)) },
            onDismiss = { themePicker = false }
        )
    }
    if (uploadPicker) {
        SettingsOptionPicker(
            title = "Upload size limit",
            options = uploadSizeOptionsFor(maxUploadSizeMiB),
            selected = maxUploadSizeMiB,
            onSelect = { maxUploadSizeMiB = it },
            onDismiss = { uploadPicker = false }
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, color = Accent, fontWeight = FontWeight.SemiBold, style = MiuixTheme.textStyles.footnote1)
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    MiuixTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = singleLine,
        // A one-line field squashes anything longer than its width into a strip; the
        // announcement is a paragraph, so it gets a box that wraps and scrolls.
        modifier = Modifier
            .fillMaxWidth()
            .then(if (singleLine) Modifier else Modifier.heightIn(min = 96.dp))
    )
}

@Composable
private fun SettingsOptionRow(label: String, valueLabel: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(valueLabel, color = TextSecondary)
    }
}

@Composable
private fun SettingsOptionPicker(
    title: String,
    options: List<SettingOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = title,
        backgroundColor = InkElevated,
        onDismissRequest = onDismiss
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val active = option.value == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable {
                            onSelect(option.value)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        option.label,
                        color = if (active) Accent else TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (active) {
                        MiuixIcon(MiuixIcons.Ok, contentDescription = "Selected", tint = Accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private sealed interface AccountSheetMode {
    data object List : AccountSheetMode
    data object Add : AccountSheetMode
    data class Actions(val account: MemosAccount) : AccountSheetMode
}

@Composable
private fun AccountSheet(state: MemosAppState, controller: MemosUiController, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf<AccountSheetMode>(AccountSheetMode.List) }
    var renaming by remember { mutableStateOf<MemosAccount?>(null) }
    var confirmingRemove by remember { mutableStateOf<MemosAccount?>(null) }
    val scope = rememberCoroutineScope()
    val actionsAccount = (mode as? AccountSheetMode.Actions)?.account
    WindowBottomSheet(
        show = true,
        title = when (mode) {
            AccountSheetMode.List -> "Accounts"
            AccountSheetMode.Add -> "Add account"
            is AccountSheetMode.Actions -> actionsAccount?.siteTitle ?: "Account"
        },
        backgroundColor = InkElevated,
        onDismissRequest = onDismiss,
        startAction = if (mode is AccountSheetMode.List) null else {
            {
                MiuixIconButton(onClick = { mode = AccountSheetMode.List }) {
                    MiuixIcon(MiuixIcons.Back, contentDescription = "Back to accounts", tint = TextPrimary)
                }
            }
        },
        endAction = if (mode is AccountSheetMode.List) {
            {
                MiuixIconButton(onClick = { mode = AccountSheetMode.Add }) {
                    MiuixIcon(MiuixIcons.Add, contentDescription = "Add account", tint = TextPrimary)
                }
            }
        } else null
    ) {
        // miuix only ime-pads the sheet; lift content above the system gesture bar.
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
        when (val current = mode) {
            AccountSheetMode.List -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.orderedAccounts.forEach { account ->
                    AccountRow(
                        account = account,
                        selected = account.id == state.activeAccountId,
                        controller = controller,
                        onOpen = {
                            scope.launch { controller.selectAccount(account.id) }
                            onDismiss()
                        },
                        onActions = { mode = AccountSheetMode.Actions(account) }
                    )
                }
                Text(
                    "Hold an account to pin, rename, or remove it.",
                    color = TextSecondary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Spacer(Modifier.height(6.dp))
                MiuixTextButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
            AccountSheetMode.Add -> AddAccountForm(state, controller) { mode = AccountSheetMode.List }
            is AccountSheetMode.Actions -> {
                // The stored version can be stale after an instance upgrade, so ask
                // again while the sheet is open; the row below reads what comes back.
                LaunchedEffect(current.account.id) { controller.refreshInstanceVersion(current.account.id) }
                val account = state.accounts.firstOrNull { it.id == current.account.id } ?: current.account
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Which Memos this account talks to; the App is built against an
                    // API that moves between releases, so it is worth showing.
                    Text(
                        if (account.instanceVersion.isBlank()) "Memos version unknown"
                        else "Memos ${account.instanceVersion}",
                        color = TextSecondary,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    AccountActionRow(
                        icon = if (current.account.pinned) MiuixIcons.Unpin else MiuixIcons.Pin,
                        label = if (current.account.pinned) "Unpin" else "Pin to top",
                        onClick = {
                            scope.launch { controller.setAccountPinned(current.account.id, !current.account.pinned) }
                            mode = AccountSheetMode.List
                        }
                    )
                    AccountActionRow(
                        icon = MiuixIcons.Rename,
                        label = "Rename",
                        onClick = {
                            renaming = current.account
                            mode = AccountSheetMode.List
                        }
                    )
                    AccountActionRow(
                        icon = MiuixIcons.Delete,
                        label = "Remove account",
                        destructive = true,
                        onClick = {
                            confirmingRemove = current.account
                            mode = AccountSheetMode.List
                        }
                    )
                }
            }
        }
        }
    }
    renaming?.let { account ->
        RenameAccountDialog(account, controller) { renaming = null }
    }
    confirmingRemove?.let { account ->
        RemoveAccountDialog(
            account = account,
            onCancel = { confirmingRemove = null },
            onConfirm = {
                confirmingRemove = null
                scope.launch { controller.removeAccount(account.id) }
            }
        )
    }
}

@Composable
private fun AccountActionRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (destructive) Danger else TextPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixIcon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AccountRow(
    account: MemosAccount,
    selected: Boolean,
    controller: MemosUiController,
    onOpen: () -> Unit,
    onActions: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(account.id) {
                detectTapGestures(onTap = { onOpen() }, onLongPress = { onActions() })
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountAvatar(account, account.siteTitle, Modifier.size(42.dp), controller)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account.siteTitle, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(account.visibleName, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) MiuixIcon(MiuixIcons.Ok, contentDescription = "Selected", tint = Accent)
    }
}

@Composable
private fun AddAccountForm(state: MemosAppState, controller: MemosUiController, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var instance by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add another Memos instance to switch between accounts.", color = TextSecondary)
        DarkField(instance, { instance = it }, "Memos instance", "memos.example.com")
        DarkField(username, { username = it }, "Username")
        DarkField(password, { password = it }, "Password", secure = true)
        state.error?.let { Text(it, color = Danger) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiuixTextButton(text = "Cancel", onClick = onBack, modifier = Modifier.weight(1f), enabled = !submitting)
            MiuixTextButton(
                text = if (submitting) "Signing in" else "Add",
                enabled = instance.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !submitting,
                onClick = {
                    submitting = true
                    scope.launch {
                        controller.addAccount(instance, username, password)
                        submitting = false
                        if (controller.state.value.error == null) onBack()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun RenameAccountDialog(account: MemosAccount, controller: MemosUiController, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember(account.id) { mutableStateOf(account.visibleName) }
    WindowDialog(
        show = true,
        title = "Rename account",
        backgroundColor = InkElevated,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                account.instanceUrl,
                color = TextSecondary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SettingsTextField("Display name", name) { name = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                MiuixTextButton(
                    text = "Save",
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch { controller.renameAccount(account.id, name) }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun RemoveAccountDialog(account: MemosAccount, onCancel: () -> Unit, onConfirm: () -> Unit) {
    WindowDialog(
        show = true,
        title = "Remove account?",
        backgroundColor = InkElevated,
        onDismissRequest = onCancel
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "${account.siteTitle} · ${account.visibleName} will be removed from this device. Memos stored on the server are not deleted.",
                color = TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                MiuixTextButton(
                    text = "Remove",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    update: AvailableUpdate,
    download: UpdateDownload?,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {
    WindowDialog(
        show = true,
        title = "Update to ${update.version}",
        backgroundColor = InkElevated,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            update.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            update.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(EditorSurface)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    MarkdownText(notes)
                }
            }
            Text(
                "${update.version} \u00b7 ${formatByteSize(update.sizeBytes)}",
                color = TextSecondary
            )
            val running = download?.takeIf { it.version == update.version }
            if (running != null) {
                // The download happens in this dialog; the system installer takes over
                // once the bytes are there.
                Text("Downloading\u2026 ${formatProgress(running)}", color = TextSecondary)
                MiuixTextButton(text = "Cancel", onClick = onCancelDownload, modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixTextButton(text = "Later", onClick = onDismiss, modifier = Modifier.weight(1f))
                    MiuixTextButton(
                        text = "Update now",
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
                MiuixTextButton(
                    text = "Skip this version",
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Writes the downloaded APK where the installer can read it and hands it over.
 *
 * The system installer owns the decision to install, so the app itself needs no
 * installation rights — only the FileProvider grant below.
 */
private fun installUpdate(context: Context, bytes: ByteArray, downloadUrl: String) {
    val fileName = downloadUrl.substringAfterLast('/').ifBlank { "bema-update.apk" }
    val file = File(File(context.cacheDir, "updates").apply { mkdirs() }, fileName)
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun formatProgress(download: UpdateDownload): String {
    val total = download.totalBytes
    if (total == null || total <= 0) return formatByteSize(download.bytesRead)
    val percent = (download.bytesRead * 100 / total).coerceIn(0, 100)
    return "$percent%"
}

private fun formatByteSize(bytes: Long): String = when {
    bytes <= 0 -> "unknown size"
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** Fraction of the width the activity panel covers when it is open. */
private const val ACTIVITY_PANEL_FRACTION = 0.86f

/**
 * The activity panel a sideways drag reveals: the month's memos as a shaded grid,
 * then the tag cloud. Both come from one `GetUserStats` response.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityPanel(
    activity: ActivityStats?,
    onTagClick: (String) -> Unit,
    onDayClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var monthShift by remember { mutableIntStateOf(0) }
    val today = remember { CalendarDays.dateOf(todayEpochDay()) }
    val shown = CalendarDays.shiftMonth(today.year, today.month, monthShift)
    val year = shown.year
    val month = shown.month

    Box(modifier.background(InkElevated)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiuixIconButton(onClick = { monthShift-- }) {
                    MiuixIcon(MiuixIcons.ChevronBackward, contentDescription = "Previous month", tint = TextPrimary)
                }
                Text("$year-$month", color = TextPrimary, style = MiuixTheme.textStyles.headline1)
                MiuixIconButton(onClick = { monthShift++ }) {
                    MiuixIcon(MiuixIcons.ChevronForward, contentDescription = "Next month", tint = TextPrimary)
                }
            }

            MonthHeatmap(year, month, activity?.dayCounts.orEmpty(), onDayClick)

            Text("Tags", color = TextPrimary, style = MiuixTheme.textStyles.headline1)
            val tags = activity?.tagCounts.orEmpty()
            if (tags.isEmpty()) {
                Text("No tags yet", color = TextSecondary, style = MiuixTheme.textStyles.footnote1)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { entry ->
                        val tag = entry.tag
                        val count = entry.count
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(InkLine)
                                .clickable { onTagClick(tag) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("#$tag", color = Accent, style = MiuixTheme.textStyles.footnote1)
                            Text(count.toString(), color = TextSecondary, style = MiuixTheme.textStyles.footnote2)
                        }
                    }
                }
            }
        }
    }
}

/** One cell per day of [month], shaded by how many memos that day holds. */
@Composable
private fun MonthHeatmap(year: Int, month: Int, dayCounts: Map<Long, Int>, onDayClick: (Long) -> Unit) {
    val lead = CalendarDays.weekdayOf(CalendarDays.epochDay(year, month, 1))
    val days = CalendarDays.daysInMonth(year, month)
    val busiest = (dayCounts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val today = todayEpochDay()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                Text(
                    label,
                    color = TextSecondary,
                    style = MiuixTheme.textStyles.footnote2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        repeat((lead + days + 6) / 7) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { column ->
                    val dayNumber = row * 7 + column - lead + 1
                    val day = if (dayNumber in 1..days) CalendarDays.epochDay(year, month, dayNumber) else null
                    val count = day?.let { dayCounts[it] ?: 0 }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (count == null) Color.Transparent else heatColour(count, busiest))
                            .then(if (day == null) Modifier else Modifier.clickable { onDayClick(day) }),
                        contentAlignment = Alignment.Center
                    ) {
                        if (count != null) {
                            Text(
                                dayNumber.toString(),
                                color = if (day == today) Accent else TextPrimary,
                                style = MiuixTheme.textStyles.footnote2
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Quiet days stay flat; busy ones climb toward the accent. */
@Composable
private fun heatColour(count: Int, busiest: Int): Color {
    if (count <= 0) return InkLine
    val step = (count.toFloat() / busiest).coerceIn(0.25f, 1f)
    return Accent.copy(alpha = 0.25f + 0.75f * step)
}

@Composable
private fun MemoDetailScreen(
    state: MemosAppState,
    controller: MemosUiController,
    modifier: Modifier,
    backProgress: () -> Float,
    startComment: Boolean,
    onCommentConsumed: () -> Unit,
    onOpenImage: (Memo, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var comment by remember { mutableStateOf(TextFieldValue()) }
    var showCommentPreview by remember { mutableStateOf(false) }
    var showCommentBox by remember { mutableStateOf(startComment) }
    var sendingComment by remember { mutableStateOf(false) }
    var commentError by remember { mutableStateOf<String?>(null) }
    val commentProgress = remember { Animatable(0f) }
    val memo = state.selectedMemo ?: return
    LaunchedEffect(startComment) {
        if (startComment) {
            showCommentBox = true
            onCommentConsumed()
        }
    }
    PredictiveBackHandler(enabled = showCommentBox) { events ->
        try {
            events.collect { commentProgress.snapTo(it.progress) }
            showCommentBox = false
        } finally {
            commentProgress.snapTo(0f)
        }
    }
    
    var detailChromeHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val topPad = with(density) {
        detailChromeHeightPx.toDp().coerceAtLeast(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp)
    }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { translationX = backProgress() * size.width }
    ) {
        LazyColumn(
            Modifier.fillMaxSize().background(Ink),
            contentPadding = PaddingValues(top = topPad, bottom = if (showCommentBox) 300.dp else 28.dp)
        ) {
            item {
                MemoTweet(
                    memo,
                    state.userProfiles[memo.creator.substringAfterLast('/')],
                    controller,
                    liked = memo.isLikedBy(state.activeAccount),
                    onOpen = {},
                    onReply = { showCommentBox = true },
                    onReact = { scope.launch { controller.react(memo, HEART_REACTION) } },
                    onOpenImage = { index -> onOpenImage(memo, index) }
                )
            }
            
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
                MemoTweet(
                    reply,
                    state.userProfiles[reply.creator.substringAfterLast('/')],
                    controller,
                    liked = reply.isLikedBy(state.activeAccount),
                    onOpen = {},
                    onReply = { showCommentBox = true },
                    onReact = { scope.launch { controller.react(reply, HEART_REACTION) } },
                    onOpenImage = { index -> onOpenImage(reply, index) }
                )
            }
        }
        TopChrome(offsetPx = 0f, onHeight = { detailChromeHeightPx = it }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixIconButton(onClick = { controller.closeMemo() }) {
                    MiuixIcon(MiuixIcons.Back, contentDescription = "Back", tint = TextPrimary)
                }
                Text("Memo", color = TextPrimary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.title3)
            }
        }
        StatusBarVeil()
        
        if (showCommentBox) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { translationY = commentProgress.value * size.height }
                    .background(InkElevated)
                    .navigationBarsPadding()
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
                            .background(EditorSurface)
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
                commentError?.let { Text(it, color = Danger) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixTextButton(
                        text = "Cancel",
                        enabled = !sendingComment,
                        onClick = {
                            comment = TextFieldValue()
                            showCommentPreview = false
                            showCommentBox = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    MiuixTextButton(
                        text = if (sendingComment) "Sending" else "Reply",
                        enabled = comment.text.isNotBlank() && !sendingComment,
                        onClick = {
                            val body = comment.text
                            sendingComment = true
                            commentError = null
                            scope.launch {
                                try {
                                    controller.comment(body)
                                    if (comment.text == body) {
                                        comment = TextFieldValue()
                                        showCommentPreview = false
                                        showCommentBox = false
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    commentError = e.message ?: "Reply failed. Your draft has been kept."
                                } finally {
                                    sendingComment = false
                                }
                            }
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
                MiuixIcon(MiuixIcons.Reply, contentDescription = "Write a reply", tint = Color.White)
            }
        }
    }
}

@Composable
private fun LoadingLine() {
    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        InfiniteProgressIndicator(color = Accent, size = 26.dp)
    }
}

private data class ImageViewerTarget(val memo: Memo, val startIndex: Int)

private fun copyMemoLink(context: Context, url: String) {
    if (url.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Memo", url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}

private fun shareMemo(context: Context, memo: Memo, url: String) {
    val snippet = memo.content.trim().take(180)
    val text = if (snippet.isBlank()) url else "$snippet\n$url"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
private fun ImageViewer(
    target: ImageViewerTarget,
    controller: MemosUiController,
    backProgress: () -> Float,
    onClose: () -> Unit
) {
    val images = target.memo.attachments.filter { it.isImage }
    if (images.isEmpty()) return
    val start = target.startIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(initialPage = start, pageCount = { images.size })
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = backProgress() * size.width
                alpha = 1f - backProgress() * 0.35f
            }
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val attachment = images[page]
            val bytes by produceState<ByteArray?>(null, attachment.name) {
                value = controller.attachmentBytes(attachment, thumbnail = false)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (bytes == null) InfiniteProgressIndicator(color = Accent, size = 28.dp)
                else AsyncImage(
                    model = bytes,
                    contentDescription = attachment.filename,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        MiuixIconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            MiuixIcon(MiuixIcons.Back, contentDescription = "Close image", tint = Color.White)
        }
        if (images.size > 1) {
            Text(
                "${pagerState.currentPage + 1} / ${images.size}",
                color = Color.White.copy(alpha = 0.8f),
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            )
        }
    }
}
