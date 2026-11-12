package app.fmrds

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

data class OutDevice(val info: AudioDeviceInfo?, val label: String)

object AudioOut {
    fun list(ctx: Context): List<OutDevice> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map {
            OutDevice(it, "${typeName(it.type)} ${it.productName ?: ""}".trim())
        }
        return listOf(OutDevice(null, "Automatico (sistema)")) + devs
    }

    private fun typeName(t: Int) = when (t) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altoparlante"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Auricolare del telefono"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Cuffie con microfono"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Cuffie"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (chiamate)"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Linea analogica"
        else -> "Uscita ($t)"
    }

    /** Suona un tono di 1,5 s sul dispositivo scelto (null = scelta automatica del sistema). */
    fun playTestTone(device: AudioDeviceInfo?, ms: Int = 1500) {
        Thread {
            try {
                val rate = 44100
                val n = rate * ms / 1000
                val buf = ShortArray(n) { i ->
                    val env = minOf(1.0, minOf(i, n - i) / 2000.0)
                    (sin(2 * PI * 440 * i / rate) * 0.3 * env * Short.MAX_VALUE).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                if (device != null) track.setPreferredDevice(device)
                track.write(buf, 0, n)
                track.play()
                Thread.sleep(ms + 200L)
                track.release()
            } catch (e: Exception) { /* ignora */ }
        }.start()
    }
}
