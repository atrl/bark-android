package day.bark.android

object BarkExpiryText {
    fun format(expireAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String? {
        val expireAt = expireAtMillis ?: return null
        val remainingMillis = expireAt - nowMillis
        if (remainingMillis <= 0) return "Expired"

        val remainingSeconds = remainingMillis / 1000L
        val unit = when {
            remainingSeconds > SECONDS_PER_YEAR -> remainingSeconds / SECONDS_PER_YEAR to "y"
            remainingSeconds > SECONDS_PER_MONTH -> remainingSeconds / SECONDS_PER_MONTH to "mo"
            remainingSeconds > SECONDS_PER_DAY -> remainingSeconds / SECONDS_PER_DAY to "d"
            remainingSeconds > SECONDS_PER_HOUR -> remainingSeconds / SECONDS_PER_HOUR to "h"
            remainingSeconds > SECONDS_PER_MINUTE -> remainingSeconds / SECONDS_PER_MINUTE to "m"
            else -> remainingSeconds to "s"
        }
        return "Expires in ${unit.first}${unit.second}"
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
    private const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR
    private const val SECONDS_PER_MONTH = 30L * SECONDS_PER_DAY
    private const val SECONDS_PER_YEAR = 365L * SECONDS_PER_DAY
}
