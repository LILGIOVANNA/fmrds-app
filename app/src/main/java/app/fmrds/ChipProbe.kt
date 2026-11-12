package app.fmrds

import android.content.Context
import android.os.Build

/** Raccoglie indizi su come il telefono gestisce l'FM. Serve per scrivere il backend giusto. */
object ChipProbe {
    private val candidates = listOf(
        "com.sec.android.app.fm" to "Samsung FM (sec)",
        "com.samsung.android.app.fm" to "Samsung FM (android)",
        "com.caf.fmradio" to "Qualcomm FM",
        "com.android.fmradio" to "FM AOSP/MediaTek",
        "com.mediatek.fmradio" to "MediaTek FM",
        "com.miui.fm" to "Xiaomi FM",
    )

    fun report(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val lines = mutableListOf(
            "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}",
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Hardware: ${Build.HARDWARE} / ${Build.BOARD}",
            "Broadcast Radio HAL: " + yn(pm.hasSystemFeature("android.hardware.broadcastradio")),
        )
        for ((pkg, label) in candidates) {
            val installed = try { pm.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
            lines += "$label: ${yn(installed)}"
        }
        return lines
    }

    private fun yn(b: Boolean) = if (b) "sì" else "no"
}
