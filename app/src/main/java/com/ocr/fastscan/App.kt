package com.ocr.fastscan

import android.app.Application
import android.content.pm.ApplicationInfo
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.ocr.fastscan.utils.AppDependencies

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        )
        // Инициализируем зависимости один раз на всё время жизни приложения
        AppDependencies.init(this)
    }
}
