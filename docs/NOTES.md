# Notas de execução

## Estado (plano 6: motor FM compatível com o DX7, versão 1.1.0)
- O que existe: tudo do plano 5 mais um segundo tipo de voz, FM de 6 operadores (núcleo msfa do Synth_Dexed), com os 32 algoritmos, as abas ALGO · OPERADOR · PITCH/LFO, leitura e gravação de `.syx` (banco de 32 vozes e voz única), importar e exportar `.syx` no navegador, aftertouch e breath (CC 2) vindos do MIDI. Os 8 cartuchos de fábrica (256 vozes) funcionam como prateleiras "DX7 ROM1A" … "DX7 ROM4B" **quando você os coloca em `app/src/main/assets/dx7/`**: eles são dados da Yamaha e não estão no repositório público (ver `docs/DX7-CARTUCHOS.md`).
- O que NÃO existe: o APK de release **assinado** (falta a chave: `tools\make-keystore.ps1`, `keystore.properties`, `tools\set-release-secrets.ps1`, e depois a tag `v1.1.0`); o medidor de atividade por operador nas fichas OP1..OP6 (o motor não publica esse dado); medição de CPU do FM no celular; legato, unison e glide de verdade no FM.
- Verificado por: 132 testes do motor no PC, 185 testes Kotlin (9 pulados quando a pasta dos cartuchos está vazia), as 256 vozes de fábrica renderizando sem NaN e sem aperto do limitador, a saída do motor igual à do msfa chamado direto (diferença máxima 1e-6, 4 vozes de cada cartucho), teste de alocação do caminho FM, e uma passagem no emulador (navegador, troca de motor, as três abas, arrastar o envelope, importar e exportar `.syx` conferidos byte a byte, reinício com sessão restaurada). O build do GitHub Actions a partir de um clone novo, sem os cartuchos, passou em 4 min 31 s.
- Ouvido por você: parecer geral "Já está funcionando perfeitamente" (12/11/2026, no S25 Ultra com o CK61, depois de instalar o APK de debug). **Não houve avaliação voz por voz**, e nada foi anotado sobre fidelidade em relação a um DX7 de verdade, nível, velocity, roda ou sustain em particular.
- Não testado: trocar analógico ↔ FM com uma nota segurada (o `adb` não faz multitoque; o teste do motor cobre sem estalo), som no emulador (`-no-audio`), CPU do FM com 16 vozes no celular.

## Estado no fim do plano 5 (versão 1.0.0)
- O que existe: tudo do plano 4 mais o banco de fábrica de 64 presets (nivelado por medição), a polifonia que cai sozinha sob carga (`LoadGovernor`) com teto manual em ⋮, o ícone adaptativo, "Sobre o Volt", "Salvar log…" no menu, o diálogo de salvar usável com o teclado virtual e a versão de release 1.0.0 (versionCode 100). Debug agora é `com.criapix.volt.debug`, "Volt dev".
- O que NÃO existe: o APK de release **assinado**. Falta você rodar `tools\make-keystore.ps1` e criar o `keystore.properties` (as senhas são suas); sem isso `assembleRelease` gera só `app-release-unsigned.apk`.
- Verificado por: 94 testes do motor no PC (`.\tools\test-engine.ps1`), 111 testes Kotlin (`gradlew :app:testDebugUnitTest`), `volt_render` (64 presets: sem NaN, sem aperto do limitador, voz livre, nível dentro da janela), `:app:assembleDebug` e `:app:assembleRelease`, e capturas no emulador (ícone, "0/8 vozes" e "1/8 vozes" em vermelho com o teto em 8, "Sobre o Volt", "Salvar log…", diálogo de salvar com o teclado). O build de release (assinado com a chave de debug, só para o teste, e **descartado**) instala ao lado do debug e toca uma tecla sem crash.
- Ouvido por você: só os 10 primeiros presets (plano 2, "pareceu muito bom", parecer geral) e o que o plano 1 verificou do CK61. **Os outros 54 presets, os efeitos, o arpejador, o teclado na tela, o MIDI Learn e a polifonia sob carga nunca foram ouvidos nem tocados no celular.** A revisão de ouvido (`docs/PRESET-REVIEW.md`, Tarefa 9) está **pendente**. Nenhum preset foi "ajustado de ouvido".

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

