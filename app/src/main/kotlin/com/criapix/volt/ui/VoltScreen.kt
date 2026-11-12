package com.criapix.volt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.midi.MidiDispatch
import com.criapix.volt.midi.MidiLearn
import com.criapix.volt.midi.MidiSettings
import com.criapix.volt.midi.describe
import com.criapix.volt.preset.AppModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** What the screen reads from outside the model; lambdas keep Native out of the UI code. */
class ScreenIo(
  val midiDevices: StateFlow<Int>,
  val midiActivity: StateFlow<Long>,
  val lastMidi: StateFlow<String>,
  val peak: () -> Float,
  val voices: () -> Int,
  val audioInfo: () -> String,
  val haptics: Haptics,
  val learn: MidiLearn,
  val pickup: StateFlow<MidiDispatch.Pickup?>,
  val midiSettings: MidiSettings,
  val onExport: () -> kotlin.Unit,
  val onImport: () -> kotlin.Unit,
  val voiceLimit: () -> Int = { 16 },
  val maxVoices: MaxVoices = MaxVoices.Fixed,
  val onExportLog: () -> kotlin.Unit = {},
  val version: String = "",
)

/** The polyphony cap setting, behind an interface so the UI does not touch Prefs or Native. */
interface MaxVoices {
  var value: Int
  object Fixed : MaxVoices { override var value: Int = 16 }
}

