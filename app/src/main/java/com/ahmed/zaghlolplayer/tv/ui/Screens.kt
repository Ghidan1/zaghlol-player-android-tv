package com.ahmed.zaghlolplayer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import com.ahmed.zaghlolplayer.tv.AppViewModel
import com.ahmed.zaghlolplayer.tv.UiState
import com.ahmed.zaghlolplayer.tv.fmtTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun GuideScreen(vm: AppViewModel, state: UiState) {
    val data = state.data
    val channels = data?.channels ?: emptyList()

    // First entry: load the guide for the first channel, today.
    LaunchedEffect(data) {
        if (state.guideChannelId.isEmpty() && channels.isNotEmpty()) {
            vm.loadGuide(channels[0].id, 0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "دليل البرامج — 7 أيام",
            color = ZaghlolColors.Text,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "اختر قناة ويوم لعرض البرامج، وشغّل البرنامج الحالي من البداية (Xtream فقط).",
            color = ZaghlolColors.Muted,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Channel selector
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(channels) { ch ->
                Chip(ch.name, state.guideChannelId == ch.id) { vm.loadGuide(ch.id, state.guideDay) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Day selector (today + 6 days)
        val dayFmt = SimpleDateFormat("EEEE d/M", Locale("ar"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items((0..6).toList()) { day ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, day)
                val label = when (day) {
                    0 -> "اليوم"
                    1 -> "غداً"
                    else -> dayFmt.format(Date(cal.timeInMillis))
                }
                Chip(label, state.guideDay == day) {
                    if (state.guideChannelId.isNotEmpty()) vm.loadGuide(state.guideChannelId, day)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (state.guideLoading) {
            Text(text = "جارٍ تحميل البرامج...", color = ZaghlolColors.Muted, fontSize = 19.sp)
        } else if (state.guidePrograms.isEmpty()) {
            EmptyState("📅", "لا توجد برامج لهذا اليوم — جرّب قناة أخرى أو حدّث دليل البرامج من الإعدادات")
        } else {
            val now = System.currentTimeMillis()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.guidePrograms) { prog ->
                    val isCurrent = prog.startMs <= now && prog.endMs > now
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) ZaghlolColors.AccentSoft else ZaghlolColors.Panel,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .tvClickable { if (isCurrent) vm.playTimeshift(prog) }
                    ) {
                        Text(
                            text = fmtTime(prog.startMs) + " - " + fmtTime(prog.endMs),
                            color = if (isCurrent) ZaghlolColors.Accent else ZaghlolColors.Muted,
                            fontSize = 17.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = prog.title,
                                color = ZaghlolColors.Text,
                                fontSize = 19.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                            if (prog.description.isNotEmpty()) {
                                Text(text = prog.description, color = ZaghlolColors.Muted, fontSize = 15.sp, maxLines = 1)
                            }
                        }
                        if (isCurrent) {
                            Text(text = "● يُعرض الآن", color = ZaghlolColors.Live, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(vm: AppViewModel, state: UiState) {
    val data = state.data
    val favChannels = data?.channels?.filter { state.favorites.contains("live:${it.id}") } ?: emptyList()
    val favMovies = data?.movies?.filter { state.favorites.contains("vod:${it.id}") } ?: emptyList()
    val favSeries = data?.series?.filter { state.favorites.contains("series:${it.id}") } ?: emptyList()
    val recents = state.recents

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "المفضلة", color = ZaghlolColors.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(12.dp))
        if (favChannels.isEmpty() && favMovies.isEmpty() && favSeries.isEmpty() && recents.isEmpty()) {
            EmptyState("⭐", "لا توجد عناصر مفضلة بعد — اضغط على النجمة بجانب أي قناة أو فيلم")
            return@Column
        }
        androidx.compose.foundation.lazy.LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (favChannels.isNotEmpty()) {
                item {
                    Column {
                        Text(text = "قنوات مفضلة", color = ZaghlolColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        for (ch in favChannels) {
                            ChannelRow(
                                channel = ch,
                                active = state.channel?.id == ch.id,
                                isFavorite = true,
                                onClick = { vm.playChannel(ch) }
                            )
                        }
                    }
                }
            }
            if (favMovies.isNotEmpty()) {
                item {
                    Column {
                        Text(text = "أفلام مفضلة", color = ZaghlolColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        for (row in favMovies.chunked(4)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (m in row) {
                                    PosterTile(
                                        name = m.name,
                                        badge = movieBadge(m),
                                        cover = m.cover,
                                        resume = state.resume["vod:${m.id}"],
                                        isFavorite = true,
                                        onClick = { vm.openMovie(m) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
            if (favSeries.isNotEmpty()) {
                item {
                    Column {
                        Text(text = "مسلسلات مفضلة", color = ZaghlolColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        for (row in favSeries.chunked(4)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (s in row) {
                                    val badge = listOfNotNull(
                                        s.year.takeIf { it.isNotEmpty() },
                                        s.rating.takeIf { it.isNotEmpty() }?.let { "⭐ $it" },
                                        s.categoryName.takeIf { it.isNotEmpty() }
                                    ).joinToString(" • ")
                                    PosterTile(
                                        name = s.name,
                                        badge = badge,
                                        cover = s.cover,
                                        resume = null,
                                        isFavorite = true,
                                        onClick = { vm.openSerie(s) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
            if (recents.isNotEmpty()) {
                item {
                    Column {
                        Text(text = "شوهد مؤخراً", color = ZaghlolColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        for (r in recents) {
                            val kindLabel = when (r.kind) {
                                "live" -> "قناة"
                                "vod" -> "فيلم"
                                else -> "مسلسل"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ZaghlolColors.Panel, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .tvClickable {
                                        when (r.kind) {
                                            "live" -> data?.channels?.firstOrNull { it.id == r.id }?.let { vm.playChannel(it) }
                                            "vod" -> data?.movies?.firstOrNull { it.id == r.id }?.let { vm.openMovie(it) }
                                            "series" -> data?.series?.firstOrNull { it.id == r.id }?.let { vm.openSerie(it) }
                                        }
                                    }
                            ) {
                                Text(text = kindLabel, color = ZaghlolColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                                Text(text = r.name, color = ZaghlolColors.Text, fontSize = 19.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, state: UiState) {
    var epgUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(state.epgUrl) }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "الإعدادات", color = ZaghlolColors.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "رابط دليل البرامج (XMLTV) — للقوائم M3U", color = ZaghlolColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LoginTextField(epgUrl, { epgUrl = it }, "https://example.com/epg.xml")
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("حفظ الرابط") { vm.setEpgUrl(epgUrl) }
            GhostButton("تحديث دليل البرامج") { vm.reloadEpg() }
        }
        if (state.epgReloadMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = state.epgReloadMsg, color = ZaghlolColors.Good, fontSize = 17.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(text = "الحساب", color = ZaghlolColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton("تسجيل الخروج") { vm.logout() }
            GhostButton("مسح كل البيانات") { vm.wipeData() }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(text = "حول التطبيق", color = ZaghlolColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Zaghlol Player v1.0.1 — نسخة Android TV\nنفس واجهة ووظائف نسخة الويندوز v2.0.3\nبيانات الدخول محفوظة على جهاز التلفزيون فقط.",
            color = ZaghlolColors.Muted,
            fontSize = 17.sp,
            lineHeight = 26.sp
        )
    }
}
