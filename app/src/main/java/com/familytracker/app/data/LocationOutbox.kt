package com.familytracker.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.familytracker.app.domain.LocationPoint
import org.json.JSONObject

/** Private, bounded, idempotent outbox. Oldest unsent points are evicted at 10,000 records. */
class LocationOutbox(context: Context) : SQLiteOpenHelper(context, "location_outbox.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE points (id TEXT PRIMARY KEY, uid TEXT NOT NULL, circle TEXT NOT NULL, time INTEGER NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE stops (uid TEXT NOT NULL, circle TEXT NOT NULL, PRIMARY KEY(uid,circle))")
        db.execSQL("CREATE INDEX points_time ON points(time)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun enqueue(p: LocationPoint) {
        val json = JSONObject().put("lat",p.latitude).put("lng",p.longitude).put("accuracy",p.accuracy)
            .put("speed",p.speed).put("battery",p.battery).put("interval",p.intervalMs)
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertWithOnConflict("points",null,ContentValues().apply {
                put("id",p.id);put("uid",p.uid);put("circle",p.circleId);put("time",p.deviceTime);put("payload",json.toString())
            },SQLiteDatabase.CONFLICT_IGNORE)
            writableDatabase.execSQL("DELETE FROM points WHERE id IN (SELECT id FROM points ORDER BY time DESC LIMIT -1 OFFSET 10000)")
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    @Synchronized fun pending(uid: String): List<LocationPoint> = readableDatabase.rawQuery(
        "SELECT id,uid,circle,time,payload FROM points WHERE uid=? ORDER BY time LIMIT 200",arrayOf(uid)).use { c ->
        buildList { while(c.moveToNext()) { val j=JSONObject(c.getString(4));add(LocationPoint(c.getString(0),c.getString(1),c.getString(2),
            j.getDouble("lat"),j.getDouble("lng"),j.getDouble("accuracy"),if(j.has("speed")) j.getDouble("speed") else null,
            if(j.has("battery")) j.getInt("battery") else null,c.getLong(3),intervalMs=j.getLong("interval"))) } }
    }
    @Synchronized fun remove(id: String) { writableDatabase.delete("points","id=?",arrayOf(id)) }
    @Synchronized fun clear(uid: String, circle: String? = null) {
        if(circle==null) writableDatabase.delete("points","uid=?",arrayOf(uid))
        else writableDatabase.delete("points","uid=? AND circle=?",arrayOf(uid,circle))
    }
    @Synchronized fun count(uid: String) = readableDatabase.rawQuery("SELECT COUNT(*) FROM points WHERE uid=?",arrayOf(uid)).use { it.moveToFirst();it.getInt(0) }
    @Synchronized fun markStopped(uid: String,circle: String) { writableDatabase.insertWithOnConflict("stops",null,ContentValues().apply { put("uid",uid);put("circle",circle) },SQLiteDatabase.CONFLICT_REPLACE) }
    @Synchronized fun pendingStops(uid: String): List<String> = readableDatabase.rawQuery("SELECT circle FROM stops WHERE uid=?",arrayOf(uid)).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
    @Synchronized fun clearStop(uid: String,circle: String) { writableDatabase.delete("stops","uid=? AND circle=?",arrayOf(uid,circle)) }
}
