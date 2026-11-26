package com.ahmed.zaghlolplayer.tv

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device storage (private SharedPreferences): saved logins, favorites,
 * recents, resume positions and settings. Same model as the Windows Store.
 * Passwords live only on this device, never leave it.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("zaghlol_player_tv", Context.MODE_PRIVATE)

    var epgUrl: String
        get() = prefs.getString("epg_url", "") ?: ""
        set(v) = prefs.edit().putString("epg_url", v).apply()

    var lastLoginId: String
        get() = prefs.getString("last_login_id", "") ?: ""
        set(v) = prefs.edit().putString("last_login_id", v).apply()

    var volume: Int
        get() = prefs.getInt("volume", 80)
        set(v) = prefs.edit().putInt("volume", v.coerceIn(0, 100)).apply()

    // ---------------- logins ----------------

    fun loadLogins(): MutableList<LoginProfile> {
        val out = mutableListOf<LoginProfile>()
        val raw = prefs.getString("logins", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = try {
                    LoginKind.valueOf(o.optString("kind", "DEMO"))
                } catch (e: Exception) {
                    LoginKind.DEMO
                }
                out.add(
                    LoginProfile(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        kind = kind,
                        m3uUrl = o.optString("m3uUrl"),
                        m3uPath = o.optString("m3uPath"),
                        server = o.optString("server"),
                        username = o.optString("username"),
                        password = o.optString("password")
                    )
                )
            }
        } catch (e: Exception) {
            // Corrupt JSON: start fresh.
        }
        return out
    }

    private fun saveLogins(list: List<LoginProfile>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("kind", p.kind.name)
                    put("m3uUrl", p.m3uUrl)
                    put("m3uPath", p.m3uPath)
                    put("server", p.server)
                    put("username", p.username)
                    put("password", p.password)
                }
            )
        }
        prefs.edit().putString("logins", arr.toString()).apply()
    }

    /** Insert at top, or update the existing login with the same kind+url/server+user. */
    fun rememberLogin(p: LoginProfile): LoginProfile {
        val list = loadLogins()
        val existing = list.firstOrNull {
            it.kind == p.kind &&
                it.m3uUrl.equals(p.m3uUrl, ignoreCase = true) &&
                it.server.equals(p.server, ignoreCase = true) &&
                it.username.equals(p.username, ignoreCase = true)
        }
        val saved: LoginProfile
        if (existing != null) {
            saved = p.copy(id = existing.id)
            val idx = list.indexOf(existing)
            list[idx] = saved
        } else {
            saved = p
            list.add(0, saved)
        }
        saveLogins(list)
        lastLoginId = saved.id
        return saved
    }

    fun deleteLogin(id: String) {
        val list = loadLogins()
        list.removeAll { it.id == id }
        saveLogins(list)
        if (lastLoginId == id) lastLoginId = ""
    }

    // ---------------- favorites ----------------

    fun loadFavorites(): MutableSet<String> {
        return prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun toggleFavorite(key: String): Boolean {
        val set = loadFavorites()
        val nowFav = if (set.contains(key)) {
            set.remove(key); false
        } else {
            set.add(key); true
        }
        prefs.edit().putStringSet("favorites", set).apply()
        return nowFav
    }

    // ---------------- recents ----------------

    fun loadRecents(): MutableList<RecentItem> {
        val out = mutableListOf<RecentItem>()
        val raw = prefs.getString("recents", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    RecentItem(
                        kind = o.optString("kind"),
                        id = o.optString("id"),
                        name = o.optString("name"),
                        whenMs = o.optLong("whenMs")
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun addRecent(kind: String, id: String, name: String) {
        val list = loadRecents()
        list.removeAll { it.kind == kind && it.id == id }
        list.add(0, RecentItem(kind, id, name, System.currentTimeMillis()))
        while (list.size > 20) list.removeAt(list.size - 1)
        val arr = JSONArray()
        for (r in list) {
            arr.put(JSONObject().apply {
                put("kind", r.kind); put("id", r.id); put("name", r.name); put("whenMs", r.whenMs)
            })
        }
        prefs.edit().putString("recents", arr.toString()).apply()
    }

    // ---------------- resume ----------------

    fun loadResume(): MutableMap<String, Float> {
        val map = mutableMapOf<String, Float>()
        val raw = prefs.getString("resume", "{}") ?: "{}"
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optDouble(k, 0.0).toFloat()
            }
        } catch (e: Exception) {
        }
        return map
    }

    fun saveResume(key: String, fraction: Float) {
        val map = loadResume()
        if (fraction <= 0.01f || fraction >= 0.99f) map.remove(key) else map[key] = fraction
        val obj = JSONObject()
        for ((k, v) in map) obj.put(k, v.toDouble())
        prefs.edit().putString("resume", obj.toString()).apply()
    }

    // ---------------- wipe ----------------

    fun wipeAll() {
        prefs.edit().clear().apply()
    }
}
