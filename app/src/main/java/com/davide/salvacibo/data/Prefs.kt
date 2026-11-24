package com.davide.salvacibo.data

import android.content.Context
import com.davide.salvacibo.domain.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Impostazioni salvate sul telefono (SharedPreferences), esposte come StateFlow. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun read() = Settings(
        notificationsEnabled = sp.getBoolean(KEY_ENABLED, true),
        notifyHour = sp.getInt(KEY_HOUR, 9),
        warnDaysUseBy = sp.getInt(KEY_WARN_USE_BY, 2),
        warnDaysBestBefore = sp.getInt(KEY_WARN_BEST_BEFORE, 3),
        warnDaysOpened = sp.getInt(KEY_WARN_OPENED, 2),
    )

    fun update(transform: (Settings) -> Settings): Settings {
        val s = transform(_settings.value)
        sp.edit()
            .putBoolean(KEY_ENABLED, s.notificationsEnabled)
            .putInt(KEY_HOUR, s.notifyHour)
            .putInt(KEY_WARN_USE_BY, s.warnDaysUseBy)
            .putInt(KEY_WARN_BEST_BEFORE, s.warnDaysBestBefore)
            .putInt(KEY_WARN_OPENED, s.warnDaysOpened)
            .apply()
        _settings.value = s
        return s
    }

    /** Ultimo giorno (epochDay) in cui il controllo notifiche è già stato fatto. */
    var lastCheckEpochDay: Long
        get() = sp.getLong(KEY_LAST_CHECK, -1L)
        set(value) = sp.edit().putLong(KEY_LAST_CHECK, value).apply()

    var askedNotificationPermission: Boolean
        get() = sp.getBoolean(KEY_ASKED_NOTIF, false)
        set(value) = sp.edit().putBoolean(KEY_ASKED_NOTIF, value).apply()

    /** Ultimo stato noto degli acquisti (ricontrollato con Google Play a ogni avvio). */
    var isSupporter: Boolean
        get() = sp.getBoolean(KEY_SUPPORTER, false)
        set(value) = sp.edit().putBoolean(KEY_SUPPORTER, value).apply()

    /** La richiesta di recensione in-app è già stata mostrata. */
    var reviewAsked: Boolean
        get() = sp.getBoolean(KEY_REVIEW_ASKED, false)
        set(value) = sp.edit().putBoolean(KEY_REVIEW_ASKED, value).apply()

    private companion object {
        const val KEY_ENABLED = "notifications_enabled"
        const val KEY_HOUR = "notify_hour"
        const val KEY_WARN_USE_BY = "warn_use_by"
        const val KEY_WARN_BEST_BEFORE = "warn_best_before"
        const val KEY_WARN_OPENED = "warn_opened"
        const val KEY_LAST_CHECK = "last_check_day"
        const val KEY_ASKED_NOTIF = "asked_notif"
        const val KEY_SUPPORTER = "supporter"
        const val KEY_REVIEW_ASKED = "review_asked"
    }
}
