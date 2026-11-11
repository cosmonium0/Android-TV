package com.example.tvhome.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.tvhome.LauncherViewModel
import com.example.tvhome.data.AppInfo
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Base = Color(0xFF0B0F17)
private val SidePadding = 56.dp

@Composable
fun HomeScreen(vm: LauncherViewModel, onLaunch: (Intent) -> Unit) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsState()
    val showHidden by vm.showHidden.collectAsState()
    val homePresses by vm.homePresses.collectAsState()

    val visible = remember(apps, showHidden) {
        if (showHidden) apps else apps.filter { !it.hidden }
    }
    val favorites = visible.filter { it.pinned }
    val others = visible.filter { !it.pinned }
    val hiddenCount = apps.count { it.hidden }

    var focusedApp by remember { mutableStateOf<AppInfo?>(null) }
    var menuFor by remember { mutableStateOf<AppInfo?>(null) }

    val accent by animateColorAsState(
        targetValue = focusedApp?.accent ?: Color(0xFF1B2A41),
        animationSpec = tween(700),
        label = "accent",
    )

    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }

    // On first load, and whenever Home/Back is pressed: scroll to the top and focus the first app.
    LaunchedEffect(homePresses, visible.isNotEmpty()) {
        if (visible.isNotEmpty()) {
            listState.scrollToItem(0)
            delay(120)
            try {
                firstFocus.requestFocus()
            } catch (_: Exception) {
                // Row not attached yet; the next recomposition will retry.
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(lerp(Base, accent, 0.45f), Base)))
    ) {
        Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
            TopBar(
                hiddenCount = hiddenCount,
                showHidden = showHidden,
                onToggleHidden = vm::toggleShowHidden,
                onSettings = {
                    onLaunch(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )

            Hero(
                app = focusedApp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = SidePadding),
            )

            val firstApp = (favorites.firstOrNull() ?: others.firstOrNull())?.packageName

            LazyColumn(
                state = listState,
                modifier = Modifier.height(350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (favorites.isNotEmpty()) {
                    item(key = "favorites") {
                        AppRow("Favorites", favorites, firstApp, firstFocus,
                            onFocus = { focusedApp = it },
                            onOpen = { onLaunch(it.launchIntent) },
                            onMenu = { menuFor = it })
                    }
                }
                if (others.isNotEmpty()) {
                    item(key = "all") {
                        AppRow("All Apps", others, firstApp, firstFocus,
                            onFocus = { focusedApp = it },
                            onOpen = { onLaunch(it.launchIntent) },
                            onMenu = { menuFor = it })
                    }
                }
            }
        }

        menuFor?.let { app ->
            val row = if (app.pinned) favorites else others
            val rowPackages = row.map { it.packageName }
            val index = rowPackages.indexOf(app.packageName)
            AppMenu(
                app = app,
                canMoveLeft = index > 0,
                canMoveRight = index in 0 until rowPackages.lastIndex,
                onDismiss = { menuFor = null },
                onMove = { delta -> vm.move(app.packageName, delta, rowPackages) },
                onTogglePin = { vm.togglePin(app.packageName); menuFor = null },
                onToggleHidden = { vm.toggleHidden(app.packageName); menuFor = null },
                onInfo = {
                    menuFor = null
                    onLaunch(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onUninstall = {
                    menuFor = null
                    onLaunch(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
            )
        }
    }
}

@Composable
private fun TopBar(
    hiddenCount: Int,
    showHidden: Boolean,
    onToggleHidden: () -> Unit,
    onSettings: () -> Unit,
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(15_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SidePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                now.format(DateTimeFormatter.ofPattern("h:mm a")),
                color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp,
            )
        }
        if (hiddenCount > 0) {
            TvButton(
                text = if (showHidden) "Hide hidden apps" else "Hidden apps ($hiddenCount)",
                onClick = onToggleHidden,
            )
            Spacer(Modifier.width(12.dp))
        }
        TvButton(text = "Settings", onClick = onSettings)
    }
}

@Composable
private fun Hero(app: AppInfo?, modifier: Modifier) {
    Crossfade(targetState = app, modifier = modifier, animationSpec = tween(350), label = "hero") { a ->
        if (a == null) {
            Box(Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .height(190.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(a.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (a.banner != null) {
                        Image(a.banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Image(a.icon, null, Modifier.size(96.dp))
                    }
                }
                Spacer(Modifier.width(32.dp))
                Column {
                    Text(a.label, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Press OK to open  ·  Hold OK to rearrange",
                        color = Color.White.copy(alpha = 0.65f), fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    title: String,
    apps: List<AppInfo>,
    firstPackage: String?,
    firstRequester: FocusRequester,
    onFocus: (AppInfo) -> Unit,
    onOpen: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
) {
    Column {
        Text(
            title,
            color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = SidePadding),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = SidePadding, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { _, app ->
                AppCard(
                    app = app,
                    modifier = if (app.packageName == firstPackage) Modifier.focusRequester(firstRequester) else Modifier,
                    onFocus = { onFocus(app) },
                    onClick = { onOpen(app) },
                    onLongClick = { onMenu(app) },
                )
            }
        }
    }
}

/** tvOS-style card: lifts and scales when focused, label fades in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCard(
    app: AppInfo,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "scale",
    )
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .width(200.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused) 48f else 0f
                this.shape = shape
                clip = true
                alpha = if (app.hidden) 0.55f else 1f
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .border(if (focused) 3.dp else 0.dp, Color.White, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (app.banner != null) {
            Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(app.accent, lerp(app.accent, Color.Black, 0.6f)))),
                contentAlignment = Alignment.Center,
            ) {
                Image(app.icon, app.label, Modifier.size(64.dp))
            }
        }
        if (app.hidden) {
            Text(
                "Hidden", color = Color.White, fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        AnimatedVisibility(visible = focused, modifier = Modifier.align(Alignment.BottomStart)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(app.label, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AppMenu(
    app: AppInfo,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit,
    onTogglePin: () -> Unit,
    onToggleHidden: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        try {
            first.requestFocus()
        } catch (_: Exception) {
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF01C2230))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(app.label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            TvButton(
                if (app.pinned) "Remove from Favorites" else "Add to Favorites",
                Modifier.fillMaxWidth().focusRequester(first),
                onClick = onTogglePin,
            )
            TvButton("Move left", Modifier.fillMaxWidth(), enabled = canMoveLeft) { onMove(-1) }
            TvButton("Move right", Modifier.fillMaxWidth(), enabled = canMoveRight) { onMove(1) }
            TvButton(if (app.hidden) "Unhide" else "Hide", Modifier.fillMaxWidth(), onClick = onToggleHidden)
            TvButton("App info", Modifier.fillMaxWidth(), onClick = onInfo)
            TvButton("Uninstall", Modifier.fillMaxWidth(), onClick = onUninstall)
        }
    }
}

@Composable
private fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.12f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            fontSize = 18.sp,
            color = when {
                focused -> Color.Black
                enabled -> Color.White
                else -> Color.White.copy(alpha = 0.35f)
            },
        )
    }
}
