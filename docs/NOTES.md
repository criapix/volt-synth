# Notas de execução

## Estado (plano 4: efeitos, arpejador, teclado)
- O que existe: tudo do plano 3 mais seis efeitos (distorção, phaser, flanger, chorus, delay, reverb), arpejador (abas ARP e FX) e teclado deslizante na tela (botão "Teclas"), 43 presets de fábrica.
- O que não existe ainda: o banco de fábrica final e ajustado (~64, plano 5).
- Verificado por: 81 testes do motor no PC (`.	ools	est-engine.ps1`), 108 testes Kotlin (`gradlew :app:testDebugUnitTest`), `:app:assembleDebug` e capturas no emulador. **Nada foi ouvido nem tocado no celular**: efeitos, arpejador e teclado são medidos por testes (tempo do eco, cauda do reverb, harmônicos, passos do arpejo); como soam é para você julgar (`docs/DEVICE-TEST.md`, plano 4).

## Diferenças em relação ao design
- Oscilador 3 como fonte de modulação: não feito (os dois LFOs e o poly-mod cobrem).
- Poly-mod do oscilador 2 para o cutoff: não feito (os coeficientes do filtro são atualizados a cada 16 amostras).
- Hard sync sem anti-aliasing no reinício.
- Ganho dos filtros: Ladder e Prophet saíam ~6 dB mais baixos que os Roland; compensado no plano 2 (ver "Plano 2").
- O reference (FM1 Droid) não tinha teste do parser MIDI; escrevi `MidiParserTest` do zero (5 casos). `MidiInput` ganhou o parâmetro `onDisconnect` (desplugar o teclado chama `Native.allNotesOff()`).

## Ambiente (Windows)
- Antes de compilar no PC: `. .\tools\env.ps1` (força o MSVC do VS 2022 Build Tools e define JAVA_HOME/ANDROID_HOME).
- CMake: 3.31.6 (vem com o VS 2022 Build Tools, aparece no PATH após `env.ps1`). Gerador: Ninja 1.12.1 (idem). Nada foi instalado no PC; o Gradle instalou o CMake 3.22.1 do SDK Android sozinho.
- Testes do motor: `.\tools\test-engine.ps1`.
- Ao editar arquivos de configuração (`libs.versions.toml`, `settings.gradle.kts`) com `Set-Content -Encoding utf8` no PowerShell 5.1, o arquivo ganha BOM e o Gradle recusa. Remover o BOM.
- Testes Kotlin que formatam números: usar `Locale.ROOT`; nesta máquina a locale é pt-BR (vírgula decimal).

## Filtros (sst-filters)
- Submódulos (commits): sst-filters `87a171b`, sst-basic-blocks `5fb6098`, simde `24fa542`.
- `updateState(state, lane)` copia `C`/`dC` do maker para a faixa `lane`; `updateCoefficients(state, lane)` copia a faixa de volta. Funciona como o plano supunha, sem adaptação.
- `sst/basic-blocks/simd/setup.h` seleciona simde sozinho em arm64; nenhuma macro extra foi necessária.
- Spike A (MSVC x64): os 6 testes `filter_` passaram de primeira. Spike B (NDK arm64-v8a e x86_64): `libvolt_engine.a` compila nos dois. Plano B (filtro escalar) NÃO foi necessário.
- Ganho na banda passante (medido a 440 Hz, ressonância 0.1, cutoff aberto): Ladder 0,51, Roland 12 dB 1,00, Roland 24 dB 0,99, Prophet 0,51. Ou seja, Ladder e Prophet soam ~6 dB mais baixos que os Roland no mesmo preset. Por isso o teste `engine_plays_a_note_at_concert_pitch` passou de RMS > 0,05 para > 0,02 (a afinação em 440 Hz continua checada). (Compensado no plano 2.)

## Desempenho
Números do **PC** (`volt_bench.exe`, 16 vozes, osc 2 e 3 ligados, cutoff 2 kHz, 10 s de áudio a 48 kHz). Não valem para o celular:
```
model 0: 16 voices, 13.7x real time, 7.3 % of one core
model 1: 16 voices, 33.7x real time, 3.0 % of one core
model 2: 16 voices, 34.2x real time, 2.9 % of one core
model 3: 16 voices, 28.5x real time, 3.5 % of one core
```
Plano 4, mesma máquina, com os seis efeitos ligados (mix 0,5):
```
model 0 + all effects: 16 voices, 10.8x real time, 9.2 % of one core
```
Os efeitos custam ~1,6 ponto percentual de um núcleo no PC (7,6 % → 9,2 %); no celular isso precisa ser medido (DEVICE-TEST, plano 4, passo 6).

