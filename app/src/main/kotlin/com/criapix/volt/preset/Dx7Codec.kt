package com.criapix.volt.preset

/** DX7 sysex: 32-voice banks and single voices (plan 6, section 0). No Android types. */
object Dx7Codec {
  data class Read(val presets: List<PresetFile>, val checksumOk: Boolean)

  private val OP_FIELDS = listOf("r1", "r2", "r3", "r4", "l1", "l2", "l3", "l4", "kls.break", "kls.ldepth", "kls.rdepth",
    "kls.lcurve", "kls.rcurve", "rate.scale", "ams", "vel", "level", "mode", "coarse", "fine", "detune")
  private val OP_MAX = intArrayOf(99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 3, 3, 7, 3, 7, 99, 1, 31, 99, 14)
  private val GLOBAL_FIELDS = listOf("fm.pitchenv.r1", "fm.pitchenv.r2", "fm.pitchenv.r3", "fm.pitchenv.r4",
    "fm.pitchenv.l1", "fm.pitchenv.l2", "fm.pitchenv.l3", "fm.pitchenv.l4", "fm.algorithm", "fm.feedback", "fm.oscsync",
    "fm.lfo.speed", "fm.lfo.delay", "fm.lfo.pmd", "fm.lfo.amd", "fm.lfo.sync", "fm.lfo.wave", "fm.lfo.pms", "fm.transpose")
  private val GLOBAL_MAX = intArrayOf(99, 99, 99, 99, 99, 99, 99, 99, 31, 7, 1, 99, 99, 99, 99, 1, 5, 7, 48)
  private fun globalOffset(g: Int) = when (g) { 8 -> -1; 18 -> 24; else -> 0 }
  private fun opOffset(k: Int) = if (k == 20) 7 else 0

  /** INIT VOICE, in VCED bytes; also the source of defaults for missing parameters. */
  private val INIT: IntArray = IntArray(155).also { p ->
    for (op in 0 until 6) {
      val o = op * 21
      for (i in 0..6) p[o + i] = 99
      p[o + 8] = 39; p[o + 16] = if (op == 5) 99 else 0; p[o + 18] = 1; p[o + 20] = 7
    }
    for (i in 126..129) p[i] = 99
    for (i in 130..133) p[i] = 50
    p[136] = 1; p[137] = 35; p[141] = 1; p[143] = 3; p[144] = 24
    "INIT VOICE".forEachIndexed { i, c -> p[145 + i] = c.code }
  }

  /** A bank or a single voice. [label] names the category ("DX7 <label>"). Throws [PresetError]. */
  fun readAny(bytes: ByteArray, label: String): Read = when (bytes.size) {
    4104 -> readBank(bytes, label)
    163 -> readVoice(bytes, label)
    else -> throw PresetError("Não é um arquivo do DX7")
  }

  fun readBank(b: ByteArray, label: String): Read {
    val u = IntArray(b.size) { b[it].toInt() and 0xFF }
    if (u.size != 4104 || u[0] != 0xF0 || u[1] != 0x43 || (u[2] and 0xF0) != 0 || u[3] != 0x09 || u[4] != 0x20 ||
      u[5] != 0x00 || u[4103] != 0xF7) throw PresetError("Não é um arquivo do DX7")
    val presets = (0 until 32).map { i -> toPreset(unpackVmem(u, 6 + i * 128), label).copy(slot = i + 1) }
    return Read(presets, checksum(u, 6, 4096) == (u[4102] and 0x7F))
  }

  private fun readVoice(b: ByteArray, label: String): Read {
    val u = IntArray(b.size) { b[it].toInt() and 0xFF }
    if (u[0] != 0xF0 || u[1] != 0x43 || (u[2] and 0xF0) != 0 || u[3] != 0 || u[4] != 1 || u[5] != 0x1B || u[162] != 0xF7)
      throw PresetError("Não é um arquivo do DX7")
    val v = IntArray(155) { u[6 + it] }.also { clamp(it) }
    return Read(listOf(toPreset(v, label)), checksum(u, 6, 155) == (u[161] and 0x7F))
  }

  fun writeBank(presets: List<PresetFile>): ByteArray {
    val out = IntArray(4104)
    intArrayOf(0xF0, 0x43, 0x00, 0x09, 0x20, 0x00).copyInto(out)
    for (i in 0 until 32) packVmem(presets.getOrNull(i)?.let(::toVced) ?: INIT.copyOf(), out, 6 + i * 128)
    out[4102] = checksum(out, 6, 4096)
    out[4103] = 0xF7
    return ByteArray(out.size) { out[it].toByte() }
  }

  fun writeVoice(p: PresetFile): ByteArray {
    val v = toVced(p)
    val out = IntArray(163)
    intArrayOf(0xF0, 0x43, 0x00, 0x00, 0x01, 0x1B).copyInto(out)
    v.copyInto(out, 6)
    out[161] = checksum(out, 6, 155)
    out[162] = 0xF7
    return ByteArray(out.size) { out[it].toByte() }
  }

