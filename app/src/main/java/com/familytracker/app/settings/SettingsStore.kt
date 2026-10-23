package com.familytracker.app.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.familytracker.app.domain.ReportingInterval
import kotlinx.coroutines.flow.*

private val Context.trackerStore by preferencesDataStore("tracker_settings")

class SettingsStore(context: Context) {
    private val store = context.trackerStore

    fun interval(uid: String) =
        store.data
            .map { ReportingInterval.fromMillis(it[longPreferencesKey("interval_$uid")] ?: 60_000) }
            .distinctUntilChanged()

    suspend fun setInterval(uid: String, value: ReportingInterval) {
        store.edit { it[longPreferencesKey("interval_$uid")] = value.millis }
    }

    fun circle(uid: String) =
        store.data.map { it[stringPreferencesKey("circle_$uid")] }.distinctUntilChanged()

    suspend fun setCircle(uid: String, id: String?) {
        store.edit {
            if (id == null) it.remove(stringPreferencesKey("circle_$uid"))
            else it[stringPreferencesKey("circle_$uid")] = id
        }
    }

    // Consent is deliberately not persisted: after process death, the user explicitly restarts
    // sharing.
    suspend fun clearAccount(uid: String) {
        setCircle(uid, null)
    }
}
