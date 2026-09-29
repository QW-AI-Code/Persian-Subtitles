package com.qwaicode.persiansubtitles

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.work.Configuration as WorkConfiguration
import com.qwaicode.persiansubtitles.data.usage.TokenUsageRepository
import com.qwaicode.persiansubtitles.network.GeminiUsageHub
import com.qwaicode.persiansubtitles.work.Notifications

class PersianSubtitlesApp : Application(), WorkConfiguration.Provider {

    /** Every context derived from the app is Persian, whatever the phone is set to. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The user switching the system language must not switch the app to English.
        java.util.Locale.setDefault(AppLocale.PERSIAN)
    }

    override fun onCreate() {
        super.onCreate()
        java.util.Locale.setDefault(AppLocale.PERSIAN)
        Notifications.ensureChannel(this)
        // Every Gemini answer reports what it cost; the settings screen shows it.
        GeminiUsageHub.listener = TokenUsageRepository.get(this)
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)
            .build()
}
