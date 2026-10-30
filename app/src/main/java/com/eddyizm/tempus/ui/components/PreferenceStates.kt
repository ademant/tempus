package com.eddyizm.tempus.ui.components

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.eddyizm.tempus.util.StreamingPreferences

@Composable
fun rememberDataSavingMode(): Boolean {
    val context = LocalContext.current.applicationContext
    val state = remember { mutableStateOf(StreamingPreferences.isDataSavingMode()) }
    DisposableEffect(context) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            state.value = StreamingPreferences.isDataSavingMode()
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state.value
}
