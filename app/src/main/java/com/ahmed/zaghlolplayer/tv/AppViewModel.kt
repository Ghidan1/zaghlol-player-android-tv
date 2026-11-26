package com.ahmed.zaghlolplayer.tv

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class Screen { LOGIN, MAIN }

data class UiState(
    val screen: Screen = Screen.LOGIN,
    val tab: AppTab = AppTab.LIVE,
    val logins: List<LoginProfile> = emptyList(),
    val loginMethod: String = "m3u",
    val busy: Boolean = false,
    val busySecs: Int = 0,
    val loginError: String = "",
    val testReport: String = "",
    val testOk: Boolean? = null,
    val data: PlaylistData? = null,
    val xtream: PlaylistService.XtreamClient? = null,
    val currentCat: String = "all",
    val movieCat: String = "all",
    val seriesCat: String = "all",
    val search: String = "",
    val playKind: PlayKind = PlayKind.LIVE,
    val channel: Channel? = null,
    val vod: VodItem? = null,
    val serie: SerieItem? = null,
    val episode: EpisodeItem? = null,
    val nowTitle: String = "",
    val nowTime: String = "",
    val nextTitle: String = "",
    val progress: Float = 0f,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val playError: String? = null,
    val focusMode: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val recents: List<RecentItem> = emptyList(),
    val resume: Map<String, Float> = emptyMap(),
    val guideChannelId: String = "",
    val guideDay: Int = 0,
    val guidePrograms: List<EpgProgram> = emptyList(),
    val guideLoading: Boolean = false,
    val movieDialog: VodItem? = null,
    val serieDialog: SerieItem? = null,
    val serieDialogSeason: String = "1",
    val toast: String = "",
    val epgUrl: String = "",
    val epgReloadMsg: String = ""
)

fun fmtTime(ms: Long): String {
    if (ms <= 0) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
}