### Balanceamento de nível (Tarefa 4)
- Alvo **T = −23 dB** (mediana −22,6 arredondada; dentro da faixa de sanidade −24…−10). Janela de [−26, −21] dB; FX só tem o limite superior.
- Fórmula do plano `novo = antigo · 10^((T − L)/40)`, uma passada resolveu 21 presets; `063-pizzicato` já estava em 1,0 e ficou 0,6 dB abaixo, então subi `osc2.level` de 0,4 para 0,6 (sem tocar em `mix.drive`).
- `master.volume` agora definido em: `010-baixo-gordo` 0.59, `021-lead-classico` 0.52, `022-lead-quadrado` 0.56, `024-lead-trance` 0.89, `031-pad-pwm` 0.54, `033-pad-vidro` 0.61, `034-pad-evolutivo` 0.62, `040-metais-poly` 0.55, `041-metais-salto` 0.38, `042-metais-lentos` 0.52, `043-stab-de-metais` 0.55, `052-violoncelo` 0.53, `061-marimba` 0.89, `063-pizzicato` 1.0, `072-clavinete` 0.9, `073-orgao` 0.45, `074-cravo` 0.95, `084-caixa` 1.0, `085-scifi-aleatorio` 0.61, `086-sirene` 0.56, `090-arpejo-classico` 0.98, `092-baixo-distorcido` 0.34
- Novo espalhamento (todos os 43): mín. -42.5 dB (`084-caixa`, FX), máx. -20.5 dB; sem FX e sem Init: -25.5 a -21.1 dB.
- **Desvio:** `001-init` fica de fora da janela de nível (teste continua exigindo que soe, seja finito e libere as vozes). Init é o estado padrão do motor (`params: {}`); mudar o volume dele o descaracterizaria. Mede −20,5 dB.
- `084-caixa` continua em −42,5 dB (FX, isenta do limite inferior): ~13 dB abaixo do Bumbo. Ver Tarefa 9.

### Banco completo, 64 presets (Tarefa 8)
- 21 presets novos (093 a 113) no fim da lista; os 43 antigos ficam nas posições 1–43 (`theFirst43KeepTheirPositionsForProgramChange` fixa a ordem).
- Espalhamento de nível (64 presets): mín. -42.5 dB, máx. -20.5 dB; sem FX e sem Init: -25.6 a -21.1 dB (janela [−26, −21]).
- `master.volume` nos novos: `096-lead-tres-osciladores` 0.59, `098-lead-unison` 0.85, `100-pad-com-chorus` 0.57, `101-pad-varredura` 0.93, `104-trompas` 0.54, `107-pluck-com-eco` 1.0, `108-kalimba` 0.88, `109-orgao-de-tubos` 0.55, `110-clavinete-com-phaser` 0.84, `111-ondas-do-mar` 1.0, `113-arpejo-com-eco` 1.0.
- Além do `master.volume`: `107-pluck-com-eco` `osc2.level` 0,5 → 0,9 (já estava em 1,0 e ficava 3 dB abaixo da janela).
- **`111-ondas-do-mar` mudou em relação ao plano**: com ataque de 1,5 s e filtro em 700 Hz ele media −53 dB na frase de 2 s (reprovava o piso de −45 dB). Ruído branco filtrado é ~14 dB mais fraco que uma senoide no mesmo nível, e um ataque lento mal sobe numa nota de 2 s. Ficou com `aenv.attack` 0,6, `filter.cutoff` 3000 e `master.volume` 1,0: mede −39,4 dB (FX só tem limite superior na janela). Soa mais aberto e com ataque mais rápido que o do plano: para você julgar.
- Os presets de ruído (`084-caixa`, `111-ondas-do-mar`, e também `082-laser` e o vento) são os mais baixos do banco por natureza do ruído filtrado.
- 5 mais pesados (CPU no PC, 16 notas segurando; duas rodadas, com o emulador aberto consumindo CPU, então os números oscilam): rodada 1: 18.3 % 104-trompas; 17.8 % 032-pad-escuro; 17.2 % 042-metais-lentos; rodada 2: 18.8 % 104-trompas; 16.6 % 041-metais-salto; 16.5 % 032-pad-escuro

