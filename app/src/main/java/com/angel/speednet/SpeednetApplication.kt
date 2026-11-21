package com.angel.speednet

import android.app.Application
import com.angel.speednet.di.AppContainer

class SpeednetApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
