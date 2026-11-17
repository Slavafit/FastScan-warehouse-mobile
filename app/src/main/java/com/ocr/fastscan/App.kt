package com.ocr.fastscan

import android.app.Application
import com.ocr.fastscan.utils.AppDependencies

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCheckProviderInstaller.install()
        // Инициализируем зависимости один раз на всё время жизни приложения
        AppDependencies.init(this)
    }
}
