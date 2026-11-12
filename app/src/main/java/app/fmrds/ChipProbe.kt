package app.fmrds

import android.content.Context
import android.os.Build
import java.io.File

/** Raccoglie indizi su come il telefono gestisce l'FM, per scrivere il collegamento giusto al chip. */
object ChipProbe {
    private val classCandidates = listOf(
        "com.samsung.media.fmradio.FMPlayer",
        "qcom.fmradio.FmReceiver",
        "com.broadcom.fm.fmreceiver.FmProxy",
        "com.mediatek.fmradio.FmNative",
    )

    fun report(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val out = mutableListOf<String>()

        out += "== Dispositivo =="
        out += "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
        out += "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        out += "Hardware: ${Build.HARDWARE} / ${Build.BOARD}"
        out += "Feature broadcastradio: " + if (pm.hasSystemFeature("android.hardware.broadcastradio")) "sì" else "no"

        out += "== Librerie di sistema (fm/radio) =="
        val libs: List<String> = try {
            pm.systemSharedLibraryNames?.filter { it.contains("fm", true) || it.contains("radio", true) }?.sorted() ?: emptyList()
        } catch (e: Exception) { emptyList() }
        out += libs.ifEmpty { listOf("(nessuna)") }

        out += "== App installate (fm/radio) =="
        val apps: List<String> = try {
            pm.getInstalledPackages(0).map { it.packageName }.filter { p ->
                val l = p.lowercase()
                l.contains("radio") || l.endsWith(".fm") || l.contains(".fm.")
            }.sorted()
        } catch (e: Exception) { emptyList() }
        out += apps.ifEmpty { listOf("(nessuna)") }

        out += "== File di sistema (fm/radio) =="
        val dirs = listOf(
            "/system/etc/permissions", "/vendor/etc/permissions", "/system/framework",
            "/system/app", "/system/priv-app", "/system/lib", "/system/lib64",
            "/vendor/lib", "/vendor/lib64", "/vendor/etc",
        )
        val permFiles = mutableListOf<File>()
        for (d in dirs) {
            val names: List<String> = try {
                File(d).list()?.filter { it.contains("fm", true) || it.contains("radio", true) }?.sorted() ?: emptyList()
            } catch (e: Exception) { emptyList() }
            if (names.isEmpty()) continue
            val more = if (names.size > 25) " …(+${names.size - 25})" else ""
            out += "$d: " + names.take(25).joinToString(", ") + more
            if (d.endsWith("etc/permissions")) names.forEach { permFiles += File(d, it) }
        }
        for (f in permFiles.take(6)) {
            val txt = try { f.readText().replace(Regex("\\s+"), " ").take(400) } catch (e: Exception) { "(illeggibile)" }
            out += "${f.name}: $txt"
        }

        out += "== Classi FM caricabili =="
        for (cn in classCandidates) {
            try {
                val c = Class.forName(cn)
                out += "$cn: TROVATA"
                c.declaredMethods
                    .map { m -> m.name + "(" + m.parameterTypes.joinToString(",") { it.simpleName } + ")" }
                    .sorted().take(70).forEach { out += "  $it" }
            } catch (t: Throwable) {
                out += "$cn: no"
            }
        }
        return out
    }
}
