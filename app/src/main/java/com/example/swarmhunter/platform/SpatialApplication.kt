package com.example.swarmhunter.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.swarmhunter.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
