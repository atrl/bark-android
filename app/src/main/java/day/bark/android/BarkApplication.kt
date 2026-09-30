package day.bark.android

import android.app.Application

class BarkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BarkNotifier(this).ensureChannels()
        day.bark.android.projects.data.BasisRefreshScheduler.configure(
            this, day.bark.android.projects.widget.BarkProjectWidgetProvider.configuredFamilies(this),
        )
        // FirebaseInitProvider reads generated resources when configured. Missing
        // config intentionally leaves Firebase unavailable and polling usable.
    }
}