Todos acima de 10× o tempo real, então o Ladder não foi trocado pelo `fut_lpmoog`. O Ladder é o mais pesado (cerca de 2,5× o custo dos Roland).

## Como ler o diagnóstico de áudio
- "Baixa latência: NÃO" ou "Latência de saída" acima de ~30 ms só com o CK61 como saída de som: o atraso está no caminho de áudio USB do Android; o app não consegue reduzi-lo pelo AAudio. Anotar os números e levar ao planejamento.
- "Estouros" subindo sem parar ou "Carga do motor" acima de 50 %: o motor não está acompanhando; anotar o modelo de filtro e o número de vozes.

## Resultados no aparelho (S25 Ultra + Yamaha CK61) — 2026-11-12
Fonte: log do app coletado por USB (`tools\pull-log.ps1`), sessão das 10:12 às 10:13, 89 amostras de áudio por segundo. O que o log não mostra (som, sensação de atraso) é o que você relatou: "funcionou perfeitamente".
- Aparelho: samsung SM-S938B, Android 17 (API 37). AAudio, 48000 Hz, **baixa latência: sim, exclusivo: sim**, bloco 96 quadros, buffer 192 (o mínimo, duas rajadas); o LatencyTuner não precisou aumentar.
- **Latência de saída estimada pelo Oboe: 7,1 a 10,4 ms (média 8,4 ms).** É a estimativa do caminho de saída do stream, não o atraso tecla → som medido de ponta a ponta.
- **Estouros: 0** na sessão toda.
- **Carga do motor:** média 6,9 %, **pico 27 %** com 8 vozes (10:12:35); máximo de 10 vozes simultâneas, a 16 %. Dentro da meta de 30 %, mas o teste não chegou a 16 vozes: o caso pior (16 vozes, osc 2 e 3 ligados, cada `filter.model`) continua sem medição.
- **Reinício do stream ao plugar o CK61:** `stream closed: ErrorDisconnected` às 10:12:09, logo depois de "MIDI conectado", e o app reabriu sozinho (`restarts=1`). Comportamento esperado quando a rota de áudio muda; nenhum som perdido foi relatado. Por isso o log não diz se a saída estava no alto-falante ou no CK61; anotar isso nos próximos testes.
- Desplugar o CK61 às 10:13:29 foi registrado ("MIDI desconectado") e chamou `allNotesOff`.
- Notas recebidas: 465 note-on e 466 note-off, notas 47 a 88, velocidade 15 a 104.
- **O que o CK61 envia** (todos no canal 1; contagem de mensagens, sem saber qual controle físico é qual): CC 74 (153), CC 72 (132), CC 73 (130), CC 71 (109), CC 91 (90), CC 14 (65), CC 13 (39), CC 93 (29), CC 15 (29), CC 92 (25), CC 9 (5) e CC 64 sustain (12). Só CC 74 e CC 71 têm função no mapa de fábrica (cutoff e ressonância); os demais (72, 73, 91, 92, 93, 13, 14, 15, 9) são candidatos para o MIDI Learn / mapa do plano 3. O pedal de sustain envia valores intermediários (12, 72, 127, 74): o limite em 64 do app funciona, mas ele não é meio-pedal.
- Sistema: o CK61 manda Active Sensing (0xFE) e uma mensagem SysEx (0xF0 … 0xF7); o app ignora, sem problema.
- **Não aparece no log:** nenhum pitch bend e nenhum CC 1 (roda de modulação). Ou não foram usados no teste, ou o CK61 não os enviou. Vale repetir só isso.
- **Decisão do usuário (2026-11-12):** os testes de pitch bend / roda de modulação e de carga com 16 vozes não serão feitos; foram dados como aprovados por decisão do usuário. **Não há medição por trás disso:** o log não tem nenhum evento de bend ou CC 1, e a carga máxima observada foi 27 % com 8 vozes (10 vozes no pico).

