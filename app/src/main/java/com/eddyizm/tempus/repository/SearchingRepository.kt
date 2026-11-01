package com.eddyizm.tempus.repository

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.database.dao.RecentSearchDao
import com.eddyizm.tempus.model.RecentSearch
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Playlist
import com.eddyizm.tempus.subsonic.models.PlaylistWithSongs
import com.eddyizm.tempus.subsonic.models.SearchResult2
import com.eddyizm.tempus.subsonic.models.SearchResult3
import com.eddyizm.tempus.ui.fragment.SearchFragment
import com.eddyizm.tempus.util.IoDispatch
import com.eddyizm.tempus.util.LibraryPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException

class SearchingRepository {
    private val recentSearchDao: RecentSearchDao
        @androidx.annotation.OptIn(UnstableApi::class)
        get() = database().recentSearchDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun search2(query: String?): MutableLiveData<SearchResult2?> {
        val result = MutableLiveData<SearchResult2?>()
        val call = App.getSubsonicClientInstance(false)
            .searchingClient
            .search3(query, 20, 0, 20, 0, 20, 0)

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            val body = response.body()
            if (response.isSuccessful && body != null) {
                result.value = body.subsonicResponse.searchResult2
            }
        }
        return result
    }

    @UnstableApi
    fun search3(sf: SearchFragment, query: String?): MutableLiveData<SearchResult3?> {
        val result = MutableLiveData<SearchResult3?>()

        IoDispatch.launch {
            // Pages through every match on the background thread, then hands the list to the fragment.
            val allSongs = ArrayList<Child>()
            var offset = 0
            val limit = 1000
            var hasMore = true

            while (hasMore) {
                try {
                    val response = App.getSubsonicClientInstance(false)
                        .searchingClient
                        .search3(query, limit, offset, 0, 0, 0, 0)
                        .execute()

                    val songs = if (response.isSuccessful) {
                        response.body()?.subsonicResponse?.searchResult3?.songs
                    } else {
                        null
                    }
                    if (!songs.isNullOrEmpty()) {
                        allSongs.addAll(songs)
                        offset += songs.size
                        hasMore = songs.size == limit
                    } else {
                        hasMore = false
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    hasMore = false
                }
            }

            val pws = PlaylistWithSongs("allsongs", allSongs)
            pws.songCount = allSongs.size
            val lpws = ArrayList<Playlist>()
            lpws.add(pws)
            pws.duration = allSongs.sumOf { it.duration?.toLong() ?: 0L }

            Handler(Looper.getMainLooper()).post {
                val view = sf.view
                if (view != null && sf.isAdded) {
                    pws.name = view.context.getString(R.string.search_all_songs, allSongs.size.toString())
                    sf.updateUI(lpws)
                }
            }
        }

        val call = App.getSubsonicClientInstance(false)
            .searchingClient
            .search3(query, 20, 0, 20, 0, 20, 0)
        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            val body = response.body()
            if (response.isSuccessful && body != null) {
                result.value = body.subsonicResponse.searchResult3
            }
        }

        return result
    }

    fun getSuggestions(query: String?): MutableLiveData<List<String>> {
        val suggestions = MutableLiveData<List<String>>()
        val call = App.getSubsonicClientInstance(false)
            .searchingClient
            .search3(query, 5, 0, 5, 0, 5, 0)

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            val result3 = response.body()?.subsonicResponse?.searchResult3
            if (!response.isSuccessful || result3 == null) return@launch

            val names = ArrayList<String>()
            result3.artists?.mapNotNullTo(names) { it.name }
            result3.albums?.mapNotNullTo(names) { it.name }
            result3.songs?.mapNotNullTo(names) { it.title }

            // distinct() keeps the first occurrence of each name, in order, like a LinkedHashSet.
            suggestions.value = names.distinct()
        }

        return suggestions
    }

    fun insert(recentSearch: RecentSearch) {
        IoDispatch.launch { recentSearchDao.insert(recentSearch) }
    }

    fun delete(recentSearch: RecentSearch) {
        IoDispatch.launch { recentSearchDao.delete(recentSearch) }
    }

    fun getRecentSearchSuggestion(): List<String> = IoDispatch.blockingIo {
        if (LibraryPreferences.isSearchSortingChronologicallyEnabled()) {
            recentSearchDao.getRecent()
        } else {
            recentSearchDao.getAlpha()
        }
    }

    private companion object {
        // AppDatabase is marked unstable, so every access goes through this helper, which carries the opt-in.
        @androidx.annotation.OptIn(UnstableApi::class)
        fun database(): AppDatabase = AppDatabase.getInstance()
    }
}
