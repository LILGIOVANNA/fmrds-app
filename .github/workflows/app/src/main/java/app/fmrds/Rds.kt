package app.fmrds

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RtPlusTag(val type: Int, val label: String, val text: String)

data class RdsState(
    val pi: Int? = null,
    val ps: String = "",
    val pty: Int = 0,
    val tp: Boolean = false,
    val ta: Boolean = false,
    val radioText: String = "",
    val rtPlus: List<RtPlusTag> = emptyList(),
    val itemRunning: Boolean = false,
    val groups: Int = 0,
) {
    val piHex: String get() = pi?.let { "%04X".format(it) } ?: "----"
}

/**
 * Decodifica dei gruppi RDS già corretti dal chip/demodulatore (4 blocchi A,B,C,D da 16 bit).
 * Supporta: PI, PS (gruppo 0), PTY/TP/TA, RadioText (gruppo 2A/2B) e RadioText+ (ODA, AID 0x4BD7).
 */
class RdsDecoder {
    private val _state = MutableStateFlow(RdsState())
    val state: StateFlow<RdsState> = _state

    private val ps = CharArray(8) { ' ' }
    private val rt = CharArray(64) { ' ' }
    private var rtAb = -1
    private var rtPlusGroup = -1               // (tipo shl 1) or versione del gruppo RT+
    private var rtItems: List<Triple<Int, Int, Int>> = emptyList() // (tipo contenuto, inizio, lunghezza)
    private var running = false
    private var ta = false

    @Synchronized
    fun reset() {
        ps.fill(' '); rt.fill(' ')
        rtAb = -1; rtPlusGroup = -1; rtItems = emptyList(); running = false; ta = false
        _state.value = RdsState()
    }

    @Synchronized
    fun feedGroup(a: Int, b: Int, c: Int, d: Int) {
        val type = (b shr 12) and 0xF
        val ver = (b shr 11) and 1
        val low5 = b and 0x1F

        when (type) {
            0 -> {
                val addr = b and 3
                ps[addr * 2] = rdsChar(d shr 8)
                ps[addr * 2 + 1] = rdsChar(d and 0xFF)
                ta = ((b shr 4) and 1) == 1
            }
            2 -> {
                val ab = (b shr 4) and 1
                if (rtAb != -1 && ab != rtAb) { rt.fill(' '); rtItems = emptyList() }
                rtAb = ab
                val addr = b and 0xF
                if (ver == 0) {
                    putRt(addr * 4, c shr 8); putRt(addr * 4 + 1, c and 0xFF)
                    putRt(addr * 4 + 2, d shr 8); putRt(addr * 4 + 3, d and 0xFF)
                } else {
                    putRt(addr * 2, d shr 8); putRt(addr * 2 + 1, d and 0xFF)
                }
            }
            3 -> if (ver == 0 && d == 0x4BD7) rtPlusGroup = low5
        }

        if (type != 3 && rtPlusGroup == ((type shl 1) or ver)) parseRtPlus(low5, c, d)

        val raw = String(rt)
        val end = raw.indexOf('\r')
        val text = (if (end >= 0) raw.substring(0, end) else raw).trimEnd()
        val tags = rtItems.mapNotNull { (ct, start, len) ->
            if (ct == 0 || start >= raw.length) null
            else RtPlusTag(ct, tagLabel(ct), raw.substring(start, minOf(raw.length, start + len)).trim())
        }.filter { it.text.isNotEmpty() }

        val s = _state.value
        _state.value = s.copy(
            pi = a,
            ps = String(ps),
            pty = (b shr 5) and 0x1F,
            tp = ((b shr 10) and 1) == 1,
            ta = ta,
            radioText = text,
            rtPlus = tags,
            itemRunning = running,
            groups = s.groups + 1,
        )
    }

    private fun putRt(i: Int, code: Int) { if (i in rt.indices) rt[i] = rdsChar(code) }

    // 37 bit: toggle(1) running(1) ct1(6) start1(6) len1(6) ct2(6) start2(6) len2(5)
    private fun parseRtPlus(low5: Int, c: Int, d: Int) {
        val bits = (low5.toLong() shl 32) or ((c.toLong() and 0xFFFF) shl 16) or (d.toLong() and 0xFFFF)
        running = ((bits shr 35) and 1L) == 1L
        val ct1 = ((bits shr 29) and 0x3F).toInt()
        val s1 = ((bits shr 23) and 0x3F).toInt()
        val l1 = ((bits shr 17) and 0x3F).toInt() + 1
        val ct2 = ((bits shr 11) and 0x3F).toInt()
        val s2 = ((bits shr 5) and 0x3F).toInt()
        val l2 = (bits and 0x1F).toInt() + 1
        rtItems = listOf(Triple(ct1, s1, l1), Triple(ct2, s2, l2))
    }

    // Semplificazione: il vero set di caratteri RDS differisce da Latin-1 sopra 0x7F.
    private fun rdsChar(code: Int): Char = when {
        code == 0x0D -> '\r'
        code < 0x20 -> ' '
        else -> code.toChar()
    }

    private fun tagLabel(ct: Int) = when (ct) {
        1 -> "Titolo"; 2 -> "Album"; 3 -> "N. traccia"; 4 -> "Artista"
        5 -> "Composizione"; 8 -> "Compositore"; 9 -> "Gruppo"; 11 -> "Genere"
        12 -> "Notizie"; 15 -> "Sport"; 19 -> "Salute"; 25 -> "Meteo"; 26 -> "Traffico"
        28 -> "Pubblicità"; 29 -> "URL"; 33 -> "Programma ora"; 34 -> "Programma dopo"
        36 -> "Conduttore"; 39 -> "Sito web"; 43 -> "Telefono"; 46 -> "Email"
        else -> "Tipo $ct"
    }
}