## Resultados no aparelho do plano 2 (S25 Ultra + CK61) — 2026-11-12
Fonte: log do app coletado por USB, sessão que começa às 11:10:54 (app 0.2.0), 171 amostras de áudio.
- **Parecer do usuário:** "pareceu muito bom"; pediu mais presets, de timbres clássicos (atendido com os 30 novos, ainda não ouvidos). É um parecer geral; não há anotação por passo do `docs/DEVICE-TEST.md`.
- Sem crash, **estouros: 0**, stream sem reinício (`restarts=0`), latência de saída estimada 6,4 a 9,8 ms. 908 notas ligadas e 908 desligadas.
- Os 10 presets de fábrica foram carregados. O log não tem "preset criado", "salvo", "apagado" nem "importado": salvar, renomear, apagar, exportar e importar não aparecem nesta sessão (pode não ter sido testado; exportar não é registrado).
- **Carga do motor: média 7,7 %, pico 36,3 % com 16 vozes (11:13:30)**, logo depois de carregar `Sino Metálico` (11:13:24) e `Init` (11:13:26). Passa da meta de 30 % do design, sem nenhum estouro. É a primeira medição com 16 vozes; antes o máximo era 27 % com 8 vozes (ver acima, onde esse teste foi dispensado por decisão do usuário). Em aberto: saber qual patch produziu o pico e se vale reduzir a polifonia sob carga (plano 5).
- O CK61 mandou CC 71, 72, 73, 74, 91, 92, 93, 13, 14 e 15; apareceram 18 linhas de pitch bend e 24 de CC 1 (roda de modulação), que faltavam no teste do plano 1. Nenhum CC 64 (sem pedal de sustain nesta sessão).
- O log não registra arrasto de knob, vibração nem o que se ouviu.

## Plano 2 (tela de toque e presets) — 2026-11-12
- Mudanças no motor: compensação de ganho do Ladder e do Prophet (x1,96, +5,8 dB; os quatro modelos agora têm ganho ~1 na banda passante, e o teste `engine_plays_a_note_at_concert_pitch` voltou a exigir RMS > 0,05); sync no estilo Prophet (o osc 1 segue o osc 2; parâmetro renomeado de `osc2.sync` para `osc1.sync`); troca de preset atômica (`postPatch`), com fade de ~5 ms quando há vozes soando.
- Presets: JSON com nome permanente do parâmetro → valor real; parâmetro ausente = padrão do motor. **Depois de publicar, não mudar um padrão em `params.def` sem revisar os presets de fábrica.**
- Banco de fábrica: 40 presets escritos por valores, sem audição por mim. Os 10 primeiros o usuário ouviu no aparelho ("pareceu muito bom"); os 30 acrescentados depois (012 a 086: baixos ácido, sub, funk e pulso; leads quadrado, assobio e trance; pads PWM, escuro, vidro e evolutivo; metais salto, lentos e stab; cordas ensemble e violoncelo; marimba, harpa e pizzicato; piano elétrico, clavinete, órgão, cravo e vibrafone; vento, laser, bumbo, caixa, sci-fi e sirene) ainda não foram ouvidos. Os nomes de arquivo agrupam por categoria (01x Baixo, 02x Lead, 03x Pad, 04x Metais, 05x Cordas, 06x Pluck, 07x Teclas, 08x Efeitos) e `FactoryPresetsTest` confere unicidade, faixa, parâmetros de passo inteiros e fonte de som audível. O banco completo, ajustado com o seu retorno, é o plano 5.
- A ordem da biblioteca é a ordem dos nomes de arquivo, então a posição mostrada no app mudou quando entraram os 30 novos (os IDs `factory:<arquivo>` não mudaram, nem a sessão restaurada). Ao fazer o Program Change (plano 3) usar essa ordem de propósito e não reordenar depois sem avisar.
- Sem efeitos no motor (chorus, reverb, delay), timbres clássicos que dependem deles saem aproximados com detune, drift e PWM (Pad PWM, Cordas Ensemble, Piano Elétrico, Órgão).
- Tamanhos de knob observados no emulador (capturas em `build/`, não versionadas):
  - **S25 Ultra (1440×3120, 560 dpi, ~891×411 dp):** abas OSC, FILTRO e ENV cabem sem rolagem. Na aba MOD o **LFO 1** (seletor + 7 knobs) cai no modo de rolagem vertical com knobs de 52 dp (o último, "Roda", fica abaixo da borda até rolar); LFO 2, POLY-MOD e VOZ cabem.
  - **16:9 (1080×1920, 420 dpi, ~731×411 dp):** rolam OSC 1, OSC 2 e OSC 3 (aba OSC) e LFO 1, LFO 2 e VOZ (aba MOD). MIXER e POLY-MOD cabem. Os rótulos dos seletores ficam apertados ("Legato Unison") mas legíveis.
