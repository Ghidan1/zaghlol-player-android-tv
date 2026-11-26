package com.ahmed.zaghlolplayer.tv.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.ahmed.zaghlolplayer.tv.AppViewModel
import com.ahmed.zaghlolplayer.tv.SerieItem
import com.ahmed.zaghlolplayer.tv.UiState
import com.ahmed.zaghlolplayer.tv.VodItem
import kotlinx.coroutines.delay

@Composable
fun MoviesScreen(vm: AppViewModel, state: UiState) {
    val data = state.data
    val movies = vm.visibleMovies()
    // D-pad: focus the first poster when the tab opens, so the arrows move
    // through the grid right away. Re-runs when the background load
    // finishes, so the focus also lands when the movies arrive late.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(state.tab, state.vodLoading) {
        if (movies.isNotEmpty()) {
            delay(400)
            firstFocus.requestFocus()
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "الأفلام", color = ZaghlolColors.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.width(12.dp))
            CountBadge(if (state.vodLoading) "جارٍ التحميل..." else "${movies.size} فيلم")
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Chip("الكل", state.movieCat == "all") { vm.selectMovieCategory("all") } }
            items(data?.vodCategories ?: emptyList()) { cat ->
                Chip("${cat.name} (${cat.count})", state.movieCat == cat.id) { vm.selectMovieCategory(cat.id) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (state.vodLoading) {
            Text(text = "جارٍ تحميل الأفلام...", color = ZaghlolColors.Muted, fontSize = 19.sp)
        } else if (movies.isEmpty() && state.vodError.isNotEmpty()) {
            Column {
                EmptyState("🎬", state.vodError)
                Spacer(modifier = Modifier.height(16.dp))
                GhostButton("إعادة المحاولة") { vm.retryVod() }
            }
        } else if (movies.isEmpty()) {
            EmptyState("🎬", "لا توجد أفلام")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(movies) { index, m ->
                    PosterTile(
                        name = m.name,
                        badge = movieBadge(m),
                        cover = m.cover,
                        resume = state.resume["vod:${m.id}"],
                        isFavorite = state.favorites.contains("vod:${m.id}"),
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { vm.openMovie(m) }
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesScreen(vm: AppViewModel, state: UiState) {
    val data = state.data
    val series = vm.visibleSeries()
    // D-pad: focus the first poster when the tab opens, so the arrows move
    // through the grid right away. Re-runs when the background load
    // finishes, so the focus also lands when the series arrive late.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(state.tab, state.seriesLoading) {
        if (series.isNotEmpty()) {
            delay(400)
            firstFocus.requestFocus()
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "المسلسلات", color = ZaghlolColors.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.width(12.dp))
            CountBadge(if (state.seriesLoading) "جارٍ التحميل..." else "${series.size} مسلسل")
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Chip("الكل", state.seriesCat == "all") { vm.selectSeriesCategory("all") } }
            items(data?.seriesCategories ?: emptyList()) { cat ->
                Chip("${cat.name} (${cat.count})", state.seriesCat == cat.id) { vm.selectSeriesCategory(cat.id) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (state.seriesLoading) {
            Text(text = "جارٍ تحميل المسلسلات...", color = ZaghlolColors.Muted, fontSize = 19.sp)
        } else if (series.isEmpty() && state.seriesError.isNotEmpty()) {
            Column {
                EmptyState("🎞️", state.seriesError)
                Spacer(modifier = Modifier.height(16.dp))
                GhostButton("إعادة المحاولة") { vm.retrySeries() }
            }
        } else if (series.isEmpty()) {
            EmptyState("🎞️", "لا توجد مسلسلات")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(series) { index, s ->
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
                        isFavorite = state.favorites.contains("series:${s.id}"),
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { vm.openSerie(s) }
                    )
                }
            }
        }
    }
}

@Composable
fun MovieDialog(vm: AppViewModel, state: UiState, vod: VodItem) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .background(ZaghlolColors.Panel, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Row {
                PosterTile(
                    name = vod.name,
                    badge = movieBadge(vod),
                    cover = vod.cover,
                    resume = state.resume["vod:${vod.id}"],
                    isFavorite = state.favorites.contains("vod:${vod.id}"),
                    onClick = {},
                    modifier = Modifier.width(420.dp)
                )
                Spacer(modifier = Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = vod.name, color = ZaghlolColors.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = movieBadge(vod), color = ZaghlolColors.Muted, fontSize = 18.sp)
                    if (vod.plot.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = vod.plot, color = ZaghlolColors.Text, fontSize = 18.sp, lineHeight = 28.sp, maxLines = 6)
                    }
                    val res = state.resume["vod:${vod.id}"]
                    if (res != null && res > 0.02f && res < 0.95f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "استكمال من ${(res * 100).toInt()}%",
                            color = ZaghlolColors.Accent,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton("تشغيل") { vm.playVod(vod) }
                        GhostButton(
                            if (state.favorites.contains("vod:${vod.id}")) "★ في المفضلة" else "☆ إضافة للمفضلة"
                        ) { vm.toggleFavorite("vod:${vod.id}") }
                        GhostButton("إغلاق") { vm.dismissDialogs() }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesDialog(vm: AppViewModel, state: UiState, serie: SerieItem) {
    val seasons = serie.episodes.keys.sorted()
    val currentSeason = state.serieDialogSeason
    val episodes = serie.episodes[currentSeason] ?: emptyList()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .fillMaxSize(0.85f)
                .background(ZaghlolColors.Panel, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = serie.name, color = ZaghlolColors.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                GhostButton("إغلاق") { vm.dismissDialogs() }
            }
            val badge = listOfNotNull(
                serie.year.takeIf { it.isNotEmpty() },
                serie.rating.takeIf { it.isNotEmpty() }?.let { "⭐ $it" },
                serie.categoryName.takeIf { it.isNotEmpty() }
            ).joinToString(" • ")
            Text(text = badge, color = ZaghlolColors.Muted, fontSize = 18.sp)
            if (serie.plot.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = serie.plot, color = ZaghlolColors.Text, fontSize = 17.sp, lineHeight = 26.sp, maxLines = 3)
            }
            if (serie.cast.isNotEmpty()) {
                Text(text = "الممثلون: ${serie.cast}", color = ZaghlolColors.Muted, fontSize = 16.sp, maxLines = 2)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton(
                    if (state.favorites.contains("series:${serie.id}")) "★ في المفضلة" else "☆ إضافة للمفضلة"
                ) { vm.toggleFavorite("series:${serie.id}") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (seasons.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(seasons) { s ->
                        Chip("الموسم $s", currentSeason == s) { vm.selectSerieSeason(s) }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (episodes.isEmpty()) {
                EmptyState("🎞️", "لا توجد حلقات في هذا الموسم")
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(episodes) { ep ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ZaghlolColors.Panel3, RoundedCornerShape(10.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .tvClickable { vm.playEpisode(serie, ep) }
                        ) {
                            Text(
                                text = "الحلقة ${ep.episodeNum}",
                                color = ZaghlolColors.Accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = ep.title,
                                color = ZaghlolColors.Text,
                                fontSize = 19.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (ep.info.isNotEmpty()) {
                                Text(text = ep.info, color = ZaghlolColors.Muted, fontSize = 15.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
