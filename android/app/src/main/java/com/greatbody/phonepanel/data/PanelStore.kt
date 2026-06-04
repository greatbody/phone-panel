package com.greatbody.phonepanel.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "phone_panel")

class PanelStore(private val context: Context) {

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_BRIGHTNESS = floatPreferencesKey("brightness")
    }

    val pairing: Flow<Pairing?> = context.dataStore.data.map { p ->
        val url = p[KEY_BASE_URL] ?: return@map null
        val token = p[KEY_TOKEN] ?: return@map null
        Pairing(url, token)
    }

    /** 0..1，-1 表示沿用系统亮度 */
    val brightness: Flow<Float> = context.dataStore.data.map { p ->
        p[KEY_BRIGHTNESS] ?: -1f
    }

    suspend fun savePairing(p: Pairing) {
        context.dataStore.edit { it[KEY_BASE_URL] = p.baseUrl; it[KEY_TOKEN] = p.token }
    }

    suspend fun clearPairing() {
        context.dataStore.edit { it.remove(KEY_BASE_URL); it.remove(KEY_TOKEN) }
    }

    suspend fun saveBrightness(v: Float) {
        context.dataStore.edit { it[KEY_BRIGHTNESS] = v.coerceIn(-1f, 1f) }
    }
}