- Navegação verificada no emulador: arrastar knob (Cutoff 20 kHz → 1,1 kHz, a barra mostra "• editado", a curva acompanha), `▶` (carrega `002 Baixo Gordo`, "editado" some, knobs vão aos valores do preset), navegador (10 cartões, favoritar, prateleira Favoritos), salvar como novo (`011 Teste`), reabrir o app depois de HOME + force-stop (volta `011 Teste` com "• editado" e os valores). O log do app tem as linhas `PRESET` e o logcat não tem crash.
- Defeito achado e corrigido nas capturas: o `Text` do Material mantém a altura de linha de 24 sp do estilo padrão quando só o `fontSize` muda, então rótulo e valor de um knob ocupavam ~48 dp e não os 30 dp de `KnobGrid.LABEL`; a última linha de knobs ficava cortada. Fixei `lineHeight` nos textos pequenos.
- Limitação vista: com o teclado virtual aberto em paisagem, o diálogo "Salvar preset" sobe e o botão "Salvar como novo" fica atrás do teclado até fechá-lo (botão voltar). Não corrigido neste plano.
- Não testado: arrastar com dois dedos, toque duplo para voltar ao padrão, ajuste fino horizontal, arrastar os pontos do desenho de envelope, importar/exportar (seletor de arquivos do Android), renomear/apagar, vibração, troca de preset com nota soando. `adb input` só simula um toque por vez e ninguém ouviu nada.
- Commit `wip(app): groups, top bar and main screen` não existe: o `git add` falhou por causa do `DebugScreen.kt` já removido e esses arquivos entraram no commit final `feat(app): touch interface with presets replaces the debug screen`.

## Plano 2: desvios do plano escrito (registrados durante a execução)
- **`enum class Unit` em `ui/ParamUi.kt`** escondia `kotlin.Unit` no pacote `ui` e quebrava todo `@Composable` (erro de tipo em `VoltTheme`). Renomeado para `ValueUnit`. Nenhum teste muda.
- **`EnvelopeGeometryTest.nearestHandleIsFound`**: o plano consultava a alça de ataque em nível 0 e a de release em nível 1, o contrário de onde o desenho põe as alças (ataque em nível 1, release em nível 0). Com os níveis do plano, o ponto (attackX, 0) fica mais perto da alça de release. Corrigi o teste (ataque nível 1, release nível 0); o código está certo.
- **`FilterCurveTest.pointsAreLogSpaced`**: o plano esperava 0 dB no primeiro ponto, mas ele cai em 20 Hz, exatamente o corte do passa-altas (20 Hz), onde um polo único dá −3 dB. Corrigi para −3 dB ±0,5 e acrescentei uma checagem do último ponto (< −30 dB); o código está certo.

