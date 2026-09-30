package day.bark.android.projects

import org.json.JSONArray
import org.json.JSONObject

/** Immutable project order; an intentionally empty catalog stays empty. */
data class BarkProjectCatalog(val projects: List<BarkProject>) {
    fun save(project: BarkProject): BarkProjectCatalog {
        val checked = project.validated()
        val index = projects.indexOfFirst { it.id == checked.id }
        return copy(projects = projects.toMutableList().apply {
            if (index < 0) add(checked) else set(index, checked)
        })
    }

    fun remove(id: String): BarkProjectCatalog = copy(projects = projects.filterNot { it.id == id })

    fun move(id: String, offset: Int): BarkProjectCatalog {
        val index = projects.indexOfFirst { it.id == id }
        if (index < 0 || offset == 0) return this
        val destination = (index.toLong() + offset).coerceIn(0L, projects.lastIndex.toLong()).toInt()
        return copy(projects = projects.toMutableList().apply { add(destination, removeAt(index)) })
    }

    fun toJson(): String = JSONObject()
        .put("version", 1)
        .put("projects", JSONArray().apply {
            projects.forEach { project ->
                put(JSONObject()
                    .put("id", project.id)
                    .put("name", project.name)
                    .put("url", project.url)
                    .put("description", project.description)
                    .put("group", project.group ?: JSONObject.NULL))
            }
        }).toString()

    companion object {
        fun defaults(): BarkProjectCatalog = BarkProjectCatalog(listOf(
            BarkProject(
                id = "ic-basis",
                name = "IC 基差",
                url = "https://basis.atrl.me/",
                description = "查看 IC、IM、IF 的年化基差与历史走势",
                group = "ic-basis",
            ),
        ))

        fun fromJson(json: String?): BarkProjectCatalog {
            if (json.isNullOrBlank()) return defaults()
            return runCatching {
                val root = JSONObject(json)
                require(root.getInt("version") == 1)
                val entries = root.getJSONArray("projects")
                val ids = mutableSetOf<String>()
                BarkProjectCatalog(buildList {
                    for (index in 0 until entries.length()) {
                        val project = runCatching {
                            val entry = entries.getJSONObject(index)
                            BarkProject(
                                id = entry.getString("id"),
                                name = entry.getString("name"),
                                url = entry.getString("url"),
                                description = if (entry.has("description")) entry.getString("description") else "",
                                group = if (entry.isNull("group")) null else entry.getString("group"),
                            ).validated()
                        }.getOrNull() ?: continue
                        if (ids.add(project.id)) add(project)
                    }
                })
            }.getOrElse { BarkProjectCatalog(emptyList()) }
        }
    }
}
