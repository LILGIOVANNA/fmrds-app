package app.fmrds

import kotlinx.coroutines.*

/**
 * Qualsiasi sorgente FM: chip interno (da implementare per modello), dongle RTL-SDR, modulo esterno...
 * Deve fornire gruppi RDS grezzi (A,B,C,D) già corretti.
 */
interface FmTuner {
    val label: String
    fun start(onGroup: (Int, Int, Int, Int) -> Unit)
    fun tune(mhz: Double)
    fun stop()
}

private data class SimStation(
    val mhz: Double, val pi: Int, val ps: String, val pty: Int,
    val artist: String, val title: String,
)

/** Sorgente finta per provare grafica e decodifica senza hardware. */
class SimulatedTuner : FmTuner {
    override val label = "Simulato (nessun hardware FM)"

    private val stations = listOf(
        SimStation(98.5, 0x5201, "RADIO AL", 10, "Artista Uno", "Canzone di prova"),
        SimStation(101.3, 0x5202, "RADIO BE", 10, "Band Due", "Secondo brano"),
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var current = 0.0
    private var job: Job? = null

    override fun start(onGroup: (Int, Int, Int, Int) -> Unit) {
        job = scope.launch {
            var i = 0
            var lastSt: SimStation? = null
            var groups: List<IntArray> = emptyList()
            while (isActive) {
                val st = stations.firstOrNull { kotlin.math.abs(it.mhz - current) < 0.05 }
                if (st !== lastSt) { lastSt = st; groups = st?.let { groupsFor(it) } ?: emptyList(); i = 0 }
                if (groups.isNotEmpty()) {
                    val g = groups[i % groups.size]
                    onGroup(g[0], g[1], g[2], g[3]); i++
                }
                delay(90)
            }
        }
    }

    override fun tune(mhz: Double) { current = mhz }
    override fun stop() { job?.cancel(); scope.cancel() }

    private fun groupsFor(st: SimStation): List<IntArray> {
        val out = mutableListOf<IntArray>()
        fun b(type: Int, ver: Int, low5: Int) =
            (type shl 12) or (ver shl 11) or (1 shl 10) or (st.pty shl 5) or low5

        val ps = st.ps.padEnd(8).take(8)
        for (i in 0..3) out += intArrayOf(st.pi, b(0, 0, i), 0, (ps[i * 2].code shl 8) or ps[i * 2 + 1].code)

        val text = "${st.artist} - ${st.title}"
        val t = (text + "\r").padEnd(64).take(64)
        val segs = (text.length + 1 + 3) / 4
        for (i in 0 until segs) out += intArrayOf(
            st.pi, b(2, 0, i),
            (t[i * 4].code shl 8) or t[i * 4 + 1].code,
            (t[i * 4 + 2].code shl 8) or t[i * 4 + 3].code,
        )

        // 3A: annuncia RT+ (AID 0x4BD7) sul gruppo 11A (codice 11*2+0 = 22)
        out += intArrayOf(st.pi, b(3, 0, 22), 0, 0x4BD7)
        val l1 = st.artist.length - 1
        val s2 = st.artist.length + 3
        val l2 = st.title.length - 1
        val bits = (1L shl 36) or (1L shl 35) or (4L shl 29) or (l1.toLong() shl 17) or
            (1L shl 11) or (s2.toLong() shl 5) or l2.toLong()
        out += intArrayOf(
            st.pi, b(11, 0, ((bits shr 32) and 0x1F).toInt()),
            ((bits shr 16) and 0xFFFF).toInt(), (bits and 0xFFFF).toInt(),
        )
        return out
    }
}
