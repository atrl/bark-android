package day.bark.android.projects

import android.content.Context
import java.util.UUID

class BarkProjectStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bark_projects", Context.MODE_PRIVATE)

    fun list(): List<BarkProject> = catalog().projects

    fun find(id: String): BarkProject? = list().firstOrNull { it.id == id }

    fun save(
        id: String? = null,
        name: String,
        url: String,
        description: String = "",
        group: String? = null,
    ): BarkProject {
        val project = BarkProject(id ?: UUID.randomUUID().toString(), name, url, description, group).validated()
        write(catalog().save(project))
        return project
    }

    fun remove(id: String) = write(catalog().remove(id))

    fun move(id: String, offset: Int) = write(catalog().move(id, offset))

    private fun catalog(): BarkProjectCatalog = BarkProjectCatalog.fromJson(prefs.getString("catalog", null))

    private fun write(catalog: BarkProjectCatalog) {
        prefs.edit().putString("catalog", catalog.toJson()).apply()
    }
}