@Composable
fun VoltScreen(model: AppModel, io: ScreenIo) {
  val state = model.state
  val current by model.current.collectAsStateWithLifecycle()
  val dirty by state.dirty.collectAsStateWithLifecycle()
  val external by state.external.collectAsStateWithLifecycle()
  val devices by io.midiDevices.collectAsStateWithLifecycle()
  val activity by io.midiActivity.collectAsStateWithLifecycle()
  val message by model.message.collectAsStateWithLifecycle()
  val learnActive by io.learn.active.collectAsStateWithLifecycle()
  val armed by io.learn.armed.collectAsStateWithLifecycle()
  val bindings by io.learn.bindings.collectAsStateWithLifecycle()
  val learned by io.learn.learned.collectAsStateWithLifecycle()
  val pickup by io.pickup.collectAsStateWithLifecycle()
  var confirmation by remember { mutableStateOf<String?>(null) }
  var pickupHint by remember { mutableStateOf<String?>(null) }
  var midiMap by remember { mutableStateOf(false) }

  var tab by rememberSaveable { mutableStateOf(Tab.OSC) }
  var flash by remember { mutableStateOf<Tab?>(null) }
  var midiLit by remember { mutableStateOf(false) }
  var level by remember { mutableFloatStateOf(0f) }
  var voices by remember { mutableIntStateOf(0) }
  var voiceLimit by remember { mutableIntStateOf(16) }
  var browser by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var settings by remember { mutableStateOf(false) }
  var keyboard by rememberSaveable { mutableStateOf(false) }

  // A MIDI controller moved a parameter on another tab: flash that tab.
  LaunchedEffect(external.seq) {
    val t = state.params.all.getOrNull(external.id)?.let { Panel.tabOf(it.name) } ?: return@LaunchedEffect
    if (t != tab) { flash = t; delay(600); flash = null }
  }
  LaunchedEffect(activity) { if (activity > 0) { midiLit = true; delay(120); midiLit = false } }
  LaunchedEffect(Unit) {
    var n = 0
    while (true) {
      level = maxOf(io.peak(), level * 0.85f)  // fast attack, slow fall
      if (n++ % 8 == 0) { voices = io.voices(); voiceLimit = io.voiceLimit() }
      delay(33)
    }
  }
  LaunchedEffect(learned?.seq) {
    val l = learned ?: return@LaunchedEffect
    confirmation = "${targetLabel(l.target)} ← ${describe(l.source)}"
    io.haptics.tick()
    delay(2000); confirmation = null
  }
  LaunchedEffect(pickup) {
    val pk = pickup ?: return@LaunchedEffect
    val prm = state.params.all[pk.param]
    pickupHint = "${ParamUi.of(prm.name).label}: leve o controle até ${ParamUi.format(prm, prm.toReal(pk.value))} para assumir"
    delay(2000); pickupHint = null
  }
  val learnUi = if (learnActive) {
    LearnUi(armed, bindings.groupBy({ it.second.target }, { it.first })) { t -> io.learn.arm(t); io.haptics.tick() }
  } else null

  LaunchedEffect(message) { if (message != null) { delay(3000); model.clearMessage() } }

  VoltTheme {
    Surface(Modifier.fillMaxSize(), color = VoltColors.background) {
      CompositionLocalProvider(LocalLearn provides learnUi) {
      Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        TopBar(
          number = model.number(current.id), name = current.name, category = current.category, dirty = dirty,
          midiDevices = devices, midiLit = midiLit, voices = voices, voiceLimit = voiceLimit, level = level,
          onPrev = { model.step(-1) }, onNext = { model.step(1) }, onBrowse = { browser = true },
          onSave = { saving = true }, onPanic = { state.allNotesOff() }, onMenu = { settings = true },
          learnActive = learnActive, onMidi = { io.learn.setActive(!learnActive) },
          keyboardOpen = keyboard, onKeyboard = { keyboard = !keyboard },
        )
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Tab.entries.forEach { t ->
            val bg by animateColorAsState(
              when { t == tab -> VoltColors.accent; t == flash -> VoltColors.accent.copy(alpha = 0.45f); else -> VoltColors.panel },
              label = "tab",
            )
            Box(
              Modifier.weight(1f).fillMaxHeight().background(bg, RoundedCornerShape(8.dp)).clickable { tab = t; io.haptics.tick() },
              contentAlignment = Alignment.Center,
            ) {
              Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (t == tab) Color.Black else VoltColors.text)
            }
          }
        }
        if (learnActive) LearnBanner(
          armed = armed, armedHasBindings = armed != null && bindings.any { it.second.target == armed }, confirmation = confirmation,
          onForget = { armed?.let { io.learn.clear(it) } }, onMap = { midiMap = true }, onDone = { io.learn.setActive(false) },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
          Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Panel.groups.getValue(tab).forEach { g ->
              GroupPanel(g, state, onStep = io.haptics::tick, modifier = Modifier.weight(g.weight).fillMaxHeight())
            }
          }
          KeyboardSlot(keyboard, onNoteOn = { n, v -> state.noteOn(n, v) }, onNoteOff = { n -> state.noteOff(n) }, onClose = { keyboard = false })
        }
      }
      }
      if (!learnActive) pickupHint?.let { h ->
        Box(Modifier.fillMaxSize().padding(bottom = 64.dp), contentAlignment = Alignment.BottomCenter) {
          Text(h, color = VoltColors.text, fontSize = 13.sp, modifier = Modifier.background(VoltColors.raised, RoundedCornerShape(8.dp)).padding(10.dp))
        }
      }
      message?.let { m ->
        Box(Modifier.fillMaxSize().padding(bottom = 16.dp), contentAlignment = Alignment.BottomCenter) {
          Text(m, color = VoltColors.text, modifier = Modifier.background(VoltColors.raised, RoundedCornerShape(8.dp)).padding(12.dp))
        }
      }
      if (browser) PresetBrowser(model, io, onClose = { browser = false })
      if (saving) SaveDialog(model, onClose = { saving = false })
      if (settings) SettingsDialog(state, io, onClose = { settings = false }, onMidiMap = { midiMap = true })
      if (midiMap) MidiMapDialog(state, io, onClose = { midiMap = false })
    }
  }
}

/** Its own function so that only the Box's scope is in sight (AnimatedVisibility is ambiguous inside a Column). */
@Composable
private fun BoxScope.KeyboardSlot(visible: Boolean, onNoteOn: (Int, Float) -> kotlin.Unit, onNoteOff: (Int) -> kotlin.Unit, onClose: () -> kotlin.Unit) {
  AnimatedVisibility(
    visible = visible,
    enter = slideInVertically { it }, exit = slideOutVertically { it },
    modifier = Modifier.align(Alignment.BottomCenter),
  ) {
    KeyboardDrawer(onNoteOn, onNoteOff, onClose, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.62f))
  }
}
