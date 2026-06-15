package day.bark.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class BarkNotificationRef(
    val id: String,
    val group: String?,
)

class BarkMessageStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "bark_messages.db",
    null,
    2,
) {
    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                title TEXT,
                subtitle TEXT,
                body TEXT,
                display_body TEXT,
                body_type TEXT,
                url TEXT,
                image TEXT,
                icon TEXT,
                group_name TEXT,
                sound TEXT,
                badge INTEGER,
                level TEXT,
                volume REAL,
                call_value INTEGER,
                auto_copy INTEGER,
                copy_value TEXT,
                action TEXT,
                create_at INTEGER NOT NULL,
                expire_at INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_messages_create_at ON messages(create_at)")
        db.execSQL("CREATE INDEX idx_messages_group ON messages(group_name)")
        db.execSQL("CREATE INDEX idx_messages_expire_at ON messages(expire_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS messages")
        onCreate(db)
    }

    fun save(message: BarkMessage) {
        deleteExpired(System.currentTimeMillis())
        writableDatabase.insertWithOnConflict("messages", null, messageValues(message), SQLiteDatabase.CONFLICT_REPLACE)
        notifyHistoryChanged()
    }

    fun saveAll(messages: List<BarkMessage>) {
        if (messages.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            messages.forEach { message ->
                writableDatabase.insertWithOnConflict(
                    "messages",
                    null,
                    messageValues(message),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        notifyHistoryChanged()
    }

    fun exportBackupJson(): String =
        BarkMessageBackupCodec.encode(recent(limit = Int.MAX_VALUE))

    fun restoreBackupJson(json: String): Int {
        val messages = BarkMessageBackupCodec.decode(json)
        saveAll(messages)
        return messages.size
    }

    fun recent(limit: Int = 100, searchText: String? = null, group: String? = null): List<BarkMessage> {
        deleteExpired(System.currentTimeMillis())
        val query = searchText?.trim()?.takeIf { it.isNotBlank() }
        val selectedGroup = BarkHistoryView.normalizeGroup(group)
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        if (query != null) {
            selectionParts += "title LIKE ? OR subtitle LIKE ? OR body LIKE ? OR display_body LIKE ?"
            val like = "%$query%"
            selectionArgs += listOf(like, like, like, like)
        }
        if (selectedGroup != null) {
            selectionParts += "group_name = ?"
            selectionArgs += selectedGroup
        }
        val cursor = readableDatabase.query(
            "messages",
            null,
            selectionParts.takeIf { it.isNotEmpty() }?.joinToString(" AND ") { "($it)" },
            selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray(),
            null,
            null,
            "create_at DESC",
            limit.toString(),
        )
        cursor.use {
            val messages = mutableListOf<BarkMessage>()
            while (it.moveToNext()) {
                messages += BarkMessage(
                    id = it.string("id") ?: continue,
                    title = it.string("title"),
                    subtitle = it.string("subtitle"),
                    body = it.string("body"),
                    displayBody = it.string("display_body"),
                    bodyType = it.string("body_type"),
                    url = it.string("url"),
                    image = it.string("image"),
                    icon = it.string("icon"),
                    group = it.string("group_name"),
                    sound = it.string("sound"),
                    badge = it.intOrNull("badge"),
                    level = it.string("level"),
                    volume = it.floatOrNull("volume"),
                    call = it.int("call_value") == 1,
                    autoCopy = it.int("auto_copy") == 1,
                    copy = it.string("copy_value"),
                    action = it.string("action"),
                    isDelete = false,
                    shouldArchive = true,
                    createAtMillis = it.long("create_at"),
                    expireAtMillis = it.longOrNull("expire_at"),
                    extras = emptyMap(),
                )
            }
            return messages
        }
    }

    fun clear() {
        writableDatabase.delete("messages", null, null)
        notifyHistoryChanged()
    }

    fun delete(id: String) {
        writableDatabase.delete("messages", "id = ?", arrayOf(id))
        notifyHistoryChanged()
    }

    fun groupFor(id: String): String? {
        val cursor = readableDatabase.query(
            "messages",
            arrayOf("group_name"),
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        )
        cursor.use {
            return if (it.moveToFirst()) it.string("group_name") else null
        }
    }

    fun delete(range: BarkHistoryDeleteRange, nowMillis: Long = System.currentTimeMillis()) {
        val window = BarkHistoryDeletePolicy.window(range, nowMillis)
        if (window == null) {
            clear()
            return
        }
        writableDatabase.delete(
            "messages",
            "create_at >= ? AND create_at <= ?",
            arrayOf(window.startMillis.toString(), window.endMillis.toString()),
        )
        notifyHistoryChanged()
    }

    fun deleteGroup(group: String?) {
        val selectedGroup = BarkHistoryView.normalizeGroup(group)
        if (selectedGroup == null) {
            writableDatabase.delete(
                "messages",
                "group_name IS NULL OR TRIM(group_name) = ''",
                null,
            )
        } else {
            writableDatabase.delete(
                "messages",
                "group_name = ?",
                arrayOf(selectedGroup),
            )
        }
        notifyHistoryChanged()
    }

    fun deleteExpired(nowMillis: Long = System.currentTimeMillis()): List<BarkNotificationRef> {
        val expiredRefs = expiredNotificationRefs(nowMillis)
        if (expiredRefs.isNotEmpty()) {
            writableDatabase.delete(
                "messages",
                "expire_at IS NOT NULL AND expire_at <= ?",
                arrayOf(nowMillis.toString()),
            )
            notifyHistoryChanged()
        }
        return expiredRefs
    }

    private fun expiredNotificationRefs(nowMillis: Long): List<BarkNotificationRef> {
        val cursor = readableDatabase.query(
            "messages",
            arrayOf("id", "group_name"),
            "expire_at IS NOT NULL AND expire_at <= ?",
            arrayOf(nowMillis.toString()),
            null,
            null,
            null,
        )
        cursor.use {
            val refs = mutableListOf<BarkNotificationRef>()
            while (it.moveToNext()) {
                val id = it.string("id") ?: continue
                refs += BarkNotificationRef(id = id, group = it.string("group_name"))
            }
            return refs
        }
    }

    private fun notifyHistoryChanged() {
        BarkRecentMessagesWidgetProvider.updateAll(appContext)
    }

    private fun messageValues(message: BarkMessage): ContentValues =
        ContentValues().apply {
            put("id", message.id)
            put("title", message.title)
            put("subtitle", message.subtitle)
            put("body", message.body)
            put("display_body", message.displayBody)
            put("body_type", message.bodyType)
            put("url", message.url)
            put("image", message.image)
            put("icon", message.icon)
            put("group_name", message.group)
            put("sound", message.sound)
            put("badge", message.badge)
            put("level", message.level)
            put("volume", message.volume)
            put("call_value", if (message.call) 1 else 0)
            put("auto_copy", if (message.autoCopy) 1 else 0)
            put("copy_value", message.copy)
            put("action", message.action)
            put("create_at", message.createAtMillis)
            put("expire_at", message.expireAtMillis)
        }
}

private fun android.database.Cursor.index(name: String): Int = getColumnIndexOrThrow(name)
private fun android.database.Cursor.string(name: String): String? =
    getString(index(name))
private fun android.database.Cursor.int(name: String): Int =
    getInt(index(name))
private fun android.database.Cursor.intOrNull(name: String): Int? =
    if (isNull(index(name))) null else getInt(index(name))
private fun android.database.Cursor.floatOrNull(name: String): Float? =
    if (isNull(index(name))) null else getFloat(index(name))
private fun android.database.Cursor.long(name: String): Long =
    getLong(index(name))
private fun android.database.Cursor.longOrNull(name: String): Long? =
    if (isNull(index(name))) null else getLong(index(name))
