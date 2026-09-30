package day.bark.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BarkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        day.bark.android.update.BarkUpdateScheduler.schedule(context)
        day.bark.android.update.BarkUpdateInstaller.reconcile(context)
        if (!BarkSettingsStore(context).listeningEnabled) {
            return
        }

        BarkDeliveryController.restore(context)
    }
}
