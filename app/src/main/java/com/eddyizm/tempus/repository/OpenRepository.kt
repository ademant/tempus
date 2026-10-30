package com.eddyizm.tempus.repository

import androidx.lifecycle.MutableLiveData
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.models.LyricsList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Lyrics from the OpenSubsonic endpoint. Runs on a repository-owned scope; LiveData stays empty on failure. */
class OpenRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getLyricsBySongId(id: String): MutableLiveData<LyricsList> {
        val lyricsList = MutableLiveData<LyricsList>()

        scope.launch {
            val response = App.getSubsonicClientInstance(false)
                .openClient
                .getLyricsBySongId(id)
                .awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            val lyrics = response.body()?.subsonicResponse?.lyricsList ?: return@launch
            lyricsList.value = lyrics
        }

        return lyricsList
    }
}
