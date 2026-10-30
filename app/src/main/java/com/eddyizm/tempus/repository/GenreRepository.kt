package com.eddyizm.tempus.repository

import androidx.lifecycle.MutableLiveData
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.models.Genre
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Genre list for the library and filter screens. The request runs on a repository-owned scope and
 * the result is delivered through LiveData, as before. A failed request leaves the LiveData empty.
 */
class GenreRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getGenres(random: Boolean, size: Int): MutableLiveData<List<Genre>> {
        val genres = MutableLiveData<List<Genre>>()

        scope.launch {
            val response = App.getSubsonicClientInstance(false)
                .browsingClient
                .getGenres()
                .awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            val container = response.body()?.subsonicResponse?.genres ?: return@launch
            val fetched = container.genres

            if (fetched == null || fetched.isEmpty()) {
                genres.value = emptyList()
                return@launch
            }

            val genreList = if (random) fetched.shuffled() else fetched

            genres.value = if (size != -1) {
                genreList.subList(0, minOf(size, genreList.size))
            } else {
                genreList.sortedBy { it.genre }
            }
        }

        return genres
    }
}
