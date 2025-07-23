package com.solobolo.floatmate.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloatMateSharedPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("floatmate_prefs", Context.MODE_PRIVATE)

    // Bubble position
    var bubbleX: Int
        get() = prefs.getInt("bubble_x", 0)
        set(value) = prefs.edit { putInt("bubble_x", value) }

    var bubbleY: Int
        get() = prefs.getInt("bubble_y", 100)
        set(value) = prefs.edit { putInt("bubble_y", value) }

    // Sticky note
    var stickyNote: String
        get() = prefs.getString("sticky_note", "") ?: ""
        set(value) = prefs.edit { putString("sticky_note", value) }

    // Favorite apps
    var favoriteApps: Set<String>
        get() = prefs.getStringSet("favorite_apps", emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet("favorite_apps", value) }

    // Service settings
    var startOnBoot: Boolean
        get() = prefs.getBoolean("start_on_boot", false)
        set(value) = prefs.edit { putBoolean("start_on_boot", value) }

    // Add or remove favorite app
    fun addFavoriteApp(packageName: String) {
        val current = favoriteApps.toMutableSet()
        current.add(packageName)
        favoriteApps = current
    }

    fun removeFavoriteApp(packageName: String) {
        val current = favoriteApps.toMutableSet()
        current.remove(packageName)
        favoriteApps = current
    }

    fun toggleFavoriteApp(packageName: String) {
        if (favoriteApps.contains(packageName)) {
            removeFavoriteApp(packageName)
        } else {
            addFavoriteApp(packageName)
        }
    }
}