package com.ahmed.zaghlolplayer.tv

enum class LoginKind { M3U_URL, M3U_FILE, XTREAM, DEMO }

data class LoginProfile(
    val id: String,
    val name: String,
    val kind: LoginKind,
    val m3uUrl: String = "",
    val m3uPath: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = ""
)

data class Category(val id: String, val name: String, var count: Int = 0)

data class Channel(
    val id: String,
    val name: String,
    val categoryId: String,
    var categoryName: String = "",
    val logo: String = "",
    val url: String,
    val epgId: String = "",
    val streamId: String = "",
    val isDemo: Boolean = false
)

data class VodItem(
    val id: String,
    val name: String,
    val categoryId: String,
    var categoryName: String = "",
    val cover: String = "",
    val rating: String = "",
    val year: String = "",
    val plot: String = "",
    val extension: String = "mp4",
    val url: String
)

data class EpisodeItem(
    val id: String,
    val title: String,
    val episodeNum: Int,
    val season: String,
    val extension: String,
    val url: String,
    val info: String = ""
)

data class SerieItem(
    val id: String,
    val name: String,
    val categoryId: String,
    var categoryName: String = "",
    val cover: String = "",
    val rating: String = "",
    val year: String = "",
    val plot: String = "",
    val cast: String = "",
    val episodes: MutableMap<String, MutableList<EpisodeItem>> = mutableMapOf()
)

data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long
)

data class PlaylistData(
    val login: LoginProfile,
    val liveCategories: List<Category>,
    val channels: List<Channel>,
    val vodCategories: List<Category>,
    val movies: List<VodItem>,
    val seriesCategories: List<Category>,
    val series: List<SerieItem>,
    val epgCache: MutableMap<String, MutableList<EpgProgram>> = mutableMapOf()
) {
    val isXtream: Boolean get() = login.kind == LoginKind.XTREAM
}

data class RecentItem(val kind: String, val id: String, val name: String, val whenMs: Long)

enum class AppTab { LIVE, GUIDE, MOVIES, SERIES, FAVS, SETTINGS }

enum class PlayKind { LIVE, VOD, SERIES }
