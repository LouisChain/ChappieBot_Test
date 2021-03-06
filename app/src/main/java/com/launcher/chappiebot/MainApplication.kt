package com.launcher.chappiebot

import android.app.Activity
import android.app.Application
import com.launcher.chappiebot.di.DaggerAppComponent
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasActivityInjector
import javax.inject.Inject

open class MainApplication : Application(), HasActivityInjector {

    @Inject
    lateinit var dispatchingActivityInjector: DispatchingAndroidInjector<Activity>

    override fun activityInjector() = dispatchingActivityInjector

    open fun inject() {
        DaggerAppComponent.builder()
                .create(this)
                .inject(this)
    }

    override fun onCreate() {
        super.onCreate()
        inject()
    }
}