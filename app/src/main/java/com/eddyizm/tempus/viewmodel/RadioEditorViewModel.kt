package com.eddyizm.tempus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.R
import com.eddyizm.tempus.repository.RadioRepository
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.InternetRadioStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response

class RadioEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val radioRepository = RadioRepository()
    private var toEdit: InternetRadioStation? = null
    var isLocal: Boolean = false

    private val isSuccess = MutableLiveData(false)
    private val errorMessage = MutableLiveData<String?>()

    fun getIsSuccess(): LiveData<Boolean> = isSuccess

    fun getErrorMessage(): LiveData<String?> = errorMessage

    fun clearError() {
        errorMessage.value = null
    }

    fun getRadioToEdit(): InternetRadioStation? = toEdit

    fun setRadioToEdit(internetRadioStation: InternetRadioStation?) {
        toEdit = internetRadioStation
        isLocal = internetRadioStation != null && radioRepository.isLocalStation(internetRadioStation.id)
    }

    fun isEditing(): Boolean = toEdit != null

    fun createRadio(name: String?, streamURL: String?, homepageURL: String?, coverArtUrl: String?) {
        errorMessage.value = null
        isSuccess.value = false

        if (isLocal) {
            radioRepository.createLocalStation(name, streamURL, homepageURL, coverArtUrl) {
                isSuccess.value = true
            }
        } else {
            sendStationRequest(
                radioRepository.createInternetRadioStation(name, streamURL, homepageURL),
                notSupportedOn501 = true,
            )
        }
    }

    fun updateRadio(name: String?, streamURL: String?, homepageURL: String?, coverArtUrl: String?) {
        val station = toEdit ?: return
        val id = station.id ?: return

        errorMessage.value = null
        isSuccess.value = false

        if (isLocal) {
            radioRepository.updateLocalStation(id, name, streamURL, homepageURL, coverArtUrl) {
                isSuccess.value = true
            }
        } else {
            sendStationRequest(radioRepository.updateInternetRadioStation(id, name, streamURL, homepageURL))
        }
    }

    fun deleteRadio() {
        val station = toEdit ?: return
        val id = station.id ?: return

        errorMessage.value = null
        isSuccess.value = false

        if (isLocal) {
            radioRepository.deleteLocalStation(id) {
                isSuccess.value = true
            }
        } else {
            sendStationRequest(radioRepository.deleteInternetRadioStation(id))
        }
    }

    private fun sendStationRequest(call: Call<ApiResponse>, notSupportedOn501: Boolean = false) {
        viewModelScope.launch {
            val response = try {
                call.await()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                errorMessage.value = "Network error: ${t.message}"
                return@launch
            }

            if (notSupportedOn501 && response.code() == 501) {
                showError(getApplication<Application>().getString(R.string.radio_dialog_not_supported_snackbar))
                return@launch
            }
            handleStationResponse(response)
        }
    }

    private fun handleStationResponse(response: Response<ApiResponse>) {
        val body = response.body()
        if (response.isSuccessful && body != null) {
            when (body.subsonicResponse.status) {
                "ok" -> isSuccess.value = true
                "failed" -> handleFailedResponse(body)
            }
        } else {
            errorMessage.value = "HTTP error: ${response.code()}"
        }
    }

    private fun showError(message: String) {
        errorMessage.value = message
    }

    private fun handleFailedResponse(apiResponse: ApiResponse) {
        var errorMsg: String? = "Unknown server error"

        val error = apiResponse.subsonicResponse.error
        if (error != null) {
            errorMsg = error.message
            if ("Not implemented" == errorMsg) {
                errorMsg = getApplication<Application>().getString(R.string.radio_dialog_not_supported_snackbar)
            }
        }

        errorMessage.value = errorMsg
    }
}
