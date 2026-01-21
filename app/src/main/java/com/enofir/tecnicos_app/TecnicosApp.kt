package com.enofir.tecnicos_app

import android.app.Application
import com.enofir.tecnicos_app.core.ApiClient

class TecnicosApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
    }
}
