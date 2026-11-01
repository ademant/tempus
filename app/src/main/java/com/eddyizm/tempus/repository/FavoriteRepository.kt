package com.eddyizm.tempus.repository

import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.database.dao.FavoriteDao
import com.eddyizm.tempus.interfaces.StarCallback
import com.eddyizm.tempus.model.Favorite
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.ResponseStatus
import com.eddyizm.tempus.util.FavoriteRegistry
import com.eddyizm.tempus.util.IoDispatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response

class FavoriteRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun star(id: String?, albumId: String?, artistId: String?, starCallback: StarCallback) {
        val record = remember(id, albumId, artistId, true)
        val call = App.getSubsonicClientInstance(false)
            .mediaAnnotationClient
            .star(id, albumId, artistId)
        send(call, record, starCallback)
    }

    fun unstar(id: String?, albumId: String?, artistId: String?, starCallback: StarCallback) {
        val record = remember(id, albumId, artistId, false)
        val call = App.getSubsonicClientInstance(false)
            .mediaAnnotationClient
            .unstar(id, albumId, artistId)
        send(call, record, starCallback)
    }

    fun getFavorites(): List<Favorite> = IoDispatch.blockingIo { favoriteDao().getAll() }

    fun starLater(id: String?, albumId: String?, artistId: String?, toStar: Boolean) {
        FavoriteRegistry.supersede(FavoriteRegistry.Kind.SONG, id, toStar)
        FavoriteRegistry.supersede(FavoriteRegistry.Kind.ALBUM, albumId, toStar)
        FavoriteRegistry.supersede(FavoriteRegistry.Kind.ARTIST, artistId, toStar)
        remember(id, albumId, artistId, toStar)
        // Timestamps are the queue table's key, so two rows in one millisecond would silently drop
        // the second.
        lastQueuedAt = maxOf(System.currentTimeMillis(), lastQueuedAt + 1)

        val favorite = Favorite(lastQueuedAt, id, albumId, artistId, toStar)
        IoDispatch.launch { favoriteDao().insert(favorite) }
    }

    fun delete(favorite: Favorite) {
        IoDispatch.launch { favoriteDao().delete(favorite) }
    }

    private fun send(call: Call<ApiResponse>, record: FavoriteRegistry.Record?, starCallback: StarCallback) {
        scope.launch {
            // A failed request (no response at all) is retried, the same as a non 2xx.
            val response = call.awaitOrNull()
            if (response == null) {
                retryOrWithdraw(record, starCallback)
                return@launch
            }
            when {
                response.isSuccessful && !serverRefused(response) -> {
                    FavoriteRegistry.accept(record)
                    starCallback.onSuccess()
                }
                serverRefused(response) -> {
                    FavoriteRegistry.strike(record)
                    starCallback.onRefused()
                }
                else -> retryOrWithdraw(record, starCallback)
            }
        }
    }

    private companion object {
        // AppDatabase is marked unstable, so every access goes through this helper, which carries the opt-in.
        @androidx.annotation.OptIn(UnstableApi::class)
        fun database(): AppDatabase = AppDatabase.getInstance()

        @androidx.annotation.OptIn(UnstableApi::class)
        fun favoriteDao(): FavoriteDao = database().favoriteDao()

        // Exactly one of the three ids is set on any call, so the caller's intent is recorded against
        // whichever one that is, under the kind that id belongs to.
        fun remember(id: String?, albumId: String?, artistId: String?, isStarred: Boolean): FavoriteRegistry.Record? {
            val song = FavoriteRegistry.set(FavoriteRegistry.Kind.SONG, id, isStarred)
            val album = FavoriteRegistry.set(FavoriteRegistry.Kind.ALBUM, albumId, isStarred)
            val artist = FavoriteRegistry.set(FavoriteRegistry.Kind.ARTIST, artistId, isStarred)
            return song ?: album ?: artist
        }

        // A non 2xx lands here too, since a proxy's 401 or 429 is answered by trying again later, not
        // by dropping the decision.
        fun retryOrWithdraw(record: FavoriteRegistry.Record?, starCallback: StarCallback) {
            if (FavoriteRegistry.isCurrent(record)) starCallback.onError()
            else FavoriteRegistry.withdraw(record)
        }

        // Subsonic reports a refusal as a 200 whose body says failed, so the status line alone cannot
        // tell a stored star from a rejected one.
        fun serverRefused(response: Response<ApiResponse>): Boolean =
            ResponseStatus.FAILED == response.body()?.subsonicResponse?.status

        private var lastQueuedAt = 0L
    }
}