fun fmtClock(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    val store = Store(application)
    val tvPlayer = TvPlayer(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var busyJob: Job? = null
    private var toastJob: Job? = null
    private var xmlTv: Map<String, List<EpgProgram>> = emptyMap()
    private var xmlTvLoaded = false
    private var pendingResumeFraction: Float? = null

    init {
        _state.update {
            it.copy(
                logins = store.loadLogins(),
                favorites = store.loadFavorites(),
                recents = store.loadRecents(),
                resume = store.loadResume(),
                epgUrl = store.epgUrl
            )
        }
        // Progress ticker (position / duration / live progress).
        viewModelScope.launch {
            while (true) {
                delay(500)
                val p = tvPlayer.player
                val st = _state.value
                if (st.screen == Screen.MAIN && st.data != null) {
                    val pos = if (p.playbackState == Player.STATE_READY) p.currentPosition else 0L
                    val dur = if (p.playbackState == Player.STATE_READY && p.duration > 0) p.duration else 0L
                    val prog = if (st.playKind == PlayKind.LIVE) st.progress else {
                        if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                    }
                    if (pos != st.positionMs || dur != st.durationMs || prog != st.progress ||
                        p.isPlaying != st.isPlaying
                    ) {
                        _state.update { it.copy(positionMs = pos, durationMs = dur, progress = prog, isPlaying = p.isPlaying) }
                    }
                }
            }
        }
        // EPG now/next refresh every minute while a live channel plays.
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val st = _state.value
                if (st.screen == Screen.MAIN && st.playKind == PlayKind.LIVE && st.channel != null) {
                    loadNowNext(st.channel)
                }
            }
        }
        // Surface player errors in the UI.
        viewModelScope.launch {
            tvPlayer.errorAr.collect { err ->
                if (err != null) _state.update { it.copy(playError = err) }
            }
        }
        boot()
    }

    override fun onCleared() {
        saveCurrentResume()
        tvPlayer.release()
        super.onCleared()
    }

    // ---------------- helpers ----------------

    fun toast(msg: String) = showToast(msg)

    private fun showToast(msg: String) {        toastJob?.cancel()
        _state.update { it.copy(toast = msg) }
        toastJob = viewModelScope.launch {
            delay(3500)
            _state.update { it.copy(toast = "") }
        }
    }

    fun setTab(tab: AppTab) = _state.update { it.copy(tab = tab, focusMode = false) }

    fun setLoginMethod(m: String) =
        _state.update { it.copy(loginMethod = m, loginError = "", testReport = "", testOk = null) }

    fun setSearch(q: String) = _state.update { it.copy(search = q) }

    fun selectCategory(id: String) = _state.update { it.copy(currentCat = id) }
    fun selectMovieCategory(id: String) = _state.update { it.copy(movieCat = id) }
    fun selectSeriesCategory(id: String) = _state.update { it.copy(seriesCat = id) }

    fun setFocusMode(on: Boolean) = _state.update { it.copy(focusMode = on) }

    fun clearPlayError() = _state.update { it.copy(playError = null) }

    fun dismissDialogs() = _state.update { it.copy(movieDialog = null, serieDialog = null) }

    // ---------------- boot / auto-login ----------------

    private fun boot() {
        val lastId = store.lastLoginId
        if (lastId.isEmpty()) return
        val p = store.loadLogins().firstOrNull { it.id == lastId } ?: return
        viewModelScope.launch {
            when (p.kind) {
                LoginKind.M3U_URL -> loginM3u(p.m3uUrl, p.name, silent = true)
                LoginKind.M3U_FILE -> {
                    val text = readUriText(p.m3uPath)
                    if (text == null) {
                        _state.update { it.copy(loginError = "ملف M3U غير موجود — أعد اختياره") }
                    } else {
                        loginM3uText(text, p.name)
                    }
                }
                LoginKind.XTREAM -> loginXtream(p.server, p.username, p.password, silent = true)
                LoginKind.DEMO -> loginDemo()
            }
        }
    }

    private fun readUriText(uriStr: String): String? {
        return try {
            val uri = Uri.parse(uriStr)
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- login ----------------

    private fun startBusy(): Job {
        _state.update { it.copy(busy = true, busySecs = 0, loginError = "", testReport = "", testOk = null) }
        val job = viewModelScope.launch {
            var s = 0
            while (true) {
                delay(1000)
                s++
                _state.update { it.copy(busySecs = s) }
            }
        }
        busyJob = job
        return job
    }

    private fun stopBusy() {
        busyJob?.cancel()
        busyJob = null
        _state.update { it.copy(busy = false) }
    }

    fun loginM3u(url: String, name: String, silent: Boolean = false) {
        viewModelScope.launch {
            if (url.isBlank()) {
                _state.update { it.copy(loginError = "من فضلك أدخل رابط M3U") }
                return@launch
            }
            startBusy()
            try {
                val r = PlaylistService.fetchText(url.trim(), 60)
                if (!r.ok) {
                    _state.update { it.copy(loginError = r.errorAr) }
                    return@launch
                }
                val text = r.text ?: ""
                if (!text.contains("#EXTM3U") && !text.contains("#EXTINF")) {
                    _state.update { it.copy(loginError = "السيرفر ردّ برد ليس قائمة M3U — تأكد من صحة الرابط وبيانات الاشتراك") }
                    return@launch
                }
                val login = LoginProfile(
                    id = UUID.randomUUID().toString(), name = name.ifBlank { "M3U Playlist" },
                    kind = LoginKind.M3U_URL, m3uUrl = url.trim()
                )
                val data = PlaylistService.parseM3u(text, login)
                if (data.channels.isEmpty() && data.movies.isEmpty() && data.series.isEmpty()) {
                    _state.update { it.copy(loginError = "القائمة فارغة — لا توجد قنوات في هذا الرابط") }
                    return@launch
                }
                enterApp(data, login, null)
            } finally {
                stopBusy()
            }
        }
    }

    fun loginM3uText(text: String, name: String) {
        viewModelScope.launch {
            startBusy()
            try {
                val login = LoginProfile(
                    id = UUID.randomUUID().toString(), name = name,
                    kind = LoginKind.M3U_FILE
                )
                val data = PlaylistService.parseM3u(text, login)
                if (data.channels.isEmpty() && data.movies.isEmpty() && data.series.isEmpty()) {
                    _state.update { it.copy(loginError = "الملف لا يحتوي على قنوات") }
                    return@launch
                }
                enterApp(data, login, null)
            } finally {
                stopBusy()
            }
        }
    }

    fun loginM3uFile(uri: Uri) {
        viewModelScope.launch {
            val text = readUriText(uri.toString())
            if (text == null) {
                _state.update { it.copy(loginError = "تعذّر قراءة الملف") }
                return@launch
            }
            // Keep the URI so auto-login can re-read the file later.
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Not all TVs grant persistable permission; login still works this session.
            }
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "playlist.m3u"
            val login = LoginProfile(
                id = UUID.randomUUID().toString(), name = "M3U File: $fileName",
                kind = LoginKind.M3U_FILE, m3uPath = uri.toString()
            )
            startBusy()
            try {
                val data = PlaylistService.parseM3u(text, login)
                if (data.channels.isEmpty() && data.movies.isEmpty() && data.series.isEmpty()) {
                    _state.update { it.copy(loginError = "الملف لا يحتوي على قنوات") }
                    return@launch
                }
                enterApp(data, login, null)
            } finally {
                stopBusy()
            }
        }
    }

    fun loginXtream(serverRaw: String, user: String, pass: String, silent: Boolean = false) {
        viewModelScope.launch {
            if (serverRaw.isBlank() || user.isBlank()) {
                _state.update { it.copy(loginError = "أدخل السيرفر واسم المستخدم وكلمة المرور") }
                return@launch
            }
            var server = serverRaw.trim().trimEnd('/')
            if (!server.startsWith("http", ignoreCase = true)) server = "http://$server"
            startBusy()
            try {
                val client = PlaylistService.XtreamClient(server, user.trim(), pass)
                val (authed, authErr) = client.authenticateDetailed()
                if (!authed) {
                    _state.update { it.copy(loginError = authErr) }
                    return@launch
                }
                val login = LoginProfile(
                    id = UUID.randomUUID().toString(), name = "Xtream",
                    kind = LoginKind.XTREAM, server = server, username = user.trim(), password = pass
                )
                // Hard global timeout: the whole login must finish in 25s.
                val data = try {
                    withTimeout(25_000) { client.loadAll(login) }
                } catch (e: TimeoutCancellationException) {
                    _state.update { it.copy(loginError = "انتهت المهلة (25 ثانية) — السيرفر بطيء أو لا يرد. جرّب «اختبار الاتصال» أولاً.") }
                    return@launch
                }
                if (data.channels.isEmpty() && data.movies.isEmpty() && data.series.isEmpty()) {
                    _state.update { it.copy(loginError = "تم تسجيل الدخول لكن الحساب لا يحتوي على قنوات أو أفلام أو مسلسلات") }
                    return@launch
                }
                enterApp(data, login, client)
            } finally {
                stopBusy()
            }
        }
    }

    fun loginDemo() {
        viewModelScope.launch {
            val data = PlaylistService.demoPlaylist()
            val login = data.login
            enterApp(data, login, null)
        }
    }

    // ---------------- connection test ----------------

    fun testM3u(url: String) {
        viewModelScope.launch {
            if (url.isBlank()) {
                _state.update { it.copy(testReport = "أدخل رابط M3U أولاً", testOk = false) }
                return@launch
            }
            val lines = mutableListOf<String>()
            val host = try {
                java.net.URL(url.trim()).host
            } catch (e: Exception) {
                url.trim()
            }
            lines.add("الخادم: $host")
            val r = PlaylistService.fetchStart(url.trim(), 15, 2048)
            if (!r.ok) {
                lines.add("النتيجة: فشل — " + r.errorAr)
                _state.update { it.copy(testReport = lines.joinToString("\n"), testOk = false) }
                return@launch
            }
            val size = r.contentLength?.let { " — الحجم ${"%,d".format(it)} بايت" } ?: ""
            lines.add("الرد: HTTP ${r.statusCode} خلال ${r.elapsedMs} م.ث$size")
            val head = (r.text ?: "").trimStart()
            val looksPlaylist = head.contains("#EXTM3U") || head.contains("#EXTINF")
            lines.add(
                if (looksPlaylist) "النتيجة: الرابط يرجّع قائمة M3U صالحة ✔ — اضغط «تحميل القائمة»"
                else "النتيجة: الرد ليس قائمة M3U — تأكد من بيانات الاشتراك والرابط"
            )
            _state.update { it.copy(testReport = lines.joinToString("\n"), testOk = looksPlaylist) }
        }
    }

    fun testXtream(serverRaw: String, user: String, pass: String) {
        viewModelScope.launch {
            if (serverRaw.isBlank()) {
                _state.update { it.copy(testReport = "أدخل رابط السيرفر أولاً", testOk = false) }
                return@launch
            }
            if (user.isBlank()) {
                _state.update { it.copy(testReport = "أدخل اسم المستخدم وكلمة المرور أولاً", testOk = false) }
                return@launch
            }
            var server = serverRaw.trim().trimEnd('/')
            if (!server.startsWith("http", ignoreCase = true)) server = "http://$server"
            val lines = mutableListOf<String>()
            lines.add("الخادم: $server")
            val sw = System.currentTimeMillis()
            val client = PlaylistService.XtreamClient(server, user.trim(), pass)
            val (authed, authErr) = client.authenticateDetailed()
            lines.add("زمن الرد: ${System.currentTimeMillis() - sw} م.ث")
            lines.add(
                if (authed) "النتيجة: تم تسجيل الدخول بنجاح ✔ — اضغط «تسجيل الدخول»"
                else "النتيجة: فشل — $authErr"
            )
            _state.update { it.copy(testReport = lines.joinToString("\n"), testOk = authed) }
        }
    }

    // ---------------- enter app ----------------

    private fun enterApp(data: PlaylistData, login: LoginProfile, xtream: PlaylistService.XtreamClient?) {
        store.rememberLogin(login)
        xmlTv = emptyMap()
        xmlTvLoaded = false
        val nCh = data.channels.size
        val nCat = data.liveCategories.size
        _state.update {
            it.copy(
                screen = Screen.MAIN, tab = AppTab.LIVE, data = data, xtream = xtream,
                logins = store.loadLogins(), currentCat = "all", movieCat = "all",
                seriesCat = "all", search = "", playKind = PlayKind.LIVE,
                channel = null, vod = null, serie = null, episode = null,
                nowTitle = "", nowTime = "", nextTitle = "", progress = 0f,
                loginError = "", focusMode = false
            )
        }
        showToast("تم تحميل $nCh قناة في $nCat تصنيف")
        val first = visibleChannels().firstOrNull()
        if (first != null) playChannel(first)
    }

    fun logout() {
        saveCurrentResume()
        tvPlayer.stop()
        store.lastLoginId = ""
        _state.update {
            it.copy(
                screen = Screen.LOGIN, data = null, xtream = null, channel = null,
                vod = null, serie = null, episode = null, tab = AppTab.LIVE,
                logins = store.loadLogins(), loginError = "", focusMode = false
            )
        }
    }

    fun wipeData() {
        saveCurrentResume()
        tvPlayer.stop()
        store.wipeAll()
        xmlTv = emptyMap()
        xmlTvLoaded = false
        _state.update {
            UiState(logins = emptyList(), favorites = emptySet(), recents = emptyList(), resume = emptyMap())
        }
        showToast("تم مسح كل البيانات")
    }

    fun deleteLogin(id: String) {
        store.deleteLogin(id)
        _state.update { it.copy(logins = store.loadLogins()) }
    }

    fun savedLogin(id: String) {
        val p = store.loadLogins().firstOrNull { it.id == id } ?: return
        when (p.kind) {
            LoginKind.M3U_URL -> loginM3u(p.m3uUrl, p.name)
            LoginKind.M3U_FILE -> {
                val text = readUriText(p.m3uPath)
                if (text == null) {
                    _state.update { it.copy(loginError = "ملف M3U غير موجود — أعد اختياره") }
                } else {
                    loginM3uText(text, p.name)
                }
            }
            LoginKind.XTREAM -> loginXtream(p.server, p.username, p.password)
            LoginKind.DEMO -> loginDemo()
        }
    }

    // ---------------- filtering ----------------

    fun visibleChannels(): List<Channel> {
        val d = _state.value.data ?: return emptyList()
        val st = _state.value
        var list = d.channels
        if (st.currentCat != "all") {
            list = list.filter { it.categoryId.equals(st.currentCat, ignoreCase = true) }
        }
        if (st.search.isNotBlank()) {
            val q = st.search.trim().lowercase(Locale.ROOT)
            list = list.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        }
        return list
    }

    fun visibleMovies(): List<VodItem> {
        val d = _state.value.data ?: return emptyList()
        val st = _state.value
        var list = d.movies
        if (st.movieCat != "all") list = list.filter { it.categoryId == st.movieCat }
        if (st.search.isNotBlank()) {
            val q = st.search.trim().lowercase(Locale.ROOT)
            list = list.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        }
        return list
    }

    fun visibleSeries(): List<SerieItem> {
        val d = _state.value.data ?: return emptyList()
        val st = _state.value
        var list = d.series
        if (st.seriesCat != "all") list = list.filter { it.categoryId == st.seriesCat }
        if (st.search.isNotBlank()) {
            val q = st.search.trim().lowercase(Locale.ROOT)
            list = list.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        }
        return list
    }

    // ---------------- playback ----------------

    fun playChannel(ch: Channel) {
        saveCurrentResume()
        _state.update {
            it.copy(
                playKind = PlayKind.LIVE, channel = ch, vod = null, serie = null,
                episode = null, nowTitle = ch.name, nowTime = "", nextTitle = "",
                progress = 0f, positionMs = 0, durationMs = 0, playError = null,
                tab = AppTab.LIVE
            )
        }
        store.addRecent("live", ch.id, ch.name)
        _state.update { it.copy(recents = store.loadRecents()) }
        tvPlayer.playHls(ch.url, "live:${ch.id}")
        loadNowNext(ch)
    }

    fun playVod(vod: VodItem) {
        saveCurrentResume()
        _state.update {
            it.copy(
                playKind = PlayKind.VOD, vod = vod, channel = null, serie = null,
                episode = null, nowTitle = vod.name,
                nowTime = (if (vod.year.isNotEmpty()) vod.year + " • " else "") + (vod.categoryName.ifEmpty { "فيلم" }),
                nextTitle = "", progress = 0f, positionMs = 0, durationMs = 0,
                playError = null, tab = AppTab.LIVE, movieDialog = null
            )
        }
        store.addRecent("vod", vod.id, vod.name)
        _state.update { it.copy(recents = store.loadRecents()) }
        applyResume("vod:${vod.id}") {
            tvPlayer.play(vod.url, "vod:${vod.id}")
        }
    }

    fun playEpisode(serie: SerieItem, ep: EpisodeItem) {
        saveCurrentResume()
        _state.update {
            it.copy(
                playKind = PlayKind.SERIES, serie = serie, episode = ep, channel = null,
                vod = null, nowTitle = ep.title, nowTime = serie.name,
                nextTitle = "", progress = 0f, positionMs = 0, durationMs = 0,
                playError = null, tab = AppTab.LIVE, serieDialog = null
            )
        }
        store.addRecent("series", serie.id, serie.name)
        _state.update { it.copy(recents = store.loadRecents()) }
        applyResume("series:${ep.id}") {
            tvPlayer.play(ep.url, "series:${ep.id}")
        }
    }

    private fun applyResume(key: String, startPlayback: () -> Unit) {
        val fraction = _state.value.resume[key]
        pendingResumeFraction = fraction
        tvPlayer.onFirstReady = {
            val f = pendingResumeFraction
            pendingResumeFraction = null
            if (f != null && f > 0.02f && f < 0.95f) {
                val dur = tvPlayer.player.duration
                if (dur > 0) {
                    tvPlayer.seekTo((f * dur).toLong())
                    showToast("استكمال من ${(f * 100).toInt()}%")
                }
            }
        }
        startPlayback()
    }

    fun saveCurrentResume() {
        val st = _state.value
        if (st.playKind == PlayKind.VOD && st.vod != null) {
            saveResumeFor("vod:${st.vod.id}")
        } else if (st.playKind == PlayKind.SERIES && st.episode != null) {
            saveResumeFor("series:${st.episode.id}")
        }
    }

    private fun saveResumeFor(key: String) {
        val p = tvPlayer.player
        val dur = p.duration
        val pos = p.currentPosition
        if (dur > 0 && pos > 0) {
            store.saveResume(key, (pos.toFloat() / dur).coerceIn(0f, 1f))
            _state.update { it.copy(resume = store.loadResume()) }
        }
    }

    fun togglePlayPause() = tvPlayer.togglePlayPause()

    fun seekBy(deltaMs: Long) {
        if (_state.value.playKind == PlayKind.LIVE) {
            // Live: left/right zaps to the previous/next channel.
            val list = visibleChannels()
            val cur = _state.value.channel ?: return
            val i = list.indexOfFirst { it.id == cur.id }
            if (i < 0) return
            val next = list[(i + (if (deltaMs > 0) 1 else -1) + list.size) % list.size]
            playChannel(next)
        } else {
            tvPlayer.seekBy(deltaMs)
        }
    }

    fun stepChannel(dir: Int) {
        val list = visibleChannels()
        val cur = _state.value.channel ?: return
        val i = list.indexOfFirst { it.id == cur.id }
        if (i < 0 || list.isEmpty()) return
        playChannel(list[(i + dir + list.size) % list.size])
    }

    // ---------------- EPG ----------------

    private suspend fun ensureXmlTv() {
        if (xmlTvLoaded) return
        xmlTvLoaded = true
        val url = store.epgUrl
        if (url.isNotBlank()) xmlTv = PlaylistService.fetchXmlTv(url.trim())
    }

    fun loadNowNext(ch: Channel) {
        viewModelScope.launch {
            val st = _state.value
            val d = st.data ?: return@launch
            val cached = d.epgCache[ch.streamId]
            var progs: List<EpgProgram> = cached?.sortedBy { it.startMs } ?: emptyList()
            if (progs.isEmpty()) {
                val client = st.xtream
                if (client != null) {
                    progs = client.getShortEpg(ch.streamId, 10)
                    if (progs.isNotEmpty()) d.epgCache[ch.streamId] = progs.toMutableList()
                } else {
                    ensureXmlTv()
                    val key = ch.epgId.ifEmpty { ch.streamId }
                    val now = System.currentTimeMillis()
                    progs = (xmlTv[key] ?: emptyList())
                        .filter { it.endMs > now - 2 * 3600_000 }
                        .sortedBy { it.startMs }.take(10)
                }
            }
            if (progs.isEmpty()) {
                _state.update { it.copy(nowTitle = ch.name, nowTime = "", nextTitle = "", progress = 0f) }
                return@launch
            }
            val now = System.currentTimeMillis()
            val cur = progs.firstOrNull { it.startMs <= now && it.endMs > now } ?: progs[0]
            val nxt = progs.firstOrNull { it.startMs > now }
            val prog = if (cur.endMs > cur.startMs) {
                ((now - cur.startMs).toFloat() / (cur.endMs - cur.startMs)).coerceIn(0f, 1f)
            } else 0f
            _state.update {
                it.copy(
                    nowTitle = cur.title.ifEmpty { ch.name },
                    nowTime = fmtTime(cur.startMs) + " - " + fmtTime(cur.endMs),
                    nextTitle = if (nxt != null) nxt.title + " • " + fmtTime(nxt.startMs) else "—",
                    progress = prog
                )
            }
        }
    }

    fun loadGuide(channelId: String, day: Int) {
        val st = _state.value
        val d = st.data ?: return
        val ch = d.channels.firstOrNull { it.id == channelId } ?: return
        _state.update { it.copy(guideChannelId = channelId, guideDay = day, guideLoading = true) }
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, day)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val from = cal.timeInMillis
            val to = from + 24 * 3600_000L
            var list: List<EpgProgram>
            val client = st.xtream
            if (client != null) {
                val cacheKey = ch.streamId + "|full"
                var full = d.epgCache[cacheKey]
                if (full.isNullOrEmpty()) {
                    full = client.getFullEpg(ch.streamId).toMutableList()
                    d.epgCache[cacheKey] = full
                }
                list = full.filter { it.startMs in from until to }.sortedBy { it.startMs }
            } else {
                ensureXmlTv()
                val key = ch.epgId.ifEmpty { ch.streamId }
                list = (xmlTv[key] ?: emptyList())
                    .filter { it.startMs in from until to }.sortedBy { it.startMs }
            }
            _state.update { it.copy(guidePrograms = list, guideLoading = false) }
        }
    }

    fun playTimeshift(program: EpgProgram) {
        val st = _state.value
        val client = st.xtream ?: run {
            showToast("التشغيل من البداية متاح فقط مع اشتراك Xtream")
            return
        }
        val ch = st.data?.channels?.firstOrNull { it.id == program.channelId }
            ?: st.data?.channels?.firstOrNull { it.id == st.guideChannelId }
            ?: return
        val durMin = ((program.endMs - program.startMs) / 60_000).toInt().coerceAtLeast(1)
        val url = client.timeshiftUrl(ch.streamId, program.startMs, durMin)
        saveCurrentResume()
        _state.update {
            it.copy(
                playKind = PlayKind.LIVE, channel = ch, vod = null, serie = null,
                episode = null, nowTitle = program.title, nowTime = fmtTime(program.startMs) + " - " + fmtTime(program.endMs),
                nextTitle = "", progress = 0f, positionMs = 0, durationMs = 0,
                playError = null, tab = AppTab.LIVE
            )
        }
        tvPlayer.playHls(url, "timeshift:${ch.id}")
    }

    // ---------------- favorites / dialogs / settings ----------------

    fun toggleFavorite(key: String) {
        val fav = store.toggleFavorite(key)
        _state.update { it.copy(favorites = store.loadFavorites()) }
        showToast(if (fav) "أُضيفت إلى المفضلة ★" else "أُزيلت من المفضلة")
    }

    fun openMovie(vod: VodItem) = _state.update { it.copy(movieDialog = vod) }

    fun openSerie(serie: SerieItem) {
        _state.update { it.copy(serieDialog = serie, serieDialogSeason = serie.episodes.keys.firstOrNull() ?: "1") }
        val client = _state.value.xtream
        if (client != null && serie.episodes.isEmpty()) {
            viewModelScope.launch {
                client.getSeriesInfo(serie)
                // Refresh the dialog with loaded episodes.
                _state.update { it.copy(serieDialog = serie, serieDialogSeason = serie.episodes.keys.firstOrNull() ?: "1") }
            }
        }
    }

    fun selectSerieSeason(season: String) = _state.update { it.copy(serieDialogSeason = season) }

    fun setEpgUrl(url: String) {
        store.epgUrl = url
        xmlTvLoaded = false
        xmlTv = emptyMap()
        _state.update { it.copy(epgUrl = url, epgReloadMsg = "") }
    }

    fun reloadEpg() {
        viewModelScope.launch {
            val url = store.epgUrl
            if (url.isBlank()) {
                _state.update { it.copy(epgReloadMsg = "لم يتم إدخال رابط EPG/XMLTV في الإعدادات") }
                return@launch
            }
            xmlTv = PlaylistService.fetchXmlTv(url.trim())
            xmlTvLoaded = true
            _state.update { it.copy(epgReloadMsg = "تم تحميل دليل البرامج: ${xmlTv.size} قناة") }
        }
    }
}
