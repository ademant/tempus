package com.eddyizm.tempus.util

import androidx.core.content.edit
import com.eddyizm.tempus.App
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Preferences {
    const val THEME = "theme"

    const val UPNP_ENABLED = "upnp_enabled"

    const val LAN_REMOTE_ENABLED = "lan_remote_enabled"

    private const val SKIP_MIN_STAR_RATING = "skip_min_star_rating"

    private const val DARK_THEME_STYLE = "dark_theme_style"

    const val MUSIC_FOLDER_ALL = "default"

    /*
     * Durable per-song resume points, kept outside the queue table because the queue is
     * rebuilt/emptied on every track switch (single-item timeline), which would otherwise wipe
     * saved positions. Each entry also carries enough metadata to render a "Continue listening"
     * overview without server lookups.
     */
    private const val RESUME_POINTS = "resume_points"

    // Hard cap on stored resume points. Ours is a single prefs string that every read re-parses
    // and every write re-serializes, so without a cap it grew to thousands of entries / multiple
    // MB of JSON churn per operation (see capResumePoints).
    private const val MAX_RESUME_POINTS = 200

    data class ResumePoint(
        val id: String,
        val position: Long,
        val title: String?,
        val album: String?,
        val artist: String?,
        val albumId: String?,
        val coverArtId: String?,
        // Media type (podcast/audiobook/...) so a card-resumed item is re-queued with the
        // same resumability it had when the point was saved (see isResumable in MediaManager).
        val type: String?,
        val timestamp: Long,
    )

    @JvmStatic
    fun saveResumePoint(id: String, position: Long, title: String?, album: String?, artist: String?, albumId: String?, coverArtId: String?, type: String?) {
        if (id.isNullOrBlank() || position <= 0L) return
        val pts = getResumePoints()
        pts[id] = ResumePoint(id, position, title, album, artist, albumId, coverArtId, type, System.currentTimeMillis())
        writeResumePoints(pts)
    }

    /** Bulk merge of server bookmarks into the local store - one read + one write, not N. */
    @JvmStatic
    fun saveResumePoints(points: Map<String, ResumePoint>) {
        if (points.isEmpty()) return
        val pts = getResumePoints()
        for ((id, incoming) in points) {
            val existing = pts[id]
            // Don't let a stale value (e.g. an old server bookmark synced in) clobber a
            // locally-written resume point that is newer. Fresher server entries still win.
            if (existing == null || incoming.timestamp >= existing.timestamp) {
                pts[id] = incoming
            }
        }
        writeResumePoints(pts)
    }

    /** Resume position (ms) for a song; 0 if none. */
    @JvmStatic
    fun getResumePoint(id: String): Long {
        return getResumePoints()[id]?.position ?: 0L
    }

    @JvmStatic
    fun clearResumePoint(id: String) {
        val pts = getResumePoints()
        if (pts.remove(id) != null) {
            writeResumePoints(pts)
        }
    }

    /** In-progress items for a "Continue listening" overview, most recent first. */
    @JvmStatic
    fun getContinueListening(): List<ResumePoint> {
        return getResumePoints().values.sortedByDescending { it.timestamp }
    }

    private fun getResumePoints(): MutableMap<String, ResumePoint> {
        val raw = App.getInstance().preferences.getString(RESUME_POINTS, null)
        return try {
            when {
                raw.isNullOrBlank() -> mutableMapOf()
                else -> Gson().fromJson(raw, object : TypeToken<MutableMap<String, ResumePoint>>() {}.type)
                    ?: mutableMapOf()
            }
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    /** Prune + persist. Every writer routes through here, so the cap holds in one place. */
    private fun writeResumePoints(pts: MutableMap<String, ResumePoint>) {
        App.getInstance().preferences.edit()
            .putString(RESUME_POINTS, Gson().toJson(capResumePoints(pts, MAX_RESUME_POINTS)))
            .apply()
    }
}

/**
 * Keep only the [max] most recently touched resume points, in place. Pure, so it is unit-tested
 * without SharedPreferences. Shared by every writer through [Preferences.writeResumePoints].
 */
internal fun capResumePoints(
    points: MutableMap<String, Preferences.ResumePoint>,
    max: Int
): MutableMap<String, Preferences.ResumePoint> {
    if (points.size <= max) return points
    val keep = points.entries.sortedByDescending { it.value.timestamp }.take(max)
    points.clear()
    for (entry in keep) points[entry.key] = entry.value
    return points
}
