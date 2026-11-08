package com.example.vendorchat.chat

/** پارسر ساده و مقاوم JSON برای خروجی مدل (Map، List، String؛ اعداد به صورت String). */
object MiniJson {
    fun parse(s: String): Any? = try {
        P(s).value()
    } catch (e: Exception) {
        null
    }

    private class P(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun expect(c: Char) {
            ws()
            if (i >= s.length || s[i] != c) throw IllegalStateException("expected $c at $i")
            i++
        }

        fun value(): Any? {
            ws()
            if (i >= s.length) throw IllegalStateException("eof")
            val c = s[i]
            return when (c) {
                '{' -> obj()
                '[' -> arr()
                '"', '\'' -> str(c)
                else -> lit()
            }
        }

        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (i < s.length && s[i] == '}') {
                i++
                return m
            }
            while (true) {
                ws()
                if (i >= s.length) throw IllegalStateException("eof")
                val k = if (s[i] == '"' || s[i] == '\'') str(s[i]) else bare()
                expect(':')
                m[k] = value()
                ws()
                if (i < s.length && s[i] == ',') {
                    i++
                    ws()
                    if (i < s.length && s[i] == '}') {
                        i++
                        return m
                    }
                    continue
                }
                expect('}')
                return m
            }
        }

        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            ws()
            if (i < s.length && s[i] == ']') {
                i++
                return l
            }
            while (true) {
                l.add(value())
                ws()
                if (i < s.length && s[i] == ',') {
                    i++
                    ws()
                    if (i < s.length && s[i] == ']') {
                        i++
                        return l
                    }
                    continue
                }
                expect(']')
                return l
            }
        }

        fun str(q: Char): String {
            i++
            val sb = StringBuilder()
            while (i < s.length && s[i] != q) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    i++
                    when (val e = s[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> {
                            sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(e)
                    }
                } else {
                    sb.append(c)
                }
                i++
            }
            if (i >= s.length) throw IllegalStateException("unterminated string")
            i++
            return sb.toString()
        }

        fun bare(): String {
            val st = i
            while (i < s.length && s[i] != ':' && !s[i].isWhitespace()) i++
            return s.substring(st, i)
        }

        fun lit(): Any? {
            val st = i
            while (i < s.length && s[i] != ',' && s[i] != '}' && s[i] != ']' && !s[i].isWhitespace()) i++
            val t = s.substring(st, i)
            if (t.isEmpty()) throw IllegalStateException("bad literal at $i")
            return when (t) {
                "null" -> null
                "true", "false" -> t
                else -> t
            }
        }
    }
}
