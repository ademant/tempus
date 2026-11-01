package com.eddyizm.tempus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.models.ArtistID3
import kotlinx.coroutines.launch

class ArtistCatalogueViewModel(application: Application) : AndroidViewModel(application) {

    private val artistList = MutableLiveData<List<ArtistID3>>(ArrayList())

    fun getArtistList(): LiveData<List<ArtistID3>> = artistList

    fun loadArtists() {
        val call = App.getSubsonicClientInstance(false).browsingClient.getArtists()
        viewModelScope.launch {
            // A failed request leaves the list as it was, as the old callback did.
            val response = call.awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch

            val indices = response.body()?.subsonicResponse?.artists?.indices ?: return@launch
            val artists = ArrayList<ArtistID3>()
            for (index in indices) {
                artists.addAll(index.artists.orEmpty())
            }
            artistList.value = artists
        }
    }
}