## Plano 3 (MIDI Learn) — 2026-11-12
- Botão MIDI na barra → modo aprender: tocar num controle da tela e mexer num controle do teclado. Alvos: qualquer parâmetro, preset anterior/próximo, pânico. Notas só para ações. CC 0, 32 e 120–127 não podem ser aprendidos.
- Precedência de um CC: aprender → associação aprendida → mapa de fábrica. Mapa de fábrica ampliado com os controladores de som do General MIDI: CC 72 → release, 73 → ataque, 75 → decay (envelope de amplitude), além de 71 → ressonância e 74 → cutoff. CC 91/92/93 ficam livres até os efeitos (plano 4).
- Captura suave: o controle só move o parâmetro enquanto o parâmetro ainda tem o último valor que ele mandou; carregar preset, mexer na tela ou outro controle o desligam, e ele volta a valer quando chega a 1,5/127 do valor ou cruza o valor. Logo depois de aprender, o controle já assume. Desligável em ⋮ → Controle MIDI.
- Ações disparam na subida para ≥ 64 (ou nota ligada); "qualquer valor dispara" (com 150 ms de antirrepique) para botões que mandam 127 e 0 em toques alternados; o mesmo liga/desliga parâmetros liga/desliga.
- Program Change n → preset na posição n+1 da biblioteca. Bank Select ignorado: só os 128 primeiros presets.
- Mapa salvo nas preferências (`midiMap`), por canal. Canal (Omni/1–16) e captura suave também.
- Verificado por: 103 testes Kotlin (MidiMap, SoftTakeover, MidiLearn, roteamento com SynthState real, Program Change, rótulos) e capturas no emulador (`build/`, não versionadas): botão MIDI → "APRENDER" com banner e contorno azul em knobs, seletores, toggles e ◀ ▶ Pânico; Cutoff armado (contorno laranja grosso, banner "Cutoff · FILTRO: mexa num controle do teclado…") com o valor intacto em 20,0 kHz; ▶ armado sem trocar o preset; tela do mapa (Omni, captura suave ligada, mapa de fábrica, lista vazia); canal 2 continua escolhido ao reabrir; "Concluir" tira banner e contornos e o knob volta a girar (Cutoff 20 kHz → 3,7 kHz, "• editado"); logcat sem crash. **Associar de fato, a captura suave e o Program Change não foram exercitados num aparelho: o emulador não tem entrada MIDI.**
- Visto nas capturas, não corrigido: em Learn mode o contorno do seletor ("Onda", "Modelo") engloba o rótulo e encosta nele; a lista do mapa de fábrica na tela do mapa rola (a coluna esquerda é maior que a tela).
- Execução: um `adb` sem `-s` falha com dois dispositivos; durante a verificação o celular do usuário apareceu no `adb devices`. Nenhum comando foi enviado a ele; os do emulador usaram `-s emulator-5554`.

## Efeitos (sst-effects)
- Submódulos novos: sst-effects `987b39d`, **fmt `407c905` (12.1.0)**. O `ParamMetadata.h` do sst-basic-blocks inclui `<fmt/core.h>`, então o fmt é dependência de fato (o plano não previa). fmt entra só como cabeçalhos (`FMT_HEADER_ONLY` em `volt_engine`); no MSVC foi preciso `/utf-8`. O fmt 11.0.2 não compila com o clang do NDK 30 (`consteval`); 12.1.0 compila.
- Spike A (MSVC x64): `fx_dump` compilou e imprimiu as quatro tabelas. Spike B (NDK 30, arm64-v8a e x86_64): `libvolt_engine.a` compila com `fx_sst.cpp`. **Plano B (efeitos próprios) NÃO foi necessário.**
- Tempo e taxa são guardados em log2 (segundos ou Hz). O decay do Reverb2 é log2 com RT60 = 4·2^v s. O feedback do Delay passa por `amp_to_linear` (x³): o wrapper manda `cbrt(fb)` para o feedback do Volt ser um ganho linear.
- Mapa de parâmetros (índice: nome, faixa, padrão), do `build/fx_dump.txt`:
  - Reverb2: 0 Pre-Delay (-8..1, -4), 1 Room Size (-1..1, 0), 2 Decay Time (-4..6, 0,75), 3 Diffusion, 4 Buildup, 5 Modulation, 6 LF Damping, 7 HF Damping (0..1), 8 Width, 9 Mix.
  - Delay: 0 Left (-8..5), 1 Right (-8..5), 2 Feedback, 3 Crossfeed, 4 Low Cut, 5 High Cut, 6 Rate, 7 Depth, 8 Channel, 10 Mix, 11 Width.
  - Flanger: 0 Mode, 1 Waveform, 2 Rate (-7..9), 3 Depth, 4 Count (1..4), 5 Base Pitch (0..127), 6 Spacing, 7 Feedback, 8 HF Damping, 9 Width, 10 Mix (-1..1).
  - Phaser: 0 Center, 1 Feedback (-1..1), 2 Sharpness, 3 Rate (-7..9), 4 Depth, 5 Stereo, 6 Mix, 7 Width, 8 Count (1..16, 4), 9 Spread, 10 Waveform, 11 Tone.
