# Notas de execução

## Estado (plano 2: tela de toque e presets)
- O que existe: tudo do plano 1 mais a tela de toque (barra superior, abas OSC · FILTRO · ENV · MOD, knobs, seletores, curva do filtro e desenhos de envelope ao vivo), presets (formato JSON, 40 de fábrica, presets do usuário, navegador com busca, categorias e favoritos, salvar/renomear/apagar, importar/exportar, restauração da sessão) e troca de preset sem estalo no motor.
- O que não existe ainda: MIDI Learn, Program Change, efeitos, arpejador, teclado na tela, banco de fábrica completo (~64).
- Verificado por: 56 testes do motor no PC (`.\tools\test-engine.ps1`), 68 testes Kotlin (`gradlew :app:testDebugUnitTest`) e capturas de tela no emulador (S25 Ultra e 16:9). **Nada foi tocado nem ouvido no celular**; o som dos presets, o toque dos knobs e a ausência de estalo na troca de preset são para você julgar (`docs/DEVICE-TEST.md`, plano 2).

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