### Polifonia sob carga (Tarefas 5 a 7)
- Motor: `setVoiceLimit(4..16)`; vozes acima do limite fazem fade de 15 ms (`quickRelease`), em vez do release do preset, que pode durar segundos. `allocPoly` e o unison respeitam o limite. Testes: `engine_voice_limit_caps_new_notes`, `engine_lowering_the_limit_fades_extra_voices_quickly_without_a_click`, `engine_raising_the_limit_again_allows_sixteen_voices`, `engine_unison_uses_no_more_voices_than_the_limit`; `alloc_` continua passando.
- `LoadGovernor` (`engine/src/governor.h`), constantes: cai de 4 em 4 até 8 quando a carga fica acima de 80 % por 250 ms, ou quando há um estouro com carga acima de 50 %; espera 2 s entre quedas; sobe de 4 em 4 depois de 10 s abaixo de 40 %. Um contador de estouros que recomeça (stream reaberto) não conta como estouro novo. Os 6 testes `governor_` passaram de primeira, sem ajuste de tempo.
- App: o governador roda no callback do Oboe com a carga suavizada; o limite efetivo é `min(governador, teto do usuário)`. ⋮ → "Polifonia máxima" 8 / 12 / 16 (salvo nas preferências) e "Polifonia: N" no diagnóstico. A barra mostra "n/limite vozes" em vermelho quando o limite é menor que 16.
- **Não medido no aparelho:** o governador nunca rodou no celular. No emulador só se viu o teto manual (8) e o texto na barra; a queda automática por carga é coberta apenas pelos testes de lógica.

### Mudanças do plano 5 e desvios (com o motivo)
1. **`001-init` fora da janela de nível**: ver "Balanceamento de nível".
2. **`063-pizzicato`, `107-pluck-com-eco`**: `osc2.level` subido porque `master.volume` já estava em 1,0 e o nível continuava abaixo da janela.
3. **`111-ondas-do-mar`** diferente do plano: ver "Banco completo". `084-caixa` com `master.volume` 1,0 e ainda ~13 dB abaixo do Bumbo.
4. **Tarefa 9 (ouvir): pendente.** Você pediu para eu seguir sem perguntar, então a folha `docs/PRESET-REVIEW.md` foi montada com os 64 presets e os vereditos ficaram em branco. Presets sem veredito saem como estão.
5. **Release não assinado**: a chave de assinatura é a identidade permanente do app e as senhas são suas, então não criei o keystore. `tools\make-keystore.ps1` e `keystore.properties` ficam por sua conta.
6. **Bug de script que deixei passar e corrigi**: o script de balanceamento zerou a sintaxe do `001-init.json` (params vazio); detectei pelo teste de renderização e restaurei o arquivo do git.
7. **Diálogo de salvar com o teclado**: com `adjustResize` e o ✓ ("Concluído") do teclado, o botão "Salvar como novo" fica alcançável no emulador (capturas `build/save_kb.png` e `save_after.png`). O diálogo de Configurações é rolável; no emulador paisagem os itens de baixo ("Polifonia máxima", "Salvar log…", "Sobre") ficam abaixo da dobra e é preciso rolar.
8. `tools\pull-log.ps1` usa `com.criapix.volt.debug` por padrão (`-Package` para outro id). Build de release não é debuggable: use ⋮ → "Salvar log…".
9. Limite de contagem do `-Stress`: com o emulador aberto na mesma máquina as medidas oscilam bastante (o mais pesado variou de 9 % a 18 % entre rodadas). Os números servem como ordem de grandeza, não como medida.


