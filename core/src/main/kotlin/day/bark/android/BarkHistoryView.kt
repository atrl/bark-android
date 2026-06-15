package day.bark.android

data class BarkHistoryGroup(
    val group: String?,
    val displayName: String,
    val totalCount: Int,
    val messages: List<BarkMessage>,
)

object BarkHistoryView {
    private const val DEFAULT_GROUP_NAME = "Default"

    fun filter(messages: List<BarkMessage>, selectedGroups: Set<String?>): List<BarkMessage> {
        val sorted = messages.sortedByDescending { it.createAtMillis }
        if (selectedGroups.isEmpty()) return sorted
        return sorted.filter { normalizeGroup(it.group) in selectedGroups.map(::normalizeGroup).toSet() }
    }

    fun search(messages: List<BarkMessage>, query: String?): List<BarkMessage> {
        val normalizedQuery = query?.trim()?.lowercase().orEmpty()
        val sorted = messages.sortedByDescending { it.createAtMillis }
        if (normalizedQuery.isBlank()) return sorted
        return sorted.filter { message ->
            listOf(message.title, message.subtitle, message.body, message.displayBody)
                .filterNotNull()
                .any { it.lowercase().contains(normalizedQuery) }
        }
    }

    fun groups(messages: List<BarkMessage>, previewLimit: Int = 5): List<BarkHistoryGroup> {
        val sorted = messages.sortedByDescending { it.createAtMillis }
        return sorted
            .groupBy { normalizeGroup(it.group) }
            .map { (group, groupMessages) ->
                BarkHistoryGroup(
                    group = group,
                    displayName = group ?: DEFAULT_GROUP_NAME,
                    totalCount = groupMessages.size,
                    messages = groupMessages.take(previewLimit),
                )
            }
    }

    fun normalizeGroup(group: String?): String? =
        group?.trim()?.takeIf { it.isNotBlank() }
}