- Ajustes fixos: Phaser Count = 4 (nunca reescrito: subir alocaria no áudio), centro 0; Flanger modo clássico, 1 voz, base 60, damping 0,1; Delay tempo inicial 0,375 s, cortes de graves/agudos nos extremos, sem modulação; Reverb pré-atraso ≈ 22 ms.
- Desvios do plano (motivos): (1) o mix do Flanger do sst não é seco em 0 (ganho e soft clip fixos); o Flanger roda com mix interno 1 e o `SstEffects::flanger` faz o dry/wet do Volt, assim mix 0 é exatamente seco. (2) O Delay faz crossfade quando o tempo muda (modo "Clean"); os testes de eco esperam 1 s de silêncio antes do impulso, e o tempo inicial do Delay é o padrão do Volt para um preset padrão não sofrer isso. (3) `fx_switching_an_effect_off…`: a distorção forte tem bordas íngremes próprias, então o teste compara o maior degrau depois do desligamento com o maior antes (< 1,5×), não com um limite fixo de 0,15.

## Plano 4 (efeitos, arpejador, teclado) — 2026-11-12
- Origem dos efeitos: phaser, flanger, delay e reverb vêm do sst-effects (Phaser, Flanger, Delay, Reverb2); **chorus (estilo Juno) e distorção foram escritos aqui**, porque o sst-effects não tem distorção e seu `Chorus.h` é um arquivo vazio. Detalhes de índices, unidades e ajustes fixos na seção "Efeitos (sst-effects)" acima. Plano B não foi necessário.
- Desvios do plano, com motivo: fmt entra como dependência (vendorizado, 12.1.0, `/utf-8` no MSVC); flanger com dry/wet próprio (mix 0 = seco exato); feedback do delay com `cbrt`; delay inicializado no tempo padrão; três testes ajustados (eco do delay com 1 s de pré-roll; clique da distorção comparado com o degrau anterior; `FactoryPresetsTest` não exige faixa de categoria para números ≥ 90, porque os três presets novos ficam no fim da lista para não mudar os Program Change dos 40 existentes).
- CPU no PC (`volt_bench`, 16 vozes, modelo 0): 7,6 % de um núcleo sem efeitos, **9,2 % com os seis efeitos ligados** (mix 0,5). Medida no celular fica para o passo 6 do DEVICE-TEST.
- Limites conhecidos: distorção sem oversampling (serrilhado com drive alto em notas agudas); um efeito religado pode repetir um resto da cauda antiga sob o fade-in de 21 ms; passos do arpejo com resolução de 16 amostras (0,33 ms); Bank Select continua ignorado; o phaser tem 4 estágios fixos.
- Controladores de fábrica: CC 91 → mix do reverb, CC 93 → mix do chorus, CC 95 → mix do phaser. Levar um deles acima de zero também liga o efeito (vale para mixes aprendidos). CC 92 fica livre.
- Presets: 090 Arpejo Clássico, 091 Cordas com Phaser, 092 Baixo Distorcido (posições 41–43 na biblioteca; preset de usuário desce 3 posições). Efeitos foram acrescentados a 030–034, 050, 051, 071, 073, 020 e 024. Nenhum foi ouvido.
- Capturas no emulador (medium_phone, 1440×3120 a 560 dpi, paisagem; `build/fx.png`, `arp.png`, `keys.png`, `hold.png`, não versionadas): as abas OSC FILTRO ENV MOD ARP FX aparecem; **na aba FX os seis grupos cabem sem rolagem**; o teclado sobe cobrindo a parte de baixo, mostra C3 – C5, Oitava −/+, e uma tecla tocada acende e a barra mostra "1 vozes"; logcat sem crash. Com o teclado aberto, a aba ARP (grupo único com seletor de modo e de divisão) fica parcialmente coberta e o grupo rola; feche o teclado para ver os knobs. Arpejo contínuo, Hold e carregar o preset 041 não foram exercitados no emulador (só os testes do motor cobrem).

## Plano 5 (banco final, polifonia sob carga, ícone, release 1.0) — 2026-11-12
Ferramentas no PC: `.	oolsender-presets.ps1` (WAV por preset + `buildender\index.html` + `loudness.csv`) e `-Stress` (CPU por preset). Linha de base dos testes do motor: 81; depois das Tarefas 1–2: 84.

