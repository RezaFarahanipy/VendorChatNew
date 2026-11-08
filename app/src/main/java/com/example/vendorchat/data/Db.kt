package com.example.vendorchat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** پایگاه دادهٔ ساده: وندور + فیلدهای انعطاف‌پذیر (کلید/مقدار) + جدول جستجوی FTS4. */
class Db(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "vendorchat_v2.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE vendor(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, updated INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE attr(id INTEGER PRIMARY KEY AUTOINCREMENT, vendor_id INTEGER NOT NULL, " +
                "k TEXT NOT NULL, v TEXT NOT NULL, secret INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX attr_vendor ON attr(vendor_id)")
        db.execSQL("CREATE VIRTUAL TABLE fts USING fts4(content)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------- خواندن ----------
    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM vendor", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    fun names(): List<VendorRow> {
        val out = ArrayList<VendorRow>()
        readableDatabase.rawQuery("SELECT id, name FROM vendor ORDER BY updated DESC", null).use { c ->
            while (c.moveToNext()) out.add(VendorRow(c.getLong(0), c.getString(1)))
        }
        return out
    }

    fun get(id: Long): VendorData? {
        val db = readableDatabase
        val name = db.rawQuery("SELECT name FROM vendor WHERE id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: return null
        val attrs = ArrayList<Attr>()
        db.rawQuery("SELECT k, v, secret FROM attr WHERE vendor_id=? ORDER BY id", arrayOf(id.toString())).use { c ->
            while (c.moveToNext()) {
                val secret = c.getInt(2) == 1
                val raw = c.getString(1)
                val v = if (secret) {
                    try {
                        Crypto.decrypt(raw)
                    } catch (e: Exception) {
                        "(قابل خواندن نیست)"
                    }
                } else raw
                attrs.add(Attr(c.getString(0), v, secret))
            }
        }
        return VendorData(id, name, attrs)
    }

    /** جستجوی FTS با رتبه‌بندی ساده (تعداد کلمات منطبق؛ نام وندور امتیاز بیشتر دارد). */
    fun search(q: String, limit: Int): List<VendorRow> {
        val match = Fa.toMatch(q) ?: return emptyList()
        val tokens = Fa.tokens(q)
        val all = names().associateBy { it.id }
        val rows = ArrayList<Pair<Long, String>>()
        readableDatabase.rawQuery("SELECT rowid, content FROM fts WHERE fts MATCH ? LIMIT 60", arrayOf(match)).use { c ->
            while (c.moveToNext()) rows.add(c.getLong(0) to c.getString(1))
        }
        return rows
            .mapNotNull { (id, content) ->
                val v = all[id] ?: return@mapNotNull null
                val nn = Fa.normalize(v.name)
                val score = tokens.count { content.contains(it) } + 2 * tokens.count { nn.contains(it) }
                v to score
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    // ---------- نوشتن ----------
    fun create(name: String): Long {
        val db = writableDatabase
        var id = -1L
        db.beginTransaction()
        try {
            id = db.insert("vendor", null, ContentValues().apply {
                put("name", name.trim())
                put("updated", System.currentTimeMillis())
            })
            reindex(db, id)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return id
    }

    fun setFields(id: Long, fields: Map<String, String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((k, v) in fields) {
                val secret = Keys.isSecret(k)
                if (k in Keys.MULTI) {
                    if (hasValue(db, id, k, v)) continue
                } else {
                    db.delete("attr", "vendor_id=? AND k=?", arrayOf(id.toString(), k))
                }
                db.insert("attr", null, ContentValues().apply {
                    put("vendor_id", id)
                    put("k", k)
                    put("v", if (secret) Crypto.encrypt(v) else v)
                    put("secret", if (secret) 1 else 0)
                })
            }
            touch(db, id)
            reindex(db, id)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun removeFields(id: Long, keys: List<String>): Int {
        val db = writableDatabase
        var n = 0
        db.beginTransaction()
        try {
            for (k in keys) n += db.delete("attr", "vendor_id=? AND k=?", arrayOf(id.toString(), k))
            touch(db, id)
            reindex(db, id)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return n
    }

    fun delete(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("attr", "vendor_id=?", arrayOf(id.toString()))
            db.delete("vendor", "id=?", arrayOf(id.toString()))
            db.delete("fts", "rowid=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- کمکی ----------
    private fun hasValue(db: SQLiteDatabase, id: Long, k: String, v: String): Boolean {
        val target = Fa.normalize(v)
        db.rawQuery("SELECT v, secret FROM attr WHERE vendor_id=? AND k=?", arrayOf(id.toString(), k)).use { c ->
            while (c.moveToNext()) {
                val plain = if (c.getInt(1) == 1) {
                    try {
                        Crypto.decrypt(c.getString(0))
                    } catch (e: Exception) {
                        ""
                    }
                } else c.getString(0)
                if (Fa.normalize(plain) == target) return true
            }
        }
        return false
    }

    private fun touch(db: SQLiteDatabase, id: Long) {
        db.execSQL("UPDATE vendor SET updated=? WHERE id=?", arrayOf<Any>(System.currentTimeMillis(), id))
    }

    /** متن جستجو = نام + همهٔ فیلدهای غیرمحرمانه (نرمال‌شده، با نسخهٔ فقط‌رقم برای شماره‌ها). */
    private fun reindex(db: SQLiteDatabase, id: Long) {
        val name = db.rawQuery("SELECT name FROM vendor WHERE id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: return
        val parts = ArrayList<String>()
        parts.add(name)
        db.rawQuery("SELECT k, v FROM attr WHERE vendor_id=? AND secret=0", arrayOf(id.toString())).use { c ->
            while (c.moveToNext()) {
                val k = c.getString(0)
                val v = c.getString(1)
                parts.add(k)
                parts.add(v)
                val digits = Fa.latinDigits(v).filter { it.isDigit() }
                if (digits.length >= 5) parts.add(digits)
            }
        }
        db.delete("fts", "rowid=?", arrayOf(id.toString()))
        db.insert("fts", null, ContentValues().apply {
            put("rowid", id)
            put("content", Fa.normalize(parts.joinToString(" ")))
        })
    }
}
