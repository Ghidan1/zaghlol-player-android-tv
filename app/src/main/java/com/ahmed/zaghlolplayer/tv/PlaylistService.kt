package com.ahmed.zaghlolplayer.tv

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * Kotlin port of the Windows PlaylistService (PlaylistService.cs).
 * Same rules: canonical group keys, M3U VOD/series classification,
 * Xtream player_api.php calls, XMLTV EPG, Arabic error messages.
 * Never puts a password into an error message.
 */
object PlaylistService {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    data class FetchResult(
        val ok: Boolean,
        val text: String? = null,
        val errorAr: String = "",
        val statusCode: Int? = null,
        val elapsedMs: Long = 0,
        val contentLength: Long? = null
    )

    fun cleanError(msg: String?): String {
        if (msg.isNullOrEmpty()) return "خطأ غير معروف"
        var s = msg.replace(Regex("(username|password)=[^&\\s\"']*", RegexOption.IGNORE_CASE), "$1=***")
        s = s.replace(Regex("://[^\\s\"']*?@", RegexOption.IGNORE_CASE), "://***@")
        return s
    }

    /** Xtream live .ts -> .m3u8 so ExoPlayer can play HLS. */
    fun streamUrlFor(url: String): String {
        if (url.isEmpty()) return url
        val re = Regex("/live/[^?]+\\.ts(\\?|$)", RegexOption.IGNORE_CASE)
        return re.replace(url) { m -> m.value.replace(".ts", ".m3u8", ignoreCase = true) }
    }

    /** Hue (0..360) for the gradient poster tile, derived from the title. */
    fun hueOf(name: String): Float {
        var h = 0
        for (c in name) h = (h * 31 + c.code) % 360
        if (h < 0) h += 360
        return h.toFloat()
    }

