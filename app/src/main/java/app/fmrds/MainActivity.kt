package app.fmrds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RadioViewModel : ViewModel() {
    private val decoder = RdsDecoder()
    private val tuner: FmTuner = SimulatedTuner()   // <- qui andrà il backend reale
    val rds = decoder.state
    val tunerLabel get() = tuner.label

    private val _freq = MutableStateFlow(98.5)
    val freq: StateFlow<Double> = _freq

    init {
        tuner.start { a, b, c, d -> decoder.feedGroup(a, b, c, d) }
        tuner.tune(_freq.value)
    }

    fun setFreq(f: Double) {
        val r = (Math.round(f * 10) / 10.0).coerceIn(87.5, 108.0)
        if (r != _freq.value) { _freq.value = r; decoder.reset(); tuner.tune(r) }
    }

    override fun onCleared() { tuner.stop() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { RadioScreen() } }
    }
}

private val Bg = Color(0xFF0E0E10)
private val Card = Color(0xFF1A1A1F)
private val Accent = Color(0xFF7CF5C8)
private val Dim = Color(0xFF8A8A93)

@Composable
fun RadioScreen(vm: RadioViewModel = viewModel()) {
    val f by vm.freq.collectAsState()
    val rds by vm.rds.collectAsState()
    val ctx = LocalContext.current
    val probe = remember { ChipProbe.report(ctx) }

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())
            .statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(vm.tunerLabel, color = Dim, fontSize = 12.sp)

        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.1f".format(f), color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(8.dp))
            Text("MHz", color = Dim, fontSize = 20.sp, modifier = Modifier.padding(bottom = 14.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(rds.ps.ifBlank { "········" }, color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Chip("PI ${rds.piHex}")
            if (rds.tp) Chip("TP")
            if (rds.ta) Chip("TA")
        }

        Panel("RadioText") {
            Text(rds.radioText.ifBlank { "—" }, color = Color.White, fontSize = 18.sp)
        }

        Panel("RadioText+" + if (rds.itemRunning) "  ▶" else "") {
            if (rds.rtPlus.isEmpty()) Text("—", color = Dim)
            rds.rtPlus.forEach {
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(it.label, color = Dim, modifier = Modifier.width(110.dp), fontSize = 14.sp)
                    Text(it.text, color = Color.White, fontSize = 16.sp)
                }
            }
        }

        Slider(
            value = f.toFloat(), onValueChange = { vm.setFreq(it.toDouble()) },
            valueRange = 87.5f..108f, steps = 204,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { vm.setFreq(f - 0.1) }) { Text("−0.1") }
            OutlinedButton(onClick = { vm.setFreq(f + 0.1) }) { Text("+0.1") }
        }

        Panel("Diagnostica chip FM") {
            probe.forEach { Text(it, color = Dim, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text, color = Color.White, fontSize = 12.sp,
        modifier = Modifier.background(Card, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title.uppercase(), color = Dim, fontSize = 11.sp, letterSpacing = 1.sp)
        content()
    }
}