## Plano 6 (motor FM compatível com o DX7) — 2026-11-12/13
- **Núcleo FM.** Synth_Dexed (`codeberg.org/dcoredump/Synth_Dexed`, commit `e3f524c8bd92cdd37d0aea2728ea991254dda8ab`, 10/09/2025) em `third_party/Synth_Dexed`, no lugar do Dexed do design: o msfa do Dexed depende de JUCE e de MTS-ESP e não compila sozinho. Os arquivos msfa compilados estão em `engine/CMakeLists.txt` (biblioteca `msfa`); os headers e fontes carregam cabeçalho Apache 2.0, compatível com a GPL-3 do Volt. Nenhum shim foi necessário e nada dentro de `third_party/` foi editado.
- **Armadilhas do msfa** (todas já tratadas no código):
  - o `lfo.h` do motor (`volt::Lfo`) esconde o `lfo.h` do msfa no caminho de include: onde o msfa é usado, incluir por caminho;
  - o `::Lfo` do msfa não tem construtor: sem `::Lfo lfo{}` a fase inicial é lixo e a mesma voz soa diferente a cada execução (quando o LFO não tem key sync ou a onda é S&H);
  - `Freqlut` e `Porta` só inicializam uma vez (`initDone`): sem zerar essas flags antes do `init`, uma troca de taxa de amostragem não valia. Achado por um teste a 44,1 kHz, que dava 402,7 Hz para um A4 (440 × 44100/48000): num aparelho a 44,1 kHz todas as notas FM sairiam ~1,5 semitom baixas;
  - os `init` do msfa recebem a taxa como `float`.
