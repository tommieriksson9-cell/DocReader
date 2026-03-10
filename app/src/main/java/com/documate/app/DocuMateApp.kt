package com.documate.app

import android.app.Application
import androidx.multidex.MultiDexApplication

class DocuMateApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        // Suppress Apache POI logging on Android
        System.setProperty("org.apache.poi.util.POILogger", "org.apache.poi.util.NullLogger")
    }
}
