package com.eddyizm.tempus.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.R
import com.eddyizm.tempus.repository.PodcastRepository
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.base.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException

class PodcastChannelEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val podcastRepository = PodcastRepository()

    private val isSuccess = MutableLiveData(false)
    private val errorMessage = MutableLiveData<String?>()

    fun getIsSuccess(): LiveData<Boolean> = isSuccess

    fun getErrorMessage(): LiveData<String?> = errorMessage

    fun clearError() {
        errorMessage.value = null
    }

    fun createChannel(url: String?) {
        errorMessage.value = null
        isSuccess.value = false

        val call = podcastRepository.createPodcastChannel(url)
        viewModelScope.launch {
            val response = try {
                call.await()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                showError("Network error: ${t.message}")
                Log.e(TAG, "Network error", t)
                return@launch
            }

            if (response.code() == 501) {
                showError(getApplication<Application>().getString(R.string.podcast_channel_not_supported_snackbar))
                return@launch
            }

            val body = response.body()
            if (response.isSuccessful && body != null) {
                if ("ok" == body.subsonicResponse.status) {
                    isSuccess.value = true
                }
            } else {
                handleHttpError(response)
            }
        }
    }

    private fun handleHttpError(response: Response<ApiResponse>) {
        var errorMsg = "HTTP error: ${response.code()}"
        response.errorBody()?.let { errorBody ->
            try {
                val serverMsg = errorBody.string()
                if (serverMsg.isNotEmpty()) {
                    errorMsg += " - $serverMsg"
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading error body", e)
            }
        }
        showError(errorMsg)
    }

    private fun showError(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
        errorMessage.value = message
        Log.e(TAG, "Error shown: $message")
    }

    private companion object {
        const val TAG = "PodcastChannelEditorViewModel"
    }
}
