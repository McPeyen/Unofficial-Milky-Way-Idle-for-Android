package com.mcpeyen.unofficialmilkywayidle

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate


class McMilkyWayIdle : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}