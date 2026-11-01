/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eddyizm.tempus.navigation

import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.app.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Sets up edge-to-edge for the activity. The app's minimum is Android 10 (API 29), so the
 * pre-29 backports are gone.
 *
 * ```
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         setUpEdgeToEdge()
 *         super.onCreate(savedInstanceState)
 *         ...
 *     }
 * ```
 */
fun ComponentActivity.setUpEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val view = findViewById<android.view.View>(android.R.id.content)
    val resources = view.resources
    val transparent = ResourcesCompat.getColor(resources, android.R.color.transparent, theme)
    val isDarkMode = isDarkMode(resources)
    window.statusBarColor = transparent
    window.navigationBarColor = transparent
    val controller = WindowInsetsControllerCompat(window, view)
    controller.isAppearanceLightStatusBars = !isDarkMode
    controller.isAppearanceLightNavigationBars = !isDarkMode
}

private fun isDarkMode(resources: Resources): Boolean {
    return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
