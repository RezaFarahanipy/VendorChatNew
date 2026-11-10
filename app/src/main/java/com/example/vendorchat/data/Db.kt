package com.example.vendorchat.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.vendorchat.chat.VendorAction

/**
 * پایگاه داده: وندور + مشخصه‌های ساده (با برچسب) + آدرس‌ها + پرسنل + حساب‌های بانکی + جدول جستجوی FTS4.
 * اطلاعات حساب بانکی (شماره حساب، شبا، صاحب حساب) رمزگذاری‌شده ذخیره می‌شود.
 */
class Db(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "vendorchat_v2.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE vendor(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, updated INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE attr(id INTEGER PRIMARY KEY AUTOINCREMENT, vendor_id INTEGER NOT NULL, " +
                "k TEXT NOT NULL, v TEXT NOT NULL, secret INTEGER NOT NULL DEFAULT 0, label TEXT)"
        )
        db.execSQL("CREATE INDEX attr_vendor ON attr(vendor_id)")
        createV2Tables(db)
        db.execSQL("CREATE VIRTUAL TABLE fts USING fts4(content)")
    }

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE address(id INTEGER PRIMARY KEY AUTOINCREMENT, vendor_id INTEGER NOT NULL, kind TEXT NOT NULL, " +
                "province TEXT, city TEXT, street TEXT, postal TEXT, lat REAL, lng REAL)"
        )
        db.execSQL("CREATE INDEX address_vendor ON address(vendor_id)")
        db.execSQL(
            "CREATE TABLE person(id INTEGER PRIMARY KEY AUTOINCREMENT, vendor_id INTEGER NOT NULL, name TEXT NOT NULL, " +
                "role TEXT, mobile TEXT, phone TEXT, email TEXT, note TEXT)"
        )
        db.execSQL("CREATE INDEX person_vendor ON person(vendor_id)")
        db.execSQL(
            "CREATE TABLE bank(id INTEGER PRIMARY KEY AUTOINCREMENT, vendor_id INTEGER NOT NULL, bank TEXT, branch TEXT, " +
                "account TEXT, sheba TEXT, holder TEXT)"
        )
        db.execSQL("CREATE INDEX bank_vendor ON bank(vendor_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE attr ADD COLUMN label TEXT")
            createV2Tables(db)
            try {
                migrateV1(db)
            } catch (e: Exception) {
                // مهاجرت ناموفق نباید برنامه را از کار بیندازد
            }
        }
    }

    /** انتقال دادهٔ قدیمی (آدرس، شهر، مسئول، بانک که قبلاً مشخصهٔ ساده بودند) به جدول‌های جدید. */
    private fun migrateV1(db: SQLiteDatabase) {
        val ids = ArrayList<Long>()
        db.rawQuery("SELECT id FROM vendor", null).use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
        for (id in ids) {
            fun vals(k: String): List<Pair<Long, String>> {
                val out = ArrayList<Pair<Long, String>>()
                db.rawQuery("SELECT id, v FROM attr WHERE vendor_id=? AND k=?", arrayOf(id.toString(), k)).use { c ->
                    while (c.moveToNext()) out.add(c.getLong(0) to c.getString(1))
                }
                return out
            }
            val street = vals("آدرس").firstOrNull()?.second
            val city = vals("شهر").firstOrNull()?.second
            val prov = vals("استان").firstOrNull()?.second
            val postal = vals("کد پستی").firstOrNull()?.second
            if (street != null || city != null || prov != null || postal != null) {
                db.insert("address", null, ContentValues().apply {
                    put("vendor_id", id); put("kind", "اصلی"); put("province", prov); put("city", city)
                    put("street", street); put("postal", postal)
                })
            }
            val role = vals("سمت").firstOrNull()?.second
            vals("مسئول").forEachIndexed { i, (_, nm) ->
                db.insert("person", null, ContentValues().apply {
                    put("vendor_id", id); put("name", nm); if (i == 0) put("role", role)
                })
            }
            val bankName = vals("بانک").firstOrNull()?.second
            val branch = vals("شعبه").firstOrNull()?.second
            var acc: String? = null
            var sheba: String? = null
            db.rawQuery("SELECT k, v FROM attr WHERE vendor_id=? AND (k='شماره حساب' OR k='شبا') AND secret=1", arrayOf(id.toString())).use { c ->
                while (c.moveToNext()) if (c.getString(0) == "شبا") sheba = c.getString(1) else acc = c.getString(1)
            }
            if (bankName != null || branch != null || acc != null || sheba != null) {
                db.insert("bank", null, ContentValues().apply {
                    put("vendor_id", id); put("bank", bankName); put("branch", branch)
                    put("account", acc); put("sheba", sheba)   // مقدارها از قبل رمزگذاری‌شده‌اند
                })
            }
            db.execSQL(
                "DELETE FROM attr WHERE vendor_id=? AND k IN ('آدرس','شهر','استان','کد پستی','مسئول','سمت','بانک','شعبه','شماره حساب','شبا')",
                arrayOf<Any>(id)
            )
            reindex(db, id)
        }
    }

    // ---------------- رمزگذاری ----------------
    private fun enc(s: String?): String? = if (s.isNullOrEmpty()) null else Crypto.encrypt(s)
    private fun dec(s: String?): String? = if (s.isNullOrEmpty()) null else try {
        Crypto.decrypt(s)
    } catch (e: Exception) {
        null
    }

    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
    private fun Cursor.dbl(i: Int): Double? = if (isNull(i)) null else getDouble(i)

    // ---------------- خواندن ----------------
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
        val a = id.toString()
        val name = db.rawQuery("SELECT name FROM vendor WHERE id=?", arrayOf(a)).use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: return null
        val attrs = ArrayList<Attr>()
        db.rawQuery("SELECT k, v, secret, label FROM attr WHERE vendor_id=? ORDER BY id", arrayOf(a)).use { c ->
            while (c.moveToNext()) {
                val secret = c.getInt(2) == 1
                val v = if (secret) (dec(c.getString(1)) ?: "(قابل خواندن نیست)") else c.getString(1)
                attrs.add(Attr(c.getString(0), v, secret, c.str(3)))
            }
        }
        val addrs = ArrayList<AddressData>()
        db.rawQuery("SELECT id, kind, province, city, street, postal, lat, lng FROM address WHERE vendor_id=? ORDER BY id", arrayOf(a)).use { c ->
            while (c.moveToNext()) addrs.add(AddressData(c.getLong(0), c.getString(1), c.str(2), c.str(3), c.str(4), c.str(5), c.dbl(6), c.dbl(7)))
        }
        val people = ArrayList<PersonData>()
        db.rawQuery("SELECT id, name, role, mobile, phone, email, note FROM person WHERE vendor_id=? ORDER BY id", arrayOf(a)).use { c ->
            while (c.moveToNext()) people.add(PersonData(c.getLong(0), c.getString(1), c.str(2), c.str(3), c.str(4), c.str(5), c.str(6)))
        }
        val banks = ArrayList<BankData>()
        db.rawQuery("SELECT id, bank, branch, account, sheba, holder FROM bank WHERE vendor_id=? ORDER BY id", arrayOf(a)).use { c ->
            while (c.moveToNext()) banks.add(BankData(c.getLong(0), c.str(1), c.str(2), dec(c.str(3)), dec(c.str(4)), dec(c.str(5))))
        }
        return VendorData(id, name, attrs, addrs, people, banks)
    }

    fun all(): List<VendorData> = names().mapNotNull { get(it.id) }

    /** جستجوی FTS با رتبه‌بندی ساده (تعداد کلمات منطبق؛ نام وندور امتیاز بیشتر دارد). */
    fun search(q: String, limit: Int): List<VendorRow> {
        val match = Fa.toMatch(q) ?: return emptyList()
        val tokens = Fa.tokens(q)
        val all = names().associateBy { it.id }
        val rows = ArrayList<Pair<Long, String>>()
        readableDatabase.rawQuery("SELECT rowid, content FROM fts WHERE fts MATCH ? LIMIT 200", arrayOf(match)).use { c ->
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

    // ---------------- نوشتن ----------------
    private inline fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val r = block(db)
            db.setTransactionSuccessful()
            return r
        } finally {
            db.endTransaction()
        }
    }

    fun create(name: String): Long = tx { db ->
        val id = db.insert("vendor", null, ContentValues().apply {
            put("name", name.trim())
            put("updated", System.currentTimeMillis())
        })
        reindex(db, id)
        id
    }

    fun rename(id: Long, name: String) = tx { db ->
        db.execSQL("UPDATE vendor SET name=? WHERE id=?", arrayOf<Any>(name.trim(), id))
        reindex(db, id)
    }

    fun delete(id: Long) = tx { db ->
        val a = arrayOf(id.toString())
        for (t in listOf("attr", "address", "person", "bank")) db.delete(t, "vendor_id=?", a)
        db.delete("vendor", "id=?", a)
        db.delete("fts", "rowid=?", a)
    }

    /** همهٔ تغییرات یک «اکشن» روی وندور را در یک تراکنش اعمال می‌کند. */
    fun apply(id: Long, a: VendorAction) = tx { db ->
        for ((k, v) in a.sets) putAttr(db, id, k, v, null)
        for ((k, vals) in a.lists) for (v in vals) putAttr(db, id, k, v, null)
        for ((k, lab, v) in a.phones) putAttr(db, id, k, v, lab.ifEmpty { null })
        for ((lab, v) in a.emails) putAttr(db, id, "ایمیل", v, lab.ifEmpty { null })
        for (x in a.addresses) putAddress(db, id, x)
        for (x in a.people) putPerson(db, id, x)
        for (x in a.banks) putBank(db, id, x)
        for ((w, d) in a.removes) removeThing(db, id, w, d)
        touch(db, id)
        reindex(db, id)
    }

    private fun putAttr(db: SQLiteDatabase, id: Long, key: String, value: String, label: String?) {
        val secret = Keys.isSecret(key)
        val a = id.toString()
        if (key in Keys.MULTI) {
            val target = Fa.normalize(value)
            var existingId: Long? = null
            db.rawQuery("SELECT id, v, secret FROM attr WHERE vendor_id=? AND k=?", arrayOf(a, key)).use { c ->
                while (c.moveToNext()) {
                    val plain = if (c.getInt(2) == 1) dec(c.getString(1)).orEmpty() else c.getString(1)
                    if (Fa.normalize(plain) == target) existingId = c.getLong(0)
                }
            }
            val eid = existingId
            if (eid != null) {
                if (label != null) db.execSQL("UPDATE attr SET label=? WHERE id=?", arrayOf<Any>(label, eid))
                return
            }
        } else {
            db.delete("attr", "vendor_id=? AND k=?", arrayOf(a, key))
        }
        db.insert("attr", null, ContentValues().apply {
            put("vendor_id", id)
            put("k", key)
            put("v", if (secret) Crypto.encrypt(value) else value)
            put("secret", if (secret) 1 else 0)
            put("label", label)
        })
    }

    private fun putAddress(db: SQLiteDatabase, id: Long, x: AddressIn) {
        val a = id.toString()
        var existing: Long? = null
        db.rawQuery("SELECT id, kind FROM address WHERE vendor_id=?", arrayOf(a)).use { c ->
            while (c.moveToNext()) if (Fa.normalize(c.getString(1)) == Fa.normalize(x.kind)) existing = c.getLong(0)
        }
        val cv = ContentValues()
        x.province?.let { cv.put("province", it) }
        x.city?.let { cv.put("city", it) }
        x.street?.let { cv.put("street", it) }
        x.postal?.let { cv.put("postal", it) }
        if (x.lat != null && x.lng != null) {
            cv.put("lat", x.lat)
            cv.put("lng", x.lng)
        }
        val eid = existing
        if (eid != null) {
            if (cv.size() > 0) db.update("address", cv, "id=?", arrayOf(eid.toString()))
        } else {
            cv.put("vendor_id", id)
            cv.put("kind", x.kind)
            db.insert("address", null, cv)
        }
    }

    private fun putPerson(db: SQLiteDatabase, id: Long, x: PersonIn) {
        val a = id.toString()
        var existing: Long? = null
        db.rawQuery("SELECT id, name FROM person WHERE vendor_id=?", arrayOf(a)).use { c ->
            while (c.moveToNext()) if (Fa.normalize(c.getString(1)) == Fa.normalize(x.name)) existing = c.getLong(0)
        }
        val cv = ContentValues()
        x.role?.let { cv.put("role", it) }
        x.mobile?.let { cv.put("mobile", it) }
        x.phone?.let { cv.put("phone", it) }
        x.email?.let { cv.put("email", it) }
        x.note?.let { cv.put("note", it) }
        val eid = existing
        if (eid != null) {
            if (cv.size() > 0) db.update("person", cv, "id=?", arrayOf(eid.toString()))
        } else {
            cv.put("vendor_id", id)
            cv.put("name", x.name)
            db.insert("person", null, cv)
        }
    }

    private fun putBank(db: SQLiteDatabase, id: Long, x: BankIn) {
        val a = id.toString()
        var existing: Long? = null
        db.rawQuery("SELECT id, bank, account FROM bank WHERE vendor_id=?", arrayOf(a)).use { c ->
            while (c.moveToNext()) {
                val acc = dec(c.str(2))
                val sameAcc = x.account != null && acc != null && acc == x.account
                val sameBank = x.account == null && x.bank != null && Fa.normalize(c.str(1).orEmpty()) == Fa.normalize(x.bank)
                if (sameAcc || sameBank) existing = c.getLong(0)
            }
        }
        val cv = ContentValues()
        x.bank?.let { cv.put("bank", it) }
        x.branch?.let { cv.put("branch", it) }
        enc(x.account)?.let { cv.put("account", it) }
        enc(x.sheba)?.let { cv.put("sheba", it) }
        enc(x.holder)?.let { cv.put("holder", it) }
        val eid = existing
        if (eid != null) {
            if (cv.size() > 0) db.update("bank", cv, "id=?", arrayOf(eid.toString()))
        } else {
            cv.put("vendor_id", id)
            db.insert("bank", null, cv)
        }
    }

    /** حذف یک چیز: کلید ساده، تلفن/ایمیل با جزئیات، آدرس با نوع، پرسنل با نام، بانک. */
    private fun removeThing(db: SQLiteDatabase, id: Long, what: String, detail: String) {
        val a = id.toString()
        val w = Fa.normalize(what)
        val d = Fa.normalize(detail)
        fun rowsMatching(table: String, col: String): List<Pair<Long, String>> {
            val out = ArrayList<Pair<Long, String>>()
            db.rawQuery("SELECT id, $col FROM $table WHERE vendor_id=?", arrayOf(a)).use { c ->
                while (c.moveToNext()) out.add(c.getLong(0) to c.getString(1).orEmpty())
            }
            return out
        }
        when {
            w.startsWith("آدرس") || w.startsWith("نشانی") || w == "لوکیشن" || w == "موقعیت" -> {
                val kind = (d.ifEmpty { w.removePrefix("آدرس").removePrefix("نشانی").trim() })
                val rows = rowsMatching("address", "kind")
                val hit = if (kind.isEmpty()) rows else rows.filter { Fa.normalize(it.second).contains(kind) || kind.contains(Fa.normalize(it.second)) }
                for ((rid, _) in hit) db.delete("address", "id=?", arrayOf(rid.toString()))
            }
            w in setOf("پرسنل", "شخص", "مسئول", "نفر", "پرسنل ها") || w.startsWith("پرسنل") || w.startsWith("مسئول") -> {
                val rows = rowsMatching("person", "name")
                val hit = if (d.isEmpty()) rows else rows.filter { Fa.normalize(it.second).contains(d) }
                for ((rid, _) in hit) db.delete("person", "id=?", arrayOf(rid.toString()))
            }
            w.contains("بانک") || w.contains("حساب") || w.contains("شبا") -> {
                val rows = rowsMatching("bank", "COALESCE(bank,'')")
                val hit = if (d.isEmpty()) rows else rows.filter { Fa.normalize(it.second).contains(d) }
                for ((rid, _) in hit) db.delete("bank", "id=?", arrayOf(rid.toString()))
            }
            else -> {
                val key = Keys.canon(what)
                val rows = ArrayList<Triple<Long, String, String>>()
                db.rawQuery("SELECT id, v, COALESCE(label,'') FROM attr WHERE vendor_id=? AND k=?", arrayOf(a, key)).use { c ->
                    while (c.moveToNext()) rows.add(Triple(c.getLong(0), c.getString(1), c.getString(2)))
                }
                val hit = if (d.isEmpty()) rows else rows.filter {
                    Fa.normalize(it.second).contains(d) || Fa.normalize(it.third).contains(d)
                }
                for ((rid, _, _) in hit) db.delete("attr", "id=?", arrayOf(rid.toString()))
            }
        }
    }

    /** برای «برگردان»: محتوای وندور را با یک نسخهٔ ذخیره‌شده جایگزین می‌کند (یا اگر حذف شده بود دوباره می‌سازد). */
    fun restoreSnapshot(snap: VendorData, existingId: Long?): Long = tx { db ->
        val id = existingId ?: db.insert("vendor", null, ContentValues().apply {
            put("name", snap.name); put("updated", System.currentTimeMillis())
        })
        if (existingId != null) {
            db.execSQL("UPDATE vendor SET name=?, updated=? WHERE id=?", arrayOf<Any>(snap.name, System.currentTimeMillis(), existingId))
            val a = arrayOf(id.toString())
            for (t in listOf("attr", "address", "person", "bank")) db.delete(t, "vendor_id=?", a)
        }
        for (a in snap.attrs) {
            db.insert("attr", null, ContentValues().apply {
                put("vendor_id", id); put("k", a.key)
                put("v", if (a.secret) Crypto.encrypt(a.value) else a.value)
                put("secret", if (a.secret) 1 else 0); put("label", a.label)
            })
        }
        for (x in snap.addresses) {
            db.insert("address", null, ContentValues().apply {
                put("vendor_id", id); put("kind", x.kind); put("province", x.province); put("city", x.city)
                put("street", x.street); put("postal", x.postal); put("lat", x.lat); put("lng", x.lng)
            })
        }
        for (p in snap.people) {
            db.insert("person", null, ContentValues().apply {
                put("vendor_id", id); put("name", p.name); put("role", p.role); put("mobile", p.mobile)
                put("phone", p.phone); put("email", p.email); put("note", p.note)
            })
        }
        for (b in snap.banks) {
            db.insert("bank", null, ContentValues().apply {
                put("vendor_id", id); put("bank", b.bank); put("branch", b.branch)
                put("account", enc(b.account)); put("sheba", enc(b.sheba)); put("holder", enc(b.holder))
            })
        }
        reindex(db, id)
        id
    }

    // ---------------- کمکی ----------------
    private fun touch(db: SQLiteDatabase, id: Long) {
        db.execSQL("UPDATE vendor SET updated=? WHERE id=?", arrayOf<Any>(System.currentTimeMillis(), id))
    }

    /** متن جستجو = نام + مشخصه‌های غیرمحرمانه + آدرس‌ها + پرسنل + نام بانک (نرمال‌شده). */
    private fun reindex(db: SQLiteDatabase, id: Long) {
        val a = id.toString()
        val name = db.rawQuery("SELECT name FROM vendor WHERE id=?", arrayOf(a)).use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: return
        val parts = ArrayList<String>()
        parts.add(name)
        db.rawQuery("SELECT k, v, label FROM attr WHERE vendor_id=? AND secret=0", arrayOf(a)).use { c ->
            while (c.moveToNext()) {
                val v = c.getString(1)
                parts.add(c.getString(0)); parts.add(v)
                c.str(2)?.let { parts.add(it) }
                val digits = Fa.latinDigits(v).filter { it.isDigit() }
                if (digits.length >= 5) parts.add(digits)
            }
        }
        db.rawQuery("SELECT kind, province, city, street, postal FROM address WHERE vendor_id=?", arrayOf(a)).use { c ->
            while (c.moveToNext()) for (i in 0..4) c.str(i)?.let { parts.add(it) }
        }
        db.rawQuery("SELECT name, role, mobile, phone, email FROM person WHERE vendor_id=?", arrayOf(a)).use { c ->
            while (c.moveToNext()) for (i in 0..4) c.str(i)?.let {
                parts.add(it)
                val digits = Fa.latinDigits(it).filter { ch -> ch.isDigit() }
                if (i in 2..3 && digits.length >= 5) parts.add(digits)
            }
        }
        db.rawQuery("SELECT bank, branch FROM bank WHERE vendor_id=?", arrayOf(a)).use { c ->
            while (c.moveToNext()) for (i in 0..1) c.str(i)?.let { parts.add(it) }
        }
        db.delete("fts", "rowid=?", arrayOf(a))
        db.insert("fts", null, ContentValues().apply {
            put("rowid", id)
            put("content", Fa.normalize(parts.joinToString(" ")))
        })
    }
}