- **Parâmetros.** `engine.type` (0 analógico, 1 FM) e 163 parâmetros `fm.*` entraram **no fim** de `params.def` (de 99 para 263 parâmetros; os ids antigos não mudaram). O plano dizia 67 por um erro de contagem. Ordem VCED: o bloco `fm.opN` guarda os 21 campos do operador N (OP1 = painel do DX7), mas no `.syx` o OP6 vem primeiro. `fm.algorithm` é 1..32 (byte − 1), `fm.transpose` −24..24 (byte − 24), `fm.opN.detune` −7..7 (byte − 7).
- **Modos de voz no FM.** Poly funciona como no analógico. Mono, legato e unison tocam uma voz só (índice 0) com retrigger; soltar a tecla de cima com outra ainda segurada retoca a anterior (o msfa não muda o pitch de uma nota em andamento). `voice.glide` e `voice.drift` são ignorados. As notas e os controles entram a cada 64 amostras (1,3 ms), como no Dexed.
- **Roda, aftertouch e breath** são configurações do teclado no DX7, não da voz; aqui ficam no preset (`fm.wheel.*`, `fm.at.*`, `fm.breath.*`). Padrão: roda com alcance 50 e destino afinação; aftertouch e breath em 0. Um `.syx` não os carrega.
- **Cartuchos.** 3 dos 8 arquivos (`rom3a`, `rom3b`, `rom4a`) trazem bits que o DX7 ignora e alguns valores fora da faixa (127 ou 100 onde o máximo é 99): `rom3a` vozes 1, 3, 14, 19 e 21; `rom3b` vozes 1, 3 e 14; `rom4a` voz 7. Mascarar e limitar como o DX7 faz muda esses bytes, então a ida e volta idêntica byte a byte só vale para os outros cinco; nos três a regra é ler → gravar → ler dar as mesmas vozes. As checksums dos 8 arquivos são válidas.
- **Nível do FM.** Sem nivelamento por voz (fidelidade). Mediana das 256 vozes: −16,0 dB com `kFmGain` 1,0; com **`kFmGain` 0,45** a mediana é −22,8 dB (alvo −23, o mesmo do banco analógico), mínimo −61,4 e máximo −8,8 dB. O teste tem teto de −7 dB (medido + 1 dB, para pegar regressões) e piso de −67 dB (o piso do plano, −60, deslocado pela calibração: TAKE OFF tem ataque lento e numa frase curta só aparece baixinho). 14 vozes ficam abaixo de −35 dB (efeitos e ataques lentos). 31 vozes não soltam em 20 s depois do último note-off: é o original (L4 > 0 ou R4 muito lento) e elas morrem por roubo de voz.
- **CPU no PC** (`volt_bench`, 16 vozes, algoritmo 32, feedback 7, os seis operadores a 99): 65,2× o tempo real, **1,5 % de um núcleo**, o caso mais barato (o Ladder analógico com 16 vozes usa 7,6 %). No celular não foi medido.
- **Biblioteca.** Id de uma voz de cartucho: `factory:dx7-<arquivo>-NN` (NN 01..32). Ordem: presets JSON de fábrica (posições 1–64), cartuchos em ordem de nome (65–320), depois os do usuário (321 em diante). Os presets do usuário que antes ocupavam 65+ passaram a 321+, então o Program Change 65–128 agora alcança ROM1A … ROM2B e **deixou de alcançar presets do usuário**; sessão e favoritos guardam o id, não a posição, e não foram afetados. Um banco `.syx` importado vira presets do usuário na categoria "DX7 <nome do arquivo>" (sem o prefixo "DX7" repetido), uma voz solta vai para "DX7 Importados", e o `PresetFile` ganhou o campo `slot` (1..32) para o banco exportado manter a ordem do cartucho. Exportar: voz única (163 bytes) de qualquer preset FM (inclusive de fábrica) e banco de 32 vozes de uma prateleira só de presets de usuário FM (mais de 32 são cortados com aviso; menos de 32 são completados com INIT VOICE). O nome no `.syx` tem 10 caracteres ASCII.
- **Telas.** Os diagramas dos 32 algoritmos vêm da própria tabela do msfa (`FmCore::algorithms`, exportada por JNI com uma subclasse `AlgoPeek`) e decodificados em Kotlin; os algoritmos 4 e 6 têm o laço de feedback passando por dois operadores. A escala de teclado desenhada é só uma imagem (curvas lineares e exponenciais aproximadas), não a tabela real do msfa. Os pontos do envelope do DX7 arrastam taxa (horizontal) e nível (vertical).
- **Publicação.** O repositório público `criapix/volt-synth` não tem os `.syx`: o histórico foi reescrito (`git filter-branch`) para tirá-los de todos os commits. O histórico antigo continua no disco em `refs/original` e há um bundle de backup temporário: **não** use `git push --mirror` nem `--all` sem antes apagar `refs/original`. O workflow `.github/workflows/release.yml` compila, testa, assina e publica a release numa tag `v*`; precisa dos segredos `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` (`tools\set-release-secrets.ps1`) e que a tag seja igual ao `versionName`.
- **Armadilhas do ambiente** (aprendidas neste plano):
  - redirecionar (`>`) a saída do `adb exec-out screencap` no PowerShell 5.1 corrompe o PNG: usar o Git Bash;
  - no Git Bash, `adb push` para `/sdcard/...` precisa de `MSYS_NO_PATHCONV=1`;
  - o seletor de documentos do Android abre em retrato;
  - digitar na busca de presets no emulador dispara a sobreposição do tutorial da caneta e perde o texto;
  - a mensagem de importar/exportar precisa aparecer **dentro** do navegador, porque ele é uma `Dialog` (janela própria) que cobre a tela;
  - `tools\pull-log.ps1` usa `adb` sem `-s`.
