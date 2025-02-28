package com.example.skoltechmapmeasurements.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to create DataStore singleton
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "measurement_settings")

class PreferencesManager(private val context: Context) {

    // Keys for preferences
    companion object {
        val BASE_URL_KEY = stringPreferencesKey("base_url")
        const val DEFAULT_BASE_URL = "http://192.168.7.48:8000"
    }

    // Get the base URL as a Flow
    val baseUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[BASE_URL_KEY] ?: DEFAULT_BASE_URL
    }

    // Save the base URL
    suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = baseUrl
        }
    }
}