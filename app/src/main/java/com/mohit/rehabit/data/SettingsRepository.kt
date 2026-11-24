package com.mohit.rehabit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mohit.rehabit.content.Equipment
import com.mohit.rehabit.domain.DayPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/** Settings as stored in a backup file (masks and minutes, so it doesn't depend on enum classes). */
@Serializable
data class BackupSettings(
    val setupDone: Boolean,
    val wakeFollowsAlarm: Boolean,
    val wakeMin: Int,
    val morningMin: Int,
    val middayMin: Int,
    val eveningMin: Int,
    val nightMin: Int,
    val workdaysMask: Int,
    val deskBreaks: Boolean,
    val desk1Min: Int,
    val desk2Min: Int,
    val equipment: List<String>,
    val checkInDaysMask: Int,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val setupDone = booleanPreferencesKey("setup_done")
        val wakeFollowsAlarm = booleanPreferencesKey("wake_follows_alarm")
        val wake = intPreferencesKey("wake_min")
        val morning = intPreferencesKey("morning_min")
        val midday = intPreferencesKey("midday_min")
        val evening = intPreferencesKey("evening_min")
        val night = intPreferencesKey("night_min")
        val workdays = intPreferencesKey("workdays_mask")
        val deskBreaks = booleanPreferencesKey("desk_breaks")
        val desk1 = intPreferencesKey("desk1_min")
        val desk2 = intPreferencesKey("desk2_min")
        val equipment = stringSetPreferencesKey("equipment")
        val checkInDays = intPreferencesKey("check_in_days_mask")
        val coreOnlyDay = longPreferencesKey("core_only_day")
        val lastReminderHandled = longPreferencesKey("last_reminder_handled")
    }

    val flow: Flow<DayPrefs> = context.dataStore.data.map { p ->
        val d = DayPrefs()
        DayPrefs(
            setupDone = p[Keys.setupDone] ?: d.setupDone,
            wakeFollowsAlarm = p[Keys.wakeFollowsAlarm] ?: d.wakeFollowsAlarm,
            wakeMin = p[Keys.wake] ?: d.wakeMin,
            morningMin = p[Keys.morning] ?: d.morningMin,
            middayMin = p[Keys.midday] ?: d.middayMin,
            eveningMin = p[Keys.evening] ?: d.eveningMin,
            nightMin = p[Keys.night] ?: d.nightMin,
            workdays = p[Keys.workdays]?.let(::daysFromMask) ?: d.workdays,
            deskBreaks = p[Keys.deskBreaks] ?: d.deskBreaks,
            desk1Min = p[Keys.desk1] ?: d.desk1Min,
            desk2Min = p[Keys.desk2] ?: d.desk2Min,
            equipment = p[Keys.equipment]
                ?.mapNotNull { name -> runCatching { Equipment.valueOf(name) }.getOrNull() }
                ?.toSet() ?: d.equipment,
            checkInDays = p[Keys.checkInDays]?.let(::daysFromMask) ?: d.checkInDays,
            coreOnlyDay = p[Keys.coreOnlyDay] ?: d.coreOnlyDay,
        )
    }

    suspend fun get(): DayPrefs = flow.first()

    /** Saves the whole day setup (and marks first-run setup as done). */
    suspend fun saveDay(d: DayPrefs) {
        context.dataStore.edit { p ->
            p[Keys.setupDone] = true
            p[Keys.wakeFollowsAlarm] = d.wakeFollowsAlarm
            p[Keys.wake] = d.wakeMin
            p[Keys.morning] = d.morningMin
            p[Keys.midday] = d.middayMin
            p[Keys.evening] = d.eveningMin
            p[Keys.night] = d.nightMin
            p[Keys.workdays] = maskFromDays(d.workdays)
            p[Keys.deskBreaks] = d.deskBreaks
            p[Keys.desk1] = d.desk1Min
            p[Keys.desk2] = d.desk2Min
            p[Keys.equipment] = d.equipment.map { it.name }.toSet()
            p[Keys.checkInDays] = maskFromDays(d.checkInDays)
        }
    }

    suspend fun exportSettings(): BackupSettings {
        val d = get()
        return BackupSettings(
            setupDone = d.setupDone,
            wakeFollowsAlarm = d.wakeFollowsAlarm,
            wakeMin = d.wakeMin,
            morningMin = d.morningMin,
            middayMin = d.middayMin,
            eveningMin = d.eveningMin,
            nightMin = d.nightMin,
            workdaysMask = maskFromDays(d.workdays),
            deskBreaks = d.deskBreaks,
            desk1Min = d.desk1Min,
            desk2Min = d.desk2Min,
            equipment = d.equipment.map { it.name },
            checkInDaysMask = maskFromDays(d.checkInDays),
        )
    }

    suspend fun importSettings(b: BackupSettings) {
        saveDay(
            DayPrefs(
                wakeFollowsAlarm = b.wakeFollowsAlarm,
                wakeMin = b.wakeMin,
                morningMin = b.morningMin,
                middayMin = b.middayMin,
                eveningMin = b.eveningMin,
                nightMin = b.nightMin,
                workdays = daysFromMask(b.workdaysMask),
                deskBreaks = b.deskBreaks,
                desk1Min = b.desk1Min,
                desk2Min = b.desk2Min,
                equipment = b.equipment.mapNotNull { name -> runCatching { Equipment.valueOf(name) }.getOrNull() }.toSet(),
                checkInDays = daysFromMask(b.checkInDaysMask),
            ),
        )
        if (!b.setupDone) context.dataStore.edit { it[Keys.setupDone] = false }
    }

    suspend fun setCoreOnlyDay(day: Long) {
        context.dataStore.edit { it[Keys.coreOnlyDay] = day }
    }

    suspend fun lastReminderHandled(): Long = context.dataStore.data.first()[Keys.lastReminderHandled] ?: 0L

    suspend fun setLastReminderHandled(millis: Long) {
        context.dataStore.edit { it[Keys.lastReminderHandled] = millis }
    }

    private fun maskFromDays(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

    private fun daysFromMask(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
}
