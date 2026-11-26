package com.ahmed.zaghlolplayer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.ahmed.zaghlolplayer.tv.AppTab
import com.ahmed.zaghlolplayer.tv.AppViewModel
import com.ahmed.zaghlolplayer.tv.PlayKind
import com.ahmed.zaghlolplayer.tv.UiState
import com.ahmed.zaghlolplayer.tv.fmtClock
import kotlinx.coroutines.delay

private val TABS = listOf(
    AppTab.LIVE to "مباشر",
    AppTab.GUIDE to "دليل البرامج",
    AppTab.SERIES to "مسلسلات",
    AppTab.MOVIES to "أفلام",
    AppTab.FAVS to "المفضلة"
)

@Composable
fun TopBar(vm: AppViewModel, state: UiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(ZaghlolColors.Panel, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        ZLogo(44.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "Zaghlol Player",
                color = ZaghlolColors.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(text = "زغلول بلاير", color = ZaghlolColors.Muted, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(24.dp))
        // Tabs
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((tab, label) in TABS) {
                val active = state.tab == tab
                Box(
                    modifier = Modifier
                        .background(
                            if (active) ZaghlolColors.AccentSoft else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            2.dp,
                            if (active) ZaghlolColors.Accent else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .tvClickable { vm.setTab(tab) }
                ) {
                    Text(
                        text = label,
                        color = if (active) ZaghlolColors.Accent else ZaghlolColors.Text,
                        fontSize = 19.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        SearchField(
            value = state.search,
            onValueChange = { vm.setSearch(it) },
            placeholder = "ابحث عن قناة أو فيلم...",
            modifier = Modifier.width(320.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        GhostButton("تنبيهات") { vm.toast("لا توجد تنبيهات جديدة") }
        Spacer(modifier = Modifier.width(8.dp))
        GhostButton("الإعدادات") { vm.setTab(AppTab.SETTINGS) }
    }
}

@Composable
fun BottomNav(vm: AppViewModel, state: UiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(ZaghlolColors.Panel, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ZLogo(36.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Zaghlol Player",
            color = ZaghlolColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val items = listOf(
                AppTab.LIVE to "الرئيسية",
                AppTab.MOVIES to "الأفلام",
                AppTab.SERIES to "المسلسلات",
                AppTab.FAVS to "المفضلة",
                AppTab.SETTINGS to "الإعدادات"
            )
            for ((tab, label) in items) {
                val active = state.tab == tab
                Box(
                    modifier = Modifier
                        .background(
                            if (active) ZaghlolColors.Accent else Color.Transparent,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .tvClickable { vm.setTab(tab) }
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 27.dp) // overscan-safe margins
    ) {
        if (!state.focusMode) {
            TopBar(vm, state)
            Spacer(modifier = Modifier.height(14.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            when (state.tab) {
                AppTab.LIVE -> LiveView(vm, state)
                AppTab.GUIDE -> GuideScreen(vm, state)
                AppTab.MOVIES -> MoviesScreen(vm, state)
                AppTab.SERIES -> SeriesScreen(vm, state)
                AppTab.FAVS -> FavoritesScreen(vm, state)
                AppTab.SETTINGS -> SettingsScreen(vm, state)
            }
        }
        if (!state.focusMode && state.tab != AppTab.LIVE) {
            Spacer(modifier = Modifier.height(14.dp))
            BottomNav(vm, state)
        }
        // Toast
        if (state.toast.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.toast,
                    color = Color.White,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .background(Color(0xCC000000), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
    // Dialogs
    state.movieDialog?.let { vod ->
        MovieDialog(vm, state, vod)
    }
    state.serieDialog?.let { serie ->
        SeriesDialog(vm, state, serie)
    }
}

@Composable
fun LiveView(vm: AppViewModel, state: UiState) {
    val data = state.data
    // D-pad: put the focus in the sidebar the moment the data arrives, so the
    // remote's arrows move through categories and channels right away. Until
    // v1.0.2 nothing ever requested focus here, so DOWN from the top bar
    // landed on the big video box (or nowhere) and the lists never moved.
    val catFocus = remember { FocusRequester() }
    LaunchedEffect(data) {
        if (data != null && data.channels.isNotEmpty()) {
            delay(500)
            catFocus.requestFocus()
        }
    }
    Row(modifier = Modifier.fillMaxSize()) {
        if (!state.focusMode) {
            // ---- Sidebar: categories, then channels ----
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .fillMaxHeight()
                    .background(ZaghlolColors.Panel, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "الأقسام",
                    color = ZaghlolColors.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
                val cats = data?.liveCategories ?: emptyList()
                val total = data?.channels?.size ?: 0
                LazyColumn(modifier = Modifier.weight(0.38f)) {
                    item {
                        CategoryItem(
                            "الكل", total, state.currentCat == "all",
                            modifier = Modifier.focusRequester(catFocus)
                        ) { vm.selectCategory("all") }
                    }
                    items(cats) { cat ->
                        CategoryItem(cat.name, cat.count, state.currentCat == cat.id) {
                            vm.selectCategory(cat.id)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val channels = vm.visibleChannels()
                Text(
                    text = "القنوات (${channels.size})",
                    color = ZaghlolColors.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
                if (channels.isEmpty()) {
                    EmptyState("📺", "لا توجد قنوات مطابقة")
                } else {
                    LazyColumn(modifier = Modifier.weight(0.62f)) {
                        items(channels) { ch ->
                            ChannelRow(
                                channel = ch,
                                active = state.channel?.id == ch.id && state.playKind == PlayKind.LIVE,
                                isFavorite = state.favorites.contains("live:${ch.id}"),
                                onClick = { vm.playChannel(ch) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        // ---- Video column ----
        Column(modifier = Modifier.weight(1f)) {
            VideoBox(vm, state, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(10.dp))
            InfoBar(state)
            Spacer(modifier = Modifier.height(10.dp))
            NowBar(vm, state)
        }
    }
}

@Composable
fun VideoBox(vm: AppViewModel, state: UiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            isFocusable = false
            isFocusableInTouchMode = false
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }
    DisposableEffect(Unit) {
        playerView.player = vm.tvPlayer.player
        onDispose { playerView.player = null }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .tvFocusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        vm.seekBy(-10_000); true
                    }
                    Key.DirectionRight -> {
                        vm.seekBy(10_000); true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )
        // Idle overlay
        if (state.channel == null && state.vod == null && state.episode == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "▶", color = Color.White.copy(alpha = 0.7f), fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "اختر قناة للمشاهدة",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "أو اضغط تشغيل لعرض أول قناة",
                        color = ZaghlolColors.Muted,
                        fontSize = 17.sp
                    )
                }
            }
        }
        // Playback error banner
        if (state.playError != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(Color(0xCC7F1D1D), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.playError,
                        color = Color.White,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                    GhostButton("✕") { vm.clearPlayError() }
                }
            }
        }
    }
}

@Composable
fun InfoBar(state: UiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(ZaghlolColors.Panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (state.playKind == PlayKind.LIVE) {
            Box(
                modifier = Modifier
                    .background(ZaghlolColors.Live, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(text = "● مباشر", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.nowTitle.ifEmpty { "—" },
                color = ZaghlolColors.Text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.nowTime.isNotEmpty()) {
                Text(text = state.nowTime, color = ZaghlolColors.Muted, fontSize = 15.sp)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "بعدين: ${state.nextTitle.ifEmpty { "—" }}",
            color = ZaghlolColors.Muted,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun NowBar(vm: AppViewModel, state: UiState) {
    var muted by remember { mutableStateOf(false) }
    val isLive = state.playKind == PlayKind.LIVE
    val title = when {
        state.channel != null && isLive -> state.channel.name
        state.vod != null -> state.vod.name
        state.episode != null -> state.episode.title
        else -> "—"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(ZaghlolColors.Panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Play / pause
        Box(
            modifier = Modifier
                .background(ZaghlolColors.Accent, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .tvClickable { vm.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.isPlaying) "⏸" else "▶",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        // Progress + time
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ZaghlolColors.Text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isLive) "مباشر" else fmtClock(state.positionMs),
                    color = ZaghlolColors.Muted,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ZaghlolColors.Accent,
                    trackColor = ZaghlolColors.Border
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLive) state.nowTime else fmtClock(state.durationMs),
                    color = ZaghlolColors.Muted,
                    fontSize = 15.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        GhostButton(if (muted) "صوت ✕" else "صوت") {
            muted = !muted
            vm.tvPlayer.player.volume = if (muted) 0f else 1f
        }
        Spacer(modifier = Modifier.width(8.dp))
        GhostButton(if (state.focusMode) "خروج من الملء" else "ملء الشاشة") {
            vm.setFocusMode(!state.focusMode)
        }
    }
}