  /** VCED -> preset (fm.* parameters and engine.type only). */
  private fun toPreset(v: IntArray, label: String): PresetFile {
    val params = LinkedHashMap<String, Float>()
    params["engine.type"] = 1f
    for (n in 1..6) {
      val off = (6 - n) * 21
      OP_FIELDS.forEachIndexed { k, f -> params["fm.op$n.$f"] = (v[off + k] - opOffset(k)).toFloat() }
    }
    GLOBAL_FIELDS.forEachIndexed { g, f -> params[f] = (v[126 + g] - globalOffset(g)).toFloat() }
    val name = String(CharArray(10) { v[145 + it].toChar() }).trimEnd().ifBlank { "Sem nome" }
    return PresetFile(name = name, category = "DX7 $label", params = params)
  }

  /** Preset -> VCED. Missing fm.* parameters take the INIT VOICE value. */
  private fun toVced(p: PresetFile): IntArray {
    val v = INIT.copyOf()
    for (n in 1..6) {
      val off = (6 - n) * 21
      OP_FIELDS.forEachIndexed { k, f -> p.params["fm.op$n.$f"]?.let { v[off + k] = Math.round(it) + opOffset(k) } }
    }
    GLOBAL_FIELDS.forEachIndexed { g, f -> p.params[f]?.let { v[126 + g] = Math.round(it) + globalOffset(g) } }
    for (i in 0 until 10) {
      val c = p.name.getOrNull(i) ?: ' '
      v[145 + i] = if (c.code in 32..126) c.code else '?'.code
    }
    clamp(v)
    return v
  }

  private fun unpackVmem(m: IntArray, at: Int): IntArray {
    val v = IntArray(155)
    for (op in 0 until 6) {
      val s = at + op * 17
      val d = op * 21
      for (k in 0..10) v[d + k] = m[s + k] and 0x7F
      v[d + 11] = m[s + 11] and 3
      v[d + 12] = (m[s + 11] shr 2) and 3
      v[d + 13] = m[s + 12] and 7
      v[d + 20] = (m[s + 12] shr 3) and 0x0F
      v[d + 14] = m[s + 13] and 3
      v[d + 15] = (m[s + 13] shr 2) and 7
      v[d + 16] = m[s + 14] and 0x7F
      v[d + 17] = m[s + 15] and 1
      v[d + 18] = (m[s + 15] shr 1) and 0x1F
      v[d + 19] = m[s + 16] and 0x7F
    }
    for (i in 0 until 8) v[126 + i] = m[at + 102 + i] and 0x7F
    v[134] = m[at + 110] and 0x1F
    v[135] = m[at + 111] and 7
    v[136] = (m[at + 111] shr 3) and 1
    for (i in 0 until 4) v[137 + i] = m[at + 112 + i] and 0x7F
    v[141] = m[at + 116] and 1
    v[142] = (m[at + 116] shr 1) and 7
    v[143] = (m[at + 116] shr 4) and 7
    v[144] = m[at + 117] and 0x7F
    for (i in 0 until 10) v[145 + i] = m[at + 118 + i]
    clamp(v)
    return v
  }

  private fun packVmem(v: IntArray, out: IntArray, at: Int) {
    for (op in 0 until 6) {
      val s = op * 21
      val d = at + op * 17
      for (k in 0..10) out[d + k] = v[s + k]
      out[d + 11] = (v[s + 12] shl 2) or v[s + 11]
      out[d + 12] = (v[s + 20] shl 3) or v[s + 13]
      out[d + 13] = (v[s + 15] shl 2) or v[s + 14]
      out[d + 14] = v[s + 16]
      out[d + 15] = (v[s + 18] shl 1) or v[s + 17]
      out[d + 16] = v[s + 19]
    }
    for (i in 0 until 8) out[at + 102 + i] = v[126 + i]
    out[at + 110] = v[134]
    out[at + 111] = (v[136] shl 3) or v[135]
    for (i in 0 until 4) out[at + 112 + i] = v[137 + i]
    out[at + 116] = (v[143] shl 4) or (v[142] shl 1) or v[141]
    out[at + 117] = v[144]
    for (i in 0 until 10) out[at + 118 + i] = v[145 + i]
  }

  private fun clamp(v: IntArray) {
    for (op in 0 until 6) for (k in 0 until 21) v[op * 21 + k] = (v[op * 21 + k] and 0x7F).coerceIn(0, OP_MAX[k])
    for (g in 0 until 19) v[126 + g] = (v[126 + g] and 0x7F).coerceIn(0, GLOBAL_MAX[g])
    for (i in 145 until 155) v[i] = (v[i] and 0x7F).let { if (it < 32) ' '.code else it }
  }

  private fun checksum(u: IntArray, from: Int, n: Int): Int {
    var sum = 0
    for (i in from until from + n) sum += u[i]
    return (-sum) and 0x7F
  }
}
