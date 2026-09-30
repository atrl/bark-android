package day.bark.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** Durable receive ledger, deliberately separate from optional/deletable history. */
class BarkInboxStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "bark_inbox.db", null, 1), BarkDeliveryInbox {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE inbox (
                source TEXT NOT NULL, delivery_id TEXT NOT NULL, payload TEXT NOT NULL,
                created_at INTEGER NOT NULL, fcm_accepted INTEGER NOT NULL, notification_tag TEXT,
                processed INTEGER NOT NULL DEFAULT 0, notification_handled INTEGER NOT NULL DEFAULT 0,
                acknowledged INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(source, delivery_id)
            )
        """.trimIndent())
        db.execSQL("CREATE TABLE foreground_requests (source TEXT NOT NULL, delivery_id TEXT NOT NULL, PRIMARY KEY(source, delivery_id))")
        db.execSQL("CREATE TABLE opened_notifications (delivery_id TEXT PRIMARY KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun markOpened(deliveryId: String) {
        writableDatabase.insertWithOnConflict("opened_notifications", null, ContentValues().apply {
            put("delivery_id", deliveryId)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun wasOpened(deliveryId: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM opened_notifications WHERE delivery_id=?", arrayOf(deliveryId),
    ).use { it.moveToFirst() }

    override fun stage(source: String, delivery: BarkDelivery) {
        val values = ContentValues().apply {
            put("source", source)
            put("delivery_id", delivery.deliveryId)
            put("payload", JSONObject(delivery.payload).toString())
            put("created_at", delivery.createdAtMillis)
            put("fcm_accepted", if (delivery.fcmAccepted) 1 else 0)
            put("notification_tag", delivery.notificationTag)
        }
        writableDatabase.insertWithOnConflict("inbox", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun requestForeground(source: String, deliveryId: String) {
        writableDatabase.insertWithOnConflict("foreground_requests", null, ContentValues().apply {
            put("source", source)
            put("delivery_id", deliveryId)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun pending(source: String): List<BarkInboxEntry> = readableDatabase.rawQuery("""
        SELECT i.*, EXISTS(SELECT 1 FROM foreground_requests f WHERE f.source=i.source AND f.delivery_id=i.delivery_id) AS requested
        FROM inbox i WHERE source=? AND (processed=0 OR (notification_handled=0 AND EXISTS(
            SELECT 1 FROM foreground_requests f WHERE f.source=i.source AND f.delivery_id=i.delivery_id)))
        ORDER BY created_at, rowid
    """.trimIndent(), arrayOf(source)).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                fun text(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
                fun flag(name: String) = cursor.getInt(cursor.getColumnIndexOrThrow(name)) == 1
                val payload = JSONObject(text("payload"))
                add(BarkInboxEntry(
                    delivery = BarkDelivery(text("delivery_id"), payload.keys().asSequence().associateWith { payload.get(it) },
                        cursor.getLong(cursor.getColumnIndexOrThrow("created_at")), flag("fcm_accepted"), text("notification_tag")),
                    processed = flag("processed"), notificationHandled = flag("notification_handled") || wasOpened(text("delivery_id")),
                    foregroundRequested = flag("requested"), acknowledged = flag("acknowledged"),
                ))
            }
        }
    }

    override fun markProcessed(source: String, deliveryId: String, notificationHandled: Boolean) {
        writableDatabase.update("inbox", ContentValues().apply {
            put("processed", 1)
            put("notification_handled", if (notificationHandled) 1 else 0)
        }, "source=? AND delivery_id=?", arrayOf(source, deliveryId))
    }

    override fun awaitingAck(source: String): List<String> = readableDatabase.rawQuery(
        "SELECT delivery_id FROM inbox WHERE source=? AND processed=1 AND acknowledged=0", arrayOf(source),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    override fun markAcknowledged(source: String, deliveryIds: List<String>) {
        writableDatabase.beginTransaction()
        try {
            deliveryIds.forEach { id ->
                writableDatabase.execSQL("UPDATE inbox SET acknowledged=1, payload='{}' WHERE source=? AND delivery_id=?", arrayOf(source, id))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /** Keep pending messages indefinitely; bound completed receipts to 7 days/5000 rows. */
    fun prune(now: Long = System.currentTimeMillis()) {
        writableDatabase.execSQL("DELETE FROM inbox WHERE acknowledged=1 AND created_at<?", arrayOf(now - 7L * 24 * 60 * 60 * 1000))
        writableDatabase.execSQL("DELETE FROM inbox WHERE rowid IN (SELECT rowid FROM inbox WHERE acknowledged=1 ORDER BY created_at DESC LIMIT -1 OFFSET 5000)")
        writableDatabase.execSQL("DELETE FROM opened_notifications WHERE delivery_id NOT IN (SELECT delivery_id FROM inbox) AND rowid < (SELECT MAX(rowid)-5000 FROM opened_notifications)")
        writableDatabase.execSQL("DELETE FROM foreground_requests WHERE rowid < (SELECT MAX(rowid)-5000 FROM foreground_requests)")
        writableDatabase.execSQL("DELETE FROM foreground_requests WHERE EXISTS(SELECT 1 FROM inbox i WHERE i.source=foreground_requests.source AND i.delivery_id=foreground_requests.delivery_id AND i.notification_handled=1)")
    }
}
