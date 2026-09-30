package day.bark.android.projects

import android.app.Activity
import android.content.Context
import android.content.Intent

object BarkProjectNavigator {
    internal const val EXTRA_PROJECT_ID = "day.bark.android.project_id"

    fun intent(context: Context, projectId: String): Intent =
        Intent(context, BarkProjectWebActivity::class.java)
            .putExtra(EXTRA_PROJECT_ID, projectId)
            .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    fun open(context: Context, projectId: String) {
        context.startActivity(intent(context, projectId))
    }
}
