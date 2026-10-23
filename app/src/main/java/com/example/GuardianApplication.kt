package com.example

import android.app.Application
import com.example.data.GuardianRepository

class GuardianApplication : Application() {
    lateinit var repository: GuardianRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = GuardianRepository(this)
    }

    companion object {
        lateinit var instance: GuardianApplication
            private set
    }
}
