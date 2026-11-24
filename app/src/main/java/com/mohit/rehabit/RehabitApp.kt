package com.mohit.rehabit

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.mohit.rehabit.content.ContentRepository
import com.mohit.rehabit.data.AgendaRepository
import com.mohit.rehabit.data.BackupRepository
import com.mohit.rehabit.data.HistoryRepository
import com.mohit.rehabit.data.UserContentRepository
import com.mohit.rehabit.data.MIGRATION_1_2
import com.mohit.rehabit.data.MIGRATION_2_3
import com.mohit.rehabit.data.MIGRATION_3_4
import com.mohit.rehabit.data.RehabitDatabase
import com.mohit.rehabit.data.SettingsRepository
import com.mohit.rehabit.reminders.Notifier
import com.mohit.rehabit.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RehabitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        container.appScope.launch { container.scheduler.rescheduleAll() }
    }
}

/** Simple manual dependency container (no DI framework needed; easy to reach from receivers). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Every schema change needs a Migration here: never a destructive fallback.
    val db: RehabitDatabase = Room.databaseBuilder(context, RehabitDatabase::class.java, "rehabit.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()

    val content = ContentRepository(context.assets, db, appScope)

    val settings = SettingsRepository(context)
    val agenda = AgendaRepository(context, content, db, settings)
    val scheduler = ReminderScheduler(context, agenda)
    val history = HistoryRepository(content, db, settings, agenda)
    val userContent = UserContentRepository(context, db)
    val backup = BackupRepository(context, db, settings)

    init {
        agenda.onChanged = { scheduler.rescheduleAll() }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as RehabitApp).container
