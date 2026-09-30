package day.bark.android

import android.app.Application

class BarkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BarkNotifier(this).ensureChannels()
        // FirebaseInitProvider reads generated resources when configured. Missing
        // config intentionally leaves Firebase unavailable and polling usable.
    }
}
