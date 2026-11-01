package com.eddyizm.tempus.viewmodel

import android.app.Dialog
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.repository.PlaylistRepository
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Playlist
import com.eddyizm.tempus.util.LibraryPreferences

@UnstableApi
class PlaylistChooserViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository()

    // Null until the user touches the switch. A false default went out on the wire and changed
    // the visibility of a playlist the user only meant to add a song to.
    private var playlistIsPublic: Boolean? = null

    private var toAdd: ArrayList<Child> = ArrayList()

    fun getIsPlaylistPublic(): Boolean? = playlistIsPublic

    fun setIsPlaylistPublic(isPublic: Boolean) {
        playlistIsPublic = isPublic
    }

    /**
     * Clears the visibility the user picked last time. This view model belongs to the activity, so
     * it outlives the dialog, while the switch is inflated unchecked every time. Without this the
     * two disagree and a later add sends a visibility the switch is not showing.
     */
    fun forgetVisibility() {
        playlistIsPublic = null
    }

    fun getPlaylistList(): LiveData<List<Playlist>> {
        playlistRepository.refreshAllPlaylists()
        val sortOrder = LibraryPreferences.getHomeSortPlaylists()
        return playlistRepository.getSortedPlaylists(sortOrder)
    }

    fun addSongsToPlaylist(owner: LifecycleOwner, dialog: Dialog, playlistId: String) {
        if (LibraryPreferences.allowPlaylistDuplicates()) {
            addRemaining(playlistId)
            dialog.dismiss()
        } else {
            playlistRepository.getPlaylistSongs(playlistId).observe(owner) { playlistSongs ->
                if (playlistSongs != null) {
                    // Removes songs the playlist already has from the list still to add. The old
                    // Guava view did the same, so this keeps that effect.
                    val existing = playlistSongs.map { it.id }.toSet()
                    toAdd.removeAll { it.id in existing }
                }
                addRemaining(playlistId)
                dialog.dismiss()
            }
        }
    }

    private fun addRemaining(playlistId: String) {
        playlistRepository.addSongToPlaylist(playlistId, ArrayList(toAdd.map { it.id }), getIsPlaylistPublic())
    }

    fun setSongsToAdd(songs: ArrayList<Child>) {
        toAdd = songs
    }

    fun getSongsToAdd(): ArrayList<Child> = toAdd
}