### Nível dos presets de fábrica (antes do balanceamento)
Nível = a janela de 400 ms mais alta do trecho, RMS em dBFS. Mediana dos 43 presets: **−22,6 dB** (mín. −42,5, máx. −10,7).
```
  #  file                          loud dB  squash %  release s
  1  001-init                        -20.5      0.00       0.32
  2  010-baixo-gordo                 -19.9      0.00       0.16
  3  011-baixo-redondo               -24.0      0.00       0.13
  4  012-baixo-acido                 -22.0      0.00       0.10
  5  013-baixo-sub                   -25.5      0.00       0.24
  6  014-baixo-funk                  -22.6      0.00       0.11
  7  015-baixo-pulso                 -21.1      0.00       0.16
  8  020-lead-sync                   -24.5      0.00       0.33
  9  021-lead-classico               -17.8      0.00       0.24
 10  022-lead-quadrado               -19.3      0.00       0.24
 11  023-lead-assobio                -22.4      0.00       0.41
 12  024-lead-trance                 -27.2      0.00       0.41
 13  030-pad-analogico               -23.1      0.00       4.08
 14  031-pad-pwm                     -18.4      0.00       2.93
 15  032-pad-escuro                  -21.4      0.00       4.96
 16  033-pad-vidro                   -20.6      0.00       4.08
 17  034-pad-evolutivo               -20.9      0.00       5.72
 18  040-metais-poly                 -18.8      0.00       0.49
 19  041-metais-salto                -12.5      0.00       0.33
 20  042-metais-lentos               -17.9      0.00       0.82
 21  043-stab-de-metais              -18.8      0.00       0.18
 22  050-cordas-suaves               -24.0      0.00       1.98
 23  051-cordas-ensemble             -24.2      0.00       1.65
 24  052-violoncelo                  -18.3      0.00       0.57
 25  060-pluck-vidro                 -24.3      0.00       0.26
 26  061-marimba                     -27.1      0.00       0.10
 27  062-harpa                       -23.6      0.00       0.98
 28  063-pizzicato                   -32.6      0.00       0.00
 29  070-sino-metalico               -23.0      0.00       1.78
 30  071-piano-eletrico              -25.3      0.00       0.54
 31  072-clavinete                   -27.3      0.00       0.11
 32  073-orgao                       -15.3      0.00       0.08
 33  074-cravo                       -28.3      0.00       0.14
 34  075-vibrafone                   -21.3      0.00       1.86
 35  081-vento                       -30.5      0.00       4.13
 36  082-laser                       -37.6      0.00       0.00
 37  083-bumbo                       -29.4      0.00       0.00
 38  084-caixa                       -42.5      0.00       0.00
 39  085-scifi-aleatorio             -20.7      0.00       0.50
 40  086-sirene                      -19.0      0.00       0.50
 41  090-arpejo-classico             -28.9      0.00       0.23
 42  091-cordas-phaser               -25.1      0.00       1.49
 43  092-baixo-distorcido            -10.7      0.00       0.13
```
- `084-caixa` estava em −48,7 dB e reprovava o piso de −45 dB do teste de renderização (Bumbo −29,4, Laser −37,6). Só acrescentei `master.volume` 1,0 (+6,2 dB) e ele foi a −42,5 dB: passa o piso, mas **continua ~13 dB abaixo do Bumbo**. Fica para as Tarefas 4 e 9 (você ouvir e decidir).
- Nenhum preset aperta o limitador (> 1 % das amostras acima de 0,95) e todos liberam as vozes.

### CPU por preset (16 notas seguradas, % de um núcleo do PC; `-Stress`)
Cinco mais pesados: `081-vento` 17,1 %, `032-pad-escuro` 15,9 %, `001-init` 15,7 %, `041-metais-salto` 14,9 %, `060-pluck-vidro` 14,9 %.
Ressalva: presets mono (baixos, leads, percussão) tocam 1 voz só e custam ~1 %; o "16 notas" só vale para os polifônicos.
**A pergunta dos 36 %:** `070-sino-metalico` NÃO está entre os mais pesados (8,8 %, 19º de 43). O pico de 36 % no celular logo depois de carregá-lo provavelmente não veio do custo dele (talvez do `Init` carregado em seguida, o 3º mais pesado, 15,7 %). Inconclusivo: o PC não mede o celular.
