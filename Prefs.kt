package com.localshelf.app

import android.content.Context
import org.json.JSONObject

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("localshelf", Context.MODE_PRIVATE)

    fun folders(): Set<String> = p.getStringSet("folders", emptySet())?.toSet() ?: emptySet()

    fun addFolder(uri: String) {
        p.edit().putStringSet("folders", folders() + uri).apply()
    }

    fun removeFolder(uri: String) {
        p.edit().putStringSet("folders", folders() - uri).apply()
    }

    fun progress(uri: String): Long = runCatching {
        JSONObject(p.getString("progress", "{}") ?: "{}").optLong(uri, 0L)
    }.getOrDefault(0L)

    fun saveProgress(uri: String, value: Long) {
        val obj = runCatching { JSONObject(p.getString("progress", "{}") ?: "{}") }.getOrElse { JSONObject() }
        obj.put(uri, value.coerceAtLeast(0L))
        p.edit().putString("progress", obj.toString()).apply()
    }

    fun clearProgress(uri: String) {
        val obj = runCatching { JSONObject(p.getString("progress", "{}") ?: "{}") }.getOrElse { JSONObject() }
        obj.remove(uri)
        p.edit().putString("progress", obj.toString()).apply()
    }

    fun favorites(): Set<String> = p.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
    fun isFavorite(uri: String): Boolean = uri in favorites()

    fun toggleFavorite(uri: String): Boolean {
        val next = favorites().toMutableSet()
        val nowFavorite = if (uri in next) {
            next.remove(uri)
            false
        } else {
            next.add(uri)
            true
        }
        p.edit().putStringSet("favorites", next).apply()
        return nowFavorite
    }

    fun watched(): Set<String> = p.getStringSet("watched", emptySet())?.toSet() ?: emptySet()
    fun isWatched(uri: String): Boolean = uri in watched()

    fun setWatched(uri: String, watched: Boolean) {
        val next = this.watched().toMutableSet()
        if (watched) next.add(uri) else next.remove(uri)
        p.edit().putStringSet("watched", next).apply()
        if (watched) clearProgress(uri)
    }
}
