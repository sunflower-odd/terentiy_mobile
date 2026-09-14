package ru.terentiy.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Точка входа приложения.
 *
 * Технический журнал включается только в отладочной сборке: пользователю технические
 * коды и подробности не показываются ни при каких условиях (stage-01-mobile.md §6).
 */
@HiltAndroidApp
class TerentiyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