    private fun openConnection(urlStr: String, timeoutSec: Int): HttpURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = timeoutSec * 1000
        conn.readTimeout = timeoutSec * 1000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate")
        return conn
    }

    private fun readAll(conn: HttpURLConnection, maxBytes: Int = -1): String {
        val encoding = conn.contentEncoding ?: ""
        var stream = conn.inputStream
        if (encoding.contains("gzip", ignoreCase = true)) stream = GZIPInputStream(stream)
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val sb = StringBuilder()
        val buf = CharArray(8192)
        var total = 0
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            if (maxBytes > 0 && total + n > maxBytes) {
                sb.append(buf, 0, maxBytes - total)
                break
            }
            sb.append(buf, 0, n)
            total += n
        }
        reader.close()
        return sb.toString()
    }

    /** GET with a hard timeout. Never throws: returns a detailed Arabic error instead. */
    suspend fun fetchText(url: String, timeoutSec: Int): FetchResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return@withContext FetchResult(false, errorAr = "الرابط غير صحيح — لازم يبدأ بـ http:// أو https://")
        }
        try {
            val conn = openConnection(url, timeoutSec.coerceAtLeast(5))
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext FetchResult(
                    false, statusCode = code, elapsedMs = System.currentTimeMillis() - start,
                    errorAr = "السيرفر رد بالخطأ $code — تأكد من صحة الرابط وبيانات الاشتراك"
                )
            }
            val text = readAll(conn)
            FetchResult(true, text = text, statusCode = code, elapsedMs = System.currentTimeMillis() - start)
        } catch (e: java.net.SocketTimeoutException) {
            FetchResult(false, elapsedMs = System.currentTimeMillis() - start, errorAr = "انتهت المهلة ($timeoutSec ثانية) — السيرفر بطيء أو لا يرد")
        } catch (e: java.io.IOException) {
            val msg = e.message ?: ""
            if (msg.contains("empty", ignoreCase = true) || msg.contains("closed", ignoreCase = true)) {
                FetchResult(false, elapsedMs = System.currentTimeMillis() - start, errorAr = "السيرفر أغلق الاتصال بدون رد — تأكد من صحة الرابط وبيانات الاشتراك")
            } else {
                FetchResult(false, elapsedMs = System.currentTimeMillis() - start, errorAr = "تعذر الاتصال بالسيرفر: " + cleanError(msg))
            }
        } catch (e: Exception) {
            FetchResult(false, elapsedMs = System.currentTimeMillis() - start, errorAr = "خطأ أثناء التحميل: " + cleanError(e.message))
        }
    }

    /** GET that reads only the first bytes — used by the connection test. */
    suspend fun fetchStart(url: String, timeoutSec: Int, maxBytes: Int): FetchResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return@withContext FetchResult(false, errorAr = "الرابط غير صحيح — لازم يبدأ بـ http:// أو https://")
        }
        try {
            val conn = openConnection(url, timeoutSec.coerceAtLeast(5))
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext FetchResult(
                    false, statusCode = code, elapsedMs = System.currentTimeMillis() - start,
                    errorAr = "السيرفر رد بالخطأ $code — تأكد من صحة الرابط وبيانات الاشتراك"
                )
            }
            val text = readAll(conn, maxBytes)
            FetchResult(
                true, text = text, statusCode = code,
                elapsedMs = System.currentTimeMillis() - start,
                contentLength = if (conn.contentLengthLong >= 0) conn.contentLengthLong else null
            )
        } catch (e: java.net.SocketTimeoutException) {
            FetchResult(false, elapsedMs = System.currentTimeMillis() - start, errorAr = "انتهت المهلة ($timeoutSec ثانية) — السيرفر بطيء أو لا يرد")
        } catch (e: java.io.IOException) {
            FetchResult(false, elapsedMs = System.currentTimeMillis() - start, errorAr = "تعذر الاتصال بالسيرفر: " + cleanError(e.message))
        } catch (e: Exception) {
            FetchResult(false, elapsedMs = System.currentTimeMillis() - start, errorAr = "خطأ أثناء التحميل: " + cleanError(e.message))
        }
    }

    // ---------------- M3U ----------------

    /**
     * Incremental M3U parser: feed lines one at a time — from a whole String
     * or from a live network stream — and build the playlist at the end.
     * Streaming matters because this panel drops ("Connection reset") huge
     * playlist downloads; buffering the whole file first fails before a
     * single channel is seen, while the streamed parser keeps everything
     * parsed up to the reset.
     */
    class M3uParser(private val login: LoginProfile) {
        val liveCatMap = linkedMapOf<String, String>() // canonical key (lowercase) -> first-seen spelling
        val vodCatMap = linkedMapOf<String, String>()
        val serCatMap = linkedMapOf<String, String>()
        val seriesMap = linkedMapOf<String, SerieItem>()
        private fun canonical(map: MutableMap<String, String>, gRaw: String): String {
            var g = gRaw.trim()
            if (g.isEmpty()) g = "All Channels"
            val key = g.lowercase(Locale.ROOT)
            return map.getOrPut(key) { g }
        }
        val channels = mutableListOf<Channel>()
        val movies = mutableListOf<VodItem>()
        var pendingAttrs = ""
        var pendingName = ""
        var pendingExtGrp = ""
        var pending = false

        /** True once a playlist marker (#EXTM3U / #EXTINF) has been seen. */
        var sawMarker = false
            private set

        fun acceptLine(rawLine: String) {
            val line = rawLine.trim()
            if (line.isEmpty()) return
            if (line.startsWith("#EXTM3U", ignoreCase = true) ||
                line.startsWith("#EXTINF", ignoreCase = true)
            ) sawMarker = true
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val comma = line.lastIndexOf(',')
                pendingAttrs = if (comma > 0) line.substring(0, comma) else line
                pendingName = if (comma > 0 && comma + 1 < line.length) line.substring(comma + 1).trim() else ""
                pendingExtGrp = ""
                pending = true
                return
            }
            if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                pendingExtGrp = line.substring("#EXTGRP:".length).trim()
                return
            }
            if (line.startsWith("#")) return
            if (pending && (line.startsWith("http", ignoreCase = true) || line.contains("://"))) {
                val attrs = parseAttrs(pendingAttrs)
                var name = pendingName
                if (name.isEmpty()) name = attrs["tvg-name"] ?: ""
                if (name.isEmpty()) name = line
                val rawGroup = (attrs["group-title"]?.takeIf { it.isNotEmpty() }) ?: pendingExtGrp
                val logo = attrs["tvg-logo"] ?: ""
                val tvgId = attrs["tvg-id"] ?: name
                // ---- classify: live | VOD movie | series episode ----
                val urlPath = line.substringBefore('?')
                val dot = urlPath.lastIndexOf('.')
                val ext = if (dot >= 0 && dot > urlPath.lastIndexOf('/')) urlPath.substring(dot + 1).lowercase(Locale.ROOT) else ""
                val isVodExt = ext in setOf("mp4", "mkv", "avi", "mov", "m4v", "webm", "flv")
                var year = ""
                val ym = Regex("\\((19|20)\\d\\d\\)").find(name)
                if (ym != null) {
                    year = ym.value.trim('(', ')')
                    name = name.removeRange(ym.range).trim()
                }
                // Series markers: S01E02 / Season 1 Episode 2 / الموسم 1 الحلقة 2
                var season = 0
                var epNum = 0
                var seriesName = ""
                val se = Regex("[Ss](\\d{1,2})\\s*[Ee](\\d{1,3})").find(name)
                if (se != null) {
                    season = se.groupValues[1].toIntOrNull() ?: 0
                    epNum = se.groupValues[2].toIntOrNull() ?: 0
                    seriesName = name.removeRange(se.range).trim().trimEnd('-', ':', '|').trim()
                } else {
                    val sm = Regex("[Ss]eason\\s*(\\d+)", RegexOption.IGNORE_CASE).find(name)
                    val em = Regex("[Ee]pisode\\s*(\\d+)", RegexOption.IGNORE_CASE).find(name)
                    val am = Regex("الموسم\\s*(\\d+)").find(name)
                    val ae = Regex("الحلقة\\s*(\\d+)").find(name)
                    if (sm != null) season = sm.groupValues[1].toIntOrNull() ?: 0
                    if (am != null) season = am.groupValues[1].toIntOrNull() ?: 0
                    if (em != null) epNum = em.groupValues[1].toIntOrNull() ?: 0
                    if (ae != null) epNum = ae.groupValues[1].toIntOrNull() ?: 0
                    if (season > 0 || epNum > 0) {
                        seriesName = name
                        for (rx in listOf("[Ss]eason\\s*\\d+", "[Ee]pisode\\s*\\d+", "الموسم\\s*\\d+", "الحلقة\\s*\\d+")) {
                            seriesName = seriesName.replace(Regex(rx, RegexOption.IGNORE_CASE), "").trim()
                        }
                        seriesName = seriesName.trimEnd('-', ':', '|').trim()
                    }
                }
                val groupSaysSeries = rawGroup.contains("مسلسل", ignoreCase = true) ||
                    rawGroup.contains("series", ignoreCase = true)
                if (seriesName.isEmpty() && groupSaysSeries && isVodExt) {
                    seriesName = name; season = 1; epNum = 0
                }
                if (seriesName.isNotEmpty()) {
                    val group = canonical(serCatMap, rawGroup)
                    val key = seriesName.lowercase(Locale.ROOT)
                    var serie = seriesMap[key]
                    if (serie == null) {
                        serie = SerieItem(
                            id = seriesName, name = seriesName, categoryId = group,
                            categoryName = group, cover = logo, year = year
                        )
                        seriesMap[key] = serie
                    }
                    if (logo.isNotEmpty() && serie.cover.isEmpty()) serie.cover = logo
                    val sn = if (season > 0) season.toString() else "1"
                    val eps = serie.episodes.getOrPut(sn) { mutableListOf() }
                    eps.add(
                        EpisodeItem(
                            id = tvgId, title = name, episodeNum = epNum, season = sn,
                            extension = if (ext.isNotEmpty()) ext else "mp4", url = line
                        )
                    )
                } else if (isVodExt) {
                    val group = canonical(vodCatMap, rawGroup)
                    movies.add(
                        VodItem(
                            id = tvgId, name = name, categoryId = group, categoryName = group,
                            cover = logo, year = year, extension = ext, url = line
                        )
                    )
                } else {
                    val group = canonical(liveCatMap, rawGroup)
                    channels.add(
                        Channel(
                            id = tvgId, name = name, categoryId = group, categoryName = group,
                            logo = logo, url = line, epgId = tvgId, streamId = tvgId
                        )
                    )
                }
                pending = false
            }
        }

        fun build(): PlaylistData {
            // Categories in the playlist's OWN order (first appearance), NOT alphabetical.
            val liveCats = liveCatMap.values.map { Category(it, it) }
            val vodCats = vodCatMap.values.map { Category(it, it) }
            val serCats = serCatMap.values.map { Category(it, it) }
            for (s in seriesMap.values) {
                for (eps in s.episodes.values) eps.sortBy { it.episodeNum }
            }
            // Real counts (single source of truth).
            for (c in liveCats) c.count = channels.count { it.categoryId.equals(c.id, ignoreCase = true) }
            for (c in vodCats) c.count = movies.count { it.categoryId.equals(c.id, ignoreCase = true) }
            for (c in serCats) c.count = seriesMap.values.count { it.categoryId.equals(c.id, ignoreCase = true) }
            return PlaylistData(
                login = login,
                liveCategories = liveCats, channels = channels,
                vodCategories = vodCats, movies = movies,
                seriesCategories = serCats, series = seriesMap.values.toList()
            )
        }
    }

    /** Parse a whole M3U text (local file / already-downloaded text). */
    fun parseM3u(text: String, login: LoginProfile): PlaylistData {
        val parser = M3uParser(login)
        // Split on \r\n, \n AND lone \r (some panels emit old-Mac line endings).
        for (line in text.split(Regex("\r\n|\n|\r"))) parser.acceptLine(line)
        return parser.build()
    }

    /**
     * Downloads an M3U playlist and parses it WHILE it streams in, line by
     * line, instead of buffering the whole file first. The panel is slow and
     * resets long connections, so a full download of a 60k-entry playlist
     * often dies mid-way ("Connection reset"). Returns (data, errorAr):
     * exactly one of them is set. The total budget caps the whole
     * download+parse; each socket read has its own shorter timeout so a
     * stalled connection fails fast instead of hanging.
     */
    suspend fun fetchAndParseM3u(
        url: String,
        login: LoginProfile,
        totalBudgetSec: Int = 150
    ): Pair<PlaylistData?, String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val budgetMs = totalBudgetSec * 1000L
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return@withContext null to "الرابط غير صحيح — لازم يبدأ بـ http:// أو https://"
        }
        try {
            val conn = openConnection(url, 30)
            conn.readTimeout = 45_000
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext null to "السيرفر رد بالخطأ $code — تأكد من صحة الرابط وبيانات الاشتراك"
            }
            val encoding = conn.contentEncoding ?: ""
            var stream = conn.inputStream
            if (encoding.contains("gzip", ignoreCase = true)) stream = GZIPInputStream(stream)
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val parser = M3uParser(login)
            var lines = 0
            var timedOut = false
            reader.use {
                while (true) {
                    val line = it.readLine() ?: break
                    parser.acceptLine(line)
                    if (++lines % 500 == 0 && System.currentTimeMillis() - start > budgetMs) {
                        timedOut = true
                        break
                    }
                }
            }
            if (timedOut) {
                return@withContext null to "انتهت المهلة ($totalBudgetSec ثانية) أثناء تحميل القائمة — السيرفر بطيء جداً. جرّب تسجيل الدخول بـ Xtream بدل رابط M3U."
            }
            if (!parser.sawMarker) {
                return@withContext null to "السيرفر ردّ برد ليس قائمة M3U — تأكد من صحة الرابط وبيانات الاشتراك"
            }
            parser.build() to ""
        } catch (e: java.net.SocketTimeoutException) {
            null to "انتهت المهلة أثناء تحميل القائمة — السيرفر بطيء أو لا يرد. جرّب تسجيل الدخول بـ Xtream بدل رابط M3U."
        } catch (e: java.io.IOException) {
            null to "تعذّر الاتصال بالسيرفر أثناء تحميل القائمة: " + cleanError(e.message) +
                " — السيرفر يقطع تحميل القوائم الكبيرة، جرّب تسجيل الدخول بـ Xtream بدل رابط M3U."
        } catch (e: Exception) {
            null to "خطأ أثناء التحميل: " + cleanError(e.message)
        }
    }


    private fun parseAttrs(extinf: String): Map<String, String> {
        val d = mutableMapOf<String, String>()
        for (m in Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"").findAll(extinf)) {
            d[m.groupValues[1]] = m.groupValues[2]
        }
        return d
    }

    // ---------------- Demo playlist (free public streams, verified working) ----------------

    fun demoPlaylist(): PlaylistData {
        val login = LoginProfile(
            id = "demo", name = "قنوات تجريبية", kind = LoginKind.DEMO
        )
        val demo = listOf(
            "العربية" to "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8",
            "الجزيرة" to "https://live-hls-web-aja.getaj.net/AJA/index.m3u8",
            "Red Bull TV" to "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
            "DW دويتشه فيله" to "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8",
            "بث تجريبي" to "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        )
        val channels = demo.map { (name, url) ->
            Channel(
                id = name, name = name, categoryId = "Demo", categoryName = "قنوات تجريبية",
                url = url, epgId = name, streamId = name, isDemo = true
            )
        }
        val cat = Category("Demo", "قنوات تجريبية", channels.size)
        return PlaylistData(
            login = login, liveCategories = listOf(cat), channels = channels,
            vodCategories = emptyList(), movies = emptyList(),
            seriesCategories = emptyList(), series = emptyList()
        )
    }

    // ---------------- Xtream Codes ----------------

    class XtreamClient(val serverRaw: String, val user: String, val pass: String) {
        val server: String = serverRaw.trim().trimEnd('/')

        fun liveUrl(streamId: String, m3u8: Boolean = true): String =
            "$server/live/${enc(user)}/${enc(pass)}/$streamId.${if (m3u8) "m3u8" else "ts"}"

        fun movieUrl(streamId: String, ext: String): String =
            "$server/movie/${enc(user)}/${enc(pass)}/$streamId.$ext"

        fun seriesUrl(episodeId: String, ext: String): String =
            "$server/series/${enc(user)}/${enc(pass)}/$episodeId.$ext"

        fun timeshiftUrl(streamId: String, startMs: Long, durationMinutes: Int): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm-ss", Locale.US)
            fmt.timeZone = TimeZone.getDefault()
            val start = fmt.format(java.util.Date(startMs))
            return "$server/streaming/timeshift.php?username=${enc(user)}&password=${enc(pass)}&stream=$streamId&start=$start&duration=$durationMinutes"
        }

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

        private fun apiUrl(action: String, extra: String = ""): String {
            var url = "$server/player_api.php?username=${enc(user)}&password=${enc(pass)}"
            if (action.isNotEmpty()) url += "&action=$action"
            if (extra.isNotEmpty()) url += "&$extra"
            return url
        }

        private suspend fun getJson(url: String, timeoutSec: Int = 30): JSONObject? {
            val r = fetchText(url, timeoutSec)
            if (!r.ok || r.text.isNullOrBlank()) return null
            return try {
                JSONObject(r.text)
            } catch (e: Exception) {
                null
            }
        }

        private suspend fun getJsonArray(url: String, timeoutSec: Int = 30): JSONArray? {
            val r = fetchText(url, timeoutSec)
            if (!r.ok || r.text.isNullOrBlank()) return null
            return try {
                JSONArray(r.text)
            } catch (e: Exception) {
                null
            }
        }

        /** Auth with a detailed Arabic reason on failure. */
        suspend fun authenticateDetailed(): Pair<Boolean, String> {
            val r = fetchText(apiUrl(""), 15)
            if (!r.ok) return false to r.errorAr
            return try {
                val root = JSONObject(r.text ?: "")
                val ui = root.optJSONObject("user_info") ?: return false to "السيرفر ردّ برد غير متوقع — تأكد من رابط السيرفر"
                if (ui.has("auth") && ui.optInt("auth", -1) == 0) {
                    return false to "بيانات الدخول غير صحيحة أو الاشتراك منتهي"
                }
                if (ui.has("status")) {
                    val st = ui.optString("status", "")
                    return if (st == "Active") true to ""
                    else false to "الحساب غير نشط (الحالة: $st) — راجع مزوّد الخدمة"
                }
                true to ""
            } catch (e: Exception) {
                false to "السيرفر ردّ برد غير صالح (ليس JSON) — تأكد من رابط السيرفر"
            }
        }

        private fun str(obj: JSONObject, name: String): String {
            if (!obj.has(name) || obj.isNull(name)) return ""
            val v = obj.get(name)
            return when (v) {
                is Number -> v.toString()
                is String -> v
                else -> ""
            }
        }

        private suspend fun getCategories(action: String, timeoutSec: Int = 30): List<Category> {
            val arr = getJsonArray(apiUrl(action), timeoutSec) ?: return emptyList()
            val list = mutableListOf<Category>()
            for (i in 0 until arr.length()) {
                val el = arr.optJSONObject(i) ?: continue
                list.add(Category(str(el, "category_id"), str(el, "category_name")))
            }
            return list
        }

        /** One retry: a slow panel often drops a single request in a burst. */
        private suspend fun getCategoriesRetry(action: String, timeoutSec: Int = 60): List<Category> {
            val first = getCategories(action, timeoutSec)
            if (first.isNotEmpty()) return first
            delay(800)
            return getCategories(action, timeoutSec)
        }

        private suspend fun getLiveStreamsRetry(timeoutSec: Int = 60): List<Channel> {
            val first = getLiveStreams(timeoutSec)
            if (first.isNotEmpty()) return first
            delay(800)
            return getLiveStreams(timeoutSec)
        }

        private suspend fun getVodStreamsRetry(timeoutSec: Int = 60): List<VodItem> {
            val first = getVodStreams(timeoutSec)
            if (first.isNotEmpty()) return first
            delay(800)
            return getVodStreams(timeoutSec)
        }

        private suspend fun getSeriesRetry(timeoutSec: Int = 60): List<SerieItem> {
            val first = getSeries(timeoutSec)
            if (first.isNotEmpty()) return first
            delay(800)
            return getSeries(timeoutSec)
        }

        /** Last-resort categories: derive them from the items themselves so the
         *  sidebar / chips are never blank when the items did load. */
        private fun deriveCategories(ids: List<String>): List<Category> {
            val seen = linkedMapOf<String, String>() // canonical key -> first-seen spelling
            for (raw in ids) {
                val id = raw.trim()
                if (id.isEmpty()) continue
                seen.getOrPut(id.lowercase(Locale.ROOT)) { id }
            }
            return seen.values.map { Category(it, it) }
        }

        private fun nameFor(map: Map<String, String>, id: String): String {
            map[id]?.let { return it }
            for ((k, v) in map) if (k.equals(id, ignoreCase = true)) return v
            return ""
        }

        /** Attach display names (case-insensitive) and real counts to a
         *  categories list, deriving the list from the items themselves when
         *  the categories call came back empty. */
        private fun finalizeCats(
            catsIn: List<Category>,
            itemCatIds: List<String>,
            itemCount: (Category) -> Int
        ): List<Category> {
            var cats = catsIn
            if (cats.isEmpty() && itemCatIds.isNotEmpty())
                cats = deriveCategories(itemCatIds)
            for (c in cats) c.count = itemCount(c)
            return cats
        }

        /**
         * Progressive loading (v1.0.3): the v1.0.2 approach loaded all 6
         * catalogue calls before entering the app, behind one 90s global
         * timeout. On this slow panel the sequential big lists take longer
         * than 90s in total, so the login counted the full 90 seconds and
         * then failed — even though live TV was usable much earlier.
         * Now entry happens as soon as LIVE TV is ready; movies and series
         * load in the background afterwards, each independently, and a
         * failure in one section never blocks the others or kicks the user
         * back to login.
         */

        /** Phase 1 — live TV only. Enter the app as soon as this returns. */
        suspend fun loadLive(login: LoginProfile, timeoutSec: Int = 60): PlaylistData {
            val liveCats0 = getCategoriesRetry("get_live_categories", timeoutSec)
            val live = getLiveStreamsRetry(timeoutSec)
            val liveCats = finalizeCats(
                liveCats0, live.map { it.categoryId },
                { c -> live.count { it.categoryId.equals(c.id, ignoreCase = true) } }
            )
            val names = liveCats.associate { it.id to it.name }
            for (ch in live) ch.categoryName = nameFor(names, ch.categoryId)
            return PlaylistData(
                login = login, liveCategories = liveCats, channels = live,
                vodCategories = emptyList(), movies = emptyList(),
                seriesCategories = emptyList(), series = emptyList()
            )
        }

        /** Phase 2a — movies, loaded in the background after entry. */
        suspend fun loadVod(login: LoginProfile, timeoutSec: Int = 60): Pair<List<Category>, List<VodItem>> {
            val vodCats0 = getCategoriesRetry("get_vod_categories", timeoutSec)
            val vod = getVodStreamsRetry(timeoutSec)
            val vodCats = finalizeCats(
                vodCats0, vod.map { it.categoryId },
                { c -> vod.count { it.categoryId.equals(c.id, ignoreCase = true) } }
            )
            val names = vodCats.associate { it.id to it.name }
            for (m in vod) m.categoryName = nameFor(names, m.categoryId)
            return vodCats to vod
        }

        /** Phase 2b — series, loaded in the background after entry. */
        suspend fun loadSeries(login: LoginProfile, timeoutSec: Int = 60): Pair<List<Category>, List<SerieItem>> {
            val serCats0 = getCategoriesRetry("get_series_categories", timeoutSec)
            val ser = getSeriesRetry(timeoutSec)
            val serCats = finalizeCats(
                serCats0, ser.map { it.categoryId },
                { c -> ser.count { it.categoryId.equals(c.id, ignoreCase = true) } }
            )
            val names = serCats.associate { it.id to it.name }
            for (s in ser) s.categoryName = nameFor(names, s.categoryId)
            return serCats to ser
        }

        suspend fun getLiveStreams(timeoutSec: Int = 30): List<Channel> {
            val arr = getJsonArray(apiUrl("get_live_streams"), timeoutSec) ?: return emptyList()
            val list = mutableListOf<Channel>()
            for (i in 0 until arr.length()) {
                val el = arr.optJSONObject(i) ?: continue
                val id = str(el, "stream_id")
                list.add(
                    Channel(
                        id = id, streamId = id, name = str(el, "name"),
                        categoryId = str(el, "category_id"),
                        logo = str(el, "stream_icon"), epgId = str(el, "epg_channel_id"),
                        url = liveUrl(id)
                    )
                )
            }
            return list
        }

        suspend fun getVodStreams(timeoutSec: Int = 30): List<VodItem> {
            val arr = getJsonArray(apiUrl("get_vod_streams"), timeoutSec) ?: return emptyList()
            val list = mutableListOf<VodItem>()
            for (i in 0 until arr.length()) {
                val el = arr.optJSONObject(i) ?: continue
                val id = str(el, "stream_id")
                var ext = str(el, "container_extension")
                if (ext.isEmpty()) ext = "mp4"
                var rating = str(el, "rating")
                if (rating.isEmpty() && el.has("rating_5based")) rating = str(el, "rating_5based")
                list.add(
                    VodItem(
                        id = id, name = str(el, "name"), categoryId = str(el, "category_id"),
                        cover = str(el, "cover"), rating = rating, year = str(el, "year"),
                        plot = str(el, "plot"), extension = ext, url = movieUrl(id, ext)
                    )
                )
            }
            return list
        }

        suspend fun getSeries(timeoutSec: Int = 30): List<SerieItem> {
            val arr = getJsonArray(apiUrl("get_series"), timeoutSec) ?: return emptyList()
            val list = mutableListOf<SerieItem>()
            for (i in 0 until arr.length()) {
                val el = arr.optJSONObject(i) ?: continue
                list.add(
                    SerieItem(
                        id = str(el, "series_id"), name = str(el, "name"),
                        categoryId = str(el, "category_id"), cover = str(el, "cover"),
                        rating = str(el, "rating"), year = str(el, "year"),
                        plot = str(el, "plot"), cast = str(el, "cast")
                    )
                )
            }
            return list
        }

        suspend fun getSeriesInfo(serie: SerieItem) {
            val doc = getJson(apiUrl("get_series_info", "series_id=${serie.id}")) ?: return
            val info = doc.optJSONObject("info")
            if (info != null) {
                val plot = str(info, "plot"); if (plot.isNotEmpty()) serie.plot = plot
                val cast = str(info, "cast"); if (cast.isNotEmpty()) serie.cast = cast
                val rating = str(info, "rating"); if (rating.isNotEmpty()) serie.rating = rating
                val year = str(info, "year"); if (year.isNotEmpty()) serie.year = year
                val cover = str(info, "cover"); if (cover.isNotEmpty()) serie.cover = cover
            }
            val eps = doc.optJSONObject("episodes") ?: return
            val keys = eps.keys()
            while (keys.hasNext()) {
                val season = keys.next()
                val arr = eps.optJSONArray(season) ?: continue
                val list = mutableListOf<EpisodeItem>()
                for (i in 0 until arr.length()) {
                    val ep = arr.optJSONObject(i) ?: continue
                    val epId = str(ep, "id")
                    var ext = str(ep, "container_extension")
                    if (ext.isEmpty()) ext = "mp4"
                    val epNum = str(ep, "episode_num").toIntOrNull() ?: 0
                    var infoText = ""
                    val epi = ep.optJSONObject("info")
                    if (epi != null) infoText = str(epi, "plot")
                    list.add(
                        EpisodeItem(
                            id = epId, title = str(ep, "title"), episodeNum = epNum,
                            season = season, extension = ext,
                            url = seriesUrl(epId, ext), info = infoText
                        )
                    )
                }
                list.sortBy { it.episodeNum }
                serie.episodes[season] = list
            }
        }

        private fun parseEpgListings(doc: JSONObject, channelId: String): List<EpgProgram> {
            val list = mutableListOf<EpgProgram>()
            val arr = doc.optJSONArray("epg_listings") ?: return list
            for (i in 0 until arr.length()) {
                val el = arr.optJSONObject(i) ?: continue
                var title = str(el, "title")
                if (title.isEmpty()) title = str(el, "name")
                val startTs = str(el, "start_timestamp").toLongOrNull() ?: 0L
                val endTs = str(el, "stop_timestamp").toLongOrNull() ?: 0L
                list.add(
                    EpgProgram(
                        channelId = channelId, title = title,
                        description = str(el, "description"),
                        startMs = if (startTs > 0) startTs * 1000 else 0L,
                        endMs = if (endTs > 0) endTs * 1000 else 0L
                    )
                )
            }
            return list
        }

        suspend fun getShortEpg(streamId: String, limit: Int = 10): List<EpgProgram> {
            val doc = getJson(apiUrl("get_short_epg", "stream_id=$streamId&limit=$limit")) ?: return emptyList()
            return parseEpgListings(doc, streamId)
        }

        suspend fun getFullEpg(streamId: String): List<EpgProgram> {
            val doc = getJson(apiUrl("get_simple_data_table", "stream_id=$streamId")) ?: return emptyList()
            return parseEpgListings(doc, streamId)
        }
    }

    // ---------------- XMLTV (for M3U playlists) ----------------

    suspend fun fetchXmlTv(url: String): Map<String, List<EpgProgram>> {
        val result = mutableMapOf<String, MutableList<EpgProgram>>()
        val r = fetchText(url, 20)
        if (r.ok && !r.text.isNullOrEmpty()) parseXmlTv(r.text, result)
        return result
    }

    fun parseXmlTv(xml: String, result: MutableMap<String, MutableList<EpgProgram>>) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(xml.reader())
            var event = parser.eventType
            var curChannel = ""
            var curStart: Long? = null
            var curEnd: Long? = null
            var curTitle = ""
            var curDesc = ""
            var inProgramme = false
            var textTag = ""
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "programme" -> {
                                inProgramme = true
                                curChannel = parser.getAttributeValue(null, "channel") ?: ""
                                curStart = parseXmlTvTime(parser.getAttributeValue(null, "start"))
                                curEnd = parseXmlTvTime(parser.getAttributeValue(null, "stop"))
                                curTitle = ""; curDesc = ""
                            }
                            "title", "desc" -> textTag = parser.name
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inProgramme) {
                            val t = parser.text
                            if (textTag == "title") curTitle += t
                            else if (textTag == "desc") curDesc += t
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "title", "desc" -> textTag = ""
                            "programme" -> {
                                if (curChannel.isNotEmpty() && curStart != null) {
                                    val list = result.getOrPut(curChannel) { mutableListOf() }
                                    list.add(
                                        EpgProgram(
                                            channelId = curChannel, title = curTitle,
                                            description = curDesc, startMs = curStart,
                                            endMs = curEnd ?: (curStart + 3600_000)
                                        )
                                    )
                                }
                                inProgramme = false
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // Malformed XMLTV: ignore, EPG just stays empty.
        }
    }

    private fun parseXmlTvTime(s: String?): Long? {
        if (s.isNullOrEmpty()) return null
        // "20261125200000 +0300"
        val parts = s.trim().split(' ')
        return try {
            val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val base = fmt.parse(parts[0])?.time ?: return null
            var offsetMs = 0L
            if (parts.size > 1) {
                val o = parts[1]
                if (o.length == 5 && (o[0] == '+' || o[0] == '-')) {
                    val sign = if (o[0] == '-') -1 else 1
                    val hh = o.substring(1, 3).toIntOrNull() ?: 0
                    val mm = o.substring(3, 5).toIntOrNull() ?: 0
                    offsetMs = sign * (hh * 3600_000L + mm * 60_000L)
                }
            }
            base - offsetMs
        } catch (e: Exception) {
            null
        }
    }
}
