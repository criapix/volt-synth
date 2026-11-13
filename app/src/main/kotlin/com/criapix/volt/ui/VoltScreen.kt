package com.criapix.volt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
import com.criapix.volt.Native
import com.criapix.volt.midi.Target
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
import kotlin.math.roundToInt

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
  /** Saves a .syx: suggested file name (without extension), the bytes, and the message shown when it worked. */
  val onExportSyx: (String, ByteArray, String) -> kotlin.Unit = { _, _, _ -> },
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

  val engineType = state.params["engine.type"]!!
  val engineNorm by state.value(engineType.id).collectAsStateWithLifecycle()
  val fm = engineType.toReal(engineNorm) >= 0.5f

  // What the sound is doing now: notes in, envelopes, LFOs, echoes. One frame clock repaints the drawings that read it.
  val algorithms = remember { Algorithms.decode(Native.fmAlgorithms()) }
  val live = remember(state) { Live(state, peak = io.peak, algorithms = { algorithms }) }
  val frame = remember { mutableLongStateOf(0L) }
  val macros = remember(state, live) { Macros(state) { live.algo } }
  DisposableEffect(state, live) {
    state.observer = live
    onDispose { if (state.observer === live) state.observer = null }
  }
  LaunchedEffect(live) {
    while (true) { withFrameNanos { live.update(); frame.longValue++ } }
  }
  LaunchedEffect(current.id, fm) { macros.reset() }

  var view by rememberSaveable { mutableIntStateOf(0) }  // 0 PALCO, 1 PAINEL
  var family by rememberSaveable { mutableStateOf("fonte") }
  var operator by rememberSaveable { mutableIntStateOf(1) }
  var base by rememberSaveable { mutableIntStateOf(48) }
  var sustain by remember { mutableStateOf(false) }
  var flash by remember { mutableStateOf<String?>(null) }
  var midiLit by remember { mutableStateOf(false) }
  var voices by remember { mutableIntStateOf(0) }
  var voiceLimit by remember { mutableIntStateOf(16) }
  var browser by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var settings by remember { mutableStateOf(false) }
  var keyboard by rememberSaveable { mutableStateOf(false) }

  // A MIDI controller moved a parameter in a family that is not on screen: flash that family tile.
  LaunchedEffect(external.seq) {
    val id = state.params.all.getOrNull(external.id)?.let { Panel.familyOf(it.name)?.id } ?: return@LaunchedEffect
    if (view == 1 && id != family) { flash = id; delay(600); flash = null }
  }
  LaunchedEffect(activity) { if (activity > 0) { midiLit = true; delay(120); midiLit = false } }
  LaunchedEffect(Unit) {
    while (true) {
      val v = io.voices(); val l = io.voiceLimit()
      if (v != voices) voices = v
      if (l != voiceLimit) voiceLimit = l
      delay(250)
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
      CompositionLocalProvider(LocalLearn provides learnUi, LocalLive provides live, LocalFrame provides frame) {
      Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        TopBar(
          number = model.number(current.id), name = current.name, category = current.category, dirty = dirty,
          midiDevices = devices, midiLit = midiLit, voices = voices, voiceLimit = voiceLimit,
          onPrev = { model.step(-1) }, onNext = { model.step(1) }, onBrowse = { browser = true },
          onSave = { saving = true },
          onPanic = { sustain = false; state.sustain(false); state.allNotesOff() },
          onMenu = { settings = true },
          learnActive = learnActive, onMidi = { io.learn.setActive(!learnActive) },
          engineSwitch = {
            LearnTarget(Target.Param(engineType.name)) {
              SegmentSwitch(listOf("ANALÓGICO", "FM"), if (fm) 1 else 0, listOf(Hues.osc1, Hues.algorithm)) { i ->
                state.set(engineType.id, engineType.toNorm(i.toFloat())); io.haptics.tick()
              }
            }
          },
          viewSwitch = {
            SegmentSwitch(listOf("PALCO", "PAINEL"), view, listOf(VoltColors.text, VoltColors.text)) { view = it; io.haptics.tick() }
          },
          showKeys = view == 1, keyboardOpen = keyboard, onKeyboard = { keyboard = !keyboard },
        )
        if (learnActive) LearnBanner(
          armed = armed, armedHasBindings = armed != null && bindings.any { it.second.target == armed }, confirmation = confirmation,
          onForget = { armed?.let { io.learn.clear(it) } }, onMap = { midiMap = true }, onDone = { io.learn.setActive(false) },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
          if (view == 0) {
            PalcoScreen(
              state, macros, fm, base, { base = it }, sustain, { sustain = it; state.sustain(it) }, io.haptics::tick,
            )
          } else {
            PainelScreen(state, fm, family, { family = it }, operator, { operator = it }, flash, io.haptics::tick)
            KeyboardSlot(keyboard, state, base, { base = it }, onClose = { keyboard = false })
          }
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

/** Its own function so that only the Box scope is in sight (AnimatedVisibility is ambiguous inside a Column). */
@Composable
private fun BoxScope.KeyboardSlot(visible: Boolean, state: com.criapix.volt.state.SynthState, base: Int, onBase: (Int) -> kotlin.Unit, onClose: () -> kotlin.Unit) {
  AnimatedVisibility(
    visible = visible,
    enter = slideInVertically { it }, exit = slideOutVertically { it },
    modifier = Modifier.align(Alignment.BottomCenter),
  ) {
    KeyboardDrawer(state, base, onBase, onClose, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.62f))
  }
}
