# Volt — design

Data: 2026-11-11. Estado: design validado com o usuário; ainda sem código.

Este documento é autossuficiente. Quem implementar não terá acesso à conversa que o gerou.

## 1. O que é

Volt é um aplicativo Android que é um sintetizador analógico virtual polifônico. O usuário toca por um teclado MIDI USB (alvo de teste: Yamaha CK61 na porta USB TO HOST) e ajusta o som pela tela do celular, em paisagem, com controles grandes para o dedo. Os controles físicos do teclado também movem os parâmetros (MIDI Learn). O app vem com um banco de presets de fábrica que recria os sons típicos de três clássicos: Minimoog, Jupiter-8 e Prophet-5.

Não é uma emulação de circuito de nenhum dos três. É um motor único cujo conjunto de recursos cobre os três.

Aparelho de referência: Samsung Galaxy S25 Ultra (SM-S938B, arm64-v8a, tela 19,5:9).

## 2. Decisões tomadas (não reabrir sem o usuário)

| Tema | Decisão |
|---|---|
| Motor | Um único motor subtrativo; os três caracteres vêm do filtro selecionável e dos presets |
| Origem do DSP | Arquitetura de voz própria, usando blocos prontos GPL-3: `surge-synthesizer/sst-filters` (filtros) e `surge-synthesizer/sst-effects` (efeitos) |
| Licença | GPL-3.0-or-later, por causa dos blocos acima |
| Linguagens | Motor em C++17 (NDK, CMake); app em Kotlin + Jetpack Compose |
| Áudio | Oboe (AAudio) em modo de baixa latência. Baixa latência é requisito |
| Tela | Paisagem, barra superior fixa + abas por seção (OSC, FILTRO, ENV, MOD, ARP, FX) |
| Controle MIDI | Padrões universais de fábrica + MIDI Learn em qualquer parâmetro |
| Extras da v1 | Reverb, delay, distorção, chorus, flanger, phaser, arpejador, teclado na tela, troca de preset pelo teclado |
| Nome | Volt. Pasta `Documentos\Github\volt-synth`. Pacote `com.criapix.volt` |
| Projeto de referência | FM1 Droid, em `C:\Users\criap\OneDrive\Documentos\Github\mvave fm1` (ver seção 9) |

## 3. Arquitetura

Duas camadas e uma fronteira estreita entre elas.

```
 Kotlin/Compose (thread de UI)          C++ (thread de áudio do Oboe)
 ┌──────────────────────────┐           ┌───────────────────────────────┐
 │ telas, presets, MIDI USB │  eventos  │ fila SPSC sem travas          │
 │ MidiRouter, MIDI Learn   │ ────────▶ │ → Engine::render(bloco)       │
 │                          │           │   arpejador → vozes → efeitos │
 │ medidores, valores       │ ◀──────── │ átomos: pico, estouros, vozes │
 └──────────────────────────┘  leitura  └───────────────────────────────┘
```

Regras da thread de áudio, sem exceção:
- Não aloca nem libera memória, não trava mutex, não faz E/S, não chama JNI.
- Recebe tudo por uma fila de eventos de tamanho fixo (um produtor, um consumidor). Eventos: `NoteOn`, `NoteOff`, `SetParam(id, valor)`, `PitchBend`, `LoadPatch` (vetor completo de parâmetros), `AllNotesOff`.
- Publica estado para a UI só por variáveis atômicas.

Parâmetros:
- Uma tabela única de parâmetros em C++ (`id`, nome estável, mínimo, máximo, padrão, curva, se é discreto). É a fonte da verdade. O Kotlin obtém a tabela por JNI na partida; não há lista duplicada à mão.
- Todo valor trafega normalizado em 0..1; o motor converte para a unidade real.
- Parâmetros contínuos são suavizados no motor (rampa de ~5 a 20 ms) para não haver "zíper".

O motor é uma biblioteca C++ sem dependência de Android. Ela compila e é testada no PC. Uma camada fina (`jni/`) liga a biblioteca ao Oboe e ao Kotlin.

## 4. Motor de som

Taxa de amostragem: a nativa do dispositivo (48 kHz no S25 Ultra). Processamento em `float`.

### 4.1 Voz

Polifonia máxima: 16 vozes. Roubo de voz: a mais antiga em release; se não houver, a mais antiga.

- **Osciladores (3)**: formas serra, quadrada com largura de pulso, triângulo, senoide. Por oscilador: forma, oitava (−2..+2), semitom, afinação fina, nível. Anti-aliasing por polyBLEP. Sync rígido do osc 1 pelo osc 2 (como no Prophet: o oscilador sincronizado é o mesmo que o poly-mod varre). O osc 3 pode ser desligado do teclado e usado como fonte de modulação (como no Minimoog).
- **Sub-oscilador** (quadrada, uma oitava abaixo do osc 1) e **ruído** (branco).
- **Mixer** com saturação suave ajustável (drive).
- **Passa-altas** simples de 1 polo antes do filtro principal (como no Jupiter).
- **Filtro principal**, com seletor de modelo:
  - `Ladder` — ladder de 24 dB/oitava (caráter Moog);
  - `Roland` — 12 ou 24 dB/oitava, limpo (caráter Jupiter);
  - `Prophet` — 24 dB/oitava com ressonância mais agressiva (caráter Prophet).
  Controles: cutoff, ressonância, quantidade de envelope (bipolar), key tracking, sensibilidade à velocity. Os três modelos vêm do `sst-filters`; a escolha exata do tipo/subtipo da biblioteca para cada modelo é decisão de implementação, registrada em `docs/NOTES.md`.
- **Envelopes (2)**: ADSR de filtro e ADSR de amplitude, com curvas exponenciais. Sensibilidade à velocity na amplitude.
- **LFOs (2)**: formas senoide, triângulo, serra, quadrada, sample & hold; frequência; atraso de entrada (fade-in); destinos com quantidade: afinação, largura de pulso, cutoff, amplitude. O LFO 1 é por voz ou global (chave); a roda de modulação escala a quantidade do LFO 1.
- **Poly-mod** (como no Prophet): fontes envelope de filtro e osc 2; destinos afinação do osc 1, largura de pulso do osc 1, cutoff.

### 4.2 Modos de voz

- `Poly`, `Mono` (retrigger), `Legato` (sem retrigger, prioridade à última nota), `Unison` (2 a 8 vozes empilhadas, com detune e abertura estéreo).
- Portamento (tempo; sempre ou só em legato).
- Analog drift: desvio lento e aleatório de afinação por voz, quantidade ajustável.
- Pitch bend com faixa ajustável (padrão ±2 semitons).

### 4.3 Efeitos

Em série, cada um com liga/desliga e 2 a 3 controles:

`distorção → phaser → flanger → chorus → delay → reverb`

Preferência: usar as implementações do `sst-effects`. Se um efeito da biblioteca for difícil de isolar, escrever uma versão própria simples é aceitável. O chorus deve ter um modo que lembre o chorus de conjunto dos Roland.

Limitador suave no fim da cadeia, para um preset nunca clipar a saída.

### 4.4 Arpejador

Fica antes das vozes. Modos: up, down, up-down, random, ordem tocada. Oitavas 1 a 4. Hold. Tempo em BPM (40 a 240) e divisão (1/4, 1/8, 1/16, tercinas). Gate ajustável. Relógio contado em amostras dentro do motor, para não depender da UI.

### 4.5 Orçamento de CPU

16 vozes com 3 osciladores, filtro ladder e todos os efeitos ligados devem usar menos de ~30 % de um núcleo no S25 Ultra. O motor mede o tempo de render por bloco e publica a carga.

## 5. Tela

Orientação fixa em paisagem, tela cheia (imersivo), tema escuro. A tela se adapta de 16:9 a 21:9.

### 5.1 Barra superior (fixa)

- Preset: número, nome, categoria; setas anterior/próximo. Tocar no nome abre o navegador.
- Botões: Salvar, MIDI (modo Learn), Teclado (gaveta), Pânico, menu ⋮ (Configurações).
- Indicador de atividade MIDI, medidor de nível de saída, contagem de vozes.

### 5.2 Abas

Uma linha de abas sob a barra: **OSC · FILTRO · ENV · MOD · ARP · FX**. Sem rolagem dentro de uma aba.

| Aba | Conteúdo |
|---|---|
| OSC | Três colunas (osc 1, 2, 3) + mixer (níveis, sub, ruído, drive) |
| FILTRO | Seletor de modelo, cutoff, ressonância, envelope, key tracking, velocity, passa-altas; curva do filtro desenhada ao vivo |
| ENV | Dois ADSR com o desenho do envelope; os pontos do desenho são arrastáveis |
| MOD | LFO 1 e 2, poly-mod, modo de voz, unison, portamento, drift, faixa do bend |
| ARP | Liga/desliga, modo, oitavas, hold, BPM, divisão, gate |
| FX | Seis blocos, cada um com liga/desliga e seus controles |

### 5.3 Controles

- **Knob**: diâmetro mínimo de 56 dp. Gira arrastando na vertical, a partir de qualquer ponto do knob. Enquanto o dedo está no controle, o valor aparece grande, com unidade real (Hz, ms, %, semitons).
- Ajuste fino: arrastar na horizontal depois de tocar (ou com um segundo dedo na tela).
- Dois toques: volta ao valor padrão do parâmetro.
- **Seletor** (forma de onda, modelo de filtro, modo de voz): botões segmentados, nunca um knob.
- Multitoque real: dois knobs podem ser movidos ao mesmo tempo.
- Vibração leve em seletores e chaves, com opção de desligar.

### 5.4 Teclado na tela

Gaveta que sobe da borda inferior: duas oitavas, botões de oitava ±, multitoque, velocity pela posição vertical do toque na tecla. Fechada, não ocupa espaço. Serve para testar sem o teclado físico.

### 5.5 Retorno visual do controle externo

Quando um CC move um parâmetro, o knob na tela acompanha. Se o parâmetro estiver em outra aba, a aba dele pisca na barra.

## 6. MIDI

### 6.1 Entrada

Qualquer dispositivo USB MIDI class-compliant, via `android.media.midi`. Conecta sozinho ao plugar; solta todas as notas ao desplugar. Canal: Omni (padrão) ou um canal fixo.

### 6.2 Mapeamento de fábrica (sem configurar nada)

| Mensagem | Efeito |
|---|---|
| Note On/Off | Notas, com velocity |
| Pitch Bend | Bend, na faixa configurada |
| CC 1 | Roda de modulação (quantidade do LFO 1) |
| CC 7 | Volume principal |
| CC 64 | Sustain |
| CC 71 | Ressonância |
| CC 74 | Cutoff |
| CC 120, 123 | Corta/solta todas as notas |
| Program Change | Carrega o preset daquele número |

### 6.3 MIDI Learn

- Botão MIDI na barra entra no modo Learn: os controles aprendíveis ficam destacados. O usuário toca em um e mexe num controle físico; a associação é criada e o controle passa a mostrar o número do CC.
- Fontes aceitas: CC (contínuo ou botão) e notas (para ações de botão).
- Alvos: qualquer parâmetro e as ações "preset anterior", "preset próximo", liga/desliga de cada efeito, hold do arpejador, pânico.
- Uma associação aprendida tem prioridade sobre o mapeamento de fábrica do mesmo CC.
- **Captura suave** (soft takeover): depois de uma troca de preset, um controle físico só assume o parâmetro quando o valor dele cruza o valor atual do parâmetro. Pode ser desligada em Configurações.
- O mapa é global, salvo no aparelho. Uma tela lista as associações, permite apagar uma ou todas e mostra a "última mensagem recebida".

Fora do escopo da v1: NRPN, controladores em modo relativo, aftertouch, MPE, saída MIDI, MIDI por Bluetooth, sincronismo por MIDI Clock.

Não se sabe quais controles do painel do CK61 transmitem MIDI nem com que números. O monitor de "última mensagem recebida" é o meio de descobrir; o resultado vai para `docs/NOTES.md`.

## 7. Presets

- Formato: JSON com `format` (versão), `name`, `category`, `params` (mapa de nome estável do parâmetro → valor real). Usar o nome, não o índice, permite acrescentar parâmetros sem quebrar presets antigos.
- Leitura tolerante: parâmetro ausente assume o padrão; valor fora da faixa é limitado; parâmetro desconhecido é ignorado. Arquivo ilegível é recusado com mensagem.
- **Fábrica**: cerca de 64 presets, somente leitura, em `assets/`. Categorias: Bass, Lead, Pad, Brass, Strings, Keys, Pluck, FX. Devem cobrir os sons característicos dos três clássicos (baixo e lead com ladder de 3 osciladores; brass, pads e strings com chorus; sync lead e timbres de poly-mod). Nomes descritivos, sem marcas registradas e sem nomes de músicas ou artistas.
- **Usuário**: salvar, salvar como, renomear, apagar, favoritar; exportar e importar um arquivo (seletor de documentos do Android).
- **Navegador**: categorias, favoritos, busca por nome; tocar carrega na hora. A troca faz um fade curto nas vozes que estão soando, sem estalo.
- Program Change e as ações anterior/próximo seguem a ordem do navegador (fábrica, depois usuário).
- O app reabre no último preset usado, com as edições não salvas preservadas.

## 8. Falhas e testes

### 8.1 Falhas

| Situação | Comportamento |
|---|---|
| Dispositivo de saída muda ou desconecta | O fluxo é reaberto sozinho; o preset e os parâmetros permanecem |
| Sem modo de baixa latência | Toca mesmo assim; Configurações mostra o aviso e os números |
| Estouros de áudio persistentes | Aumenta o buffer em um bloco por vez (até 4); se continuar, reduz a polifonia e avisa |
| Notas presas | Botão Pânico; CC 120/123; desconexão do teclado solta tudo |
| Preset inválido | Seção 7 |

Configurações mostra o diagnóstico de áudio: taxa, tamanho do bloco, buffer, modo de baixa latência, latência de saída estimada, estouros, carga do motor.

### 8.2 Testes

- **Motor (C++, no PC, por CTest)**: afinação de cada forma de onda; energia de aliasing abaixo de um limite; resposta de cada modelo de filtro (atenuação acima do cutoff, auto-oscilação com ressonância alta); tempos de ADSR; roubo de voz; mono/legato/unison; padrões do arpejador; suavização de parâmetros; ausência de alocação em `render` (contador em `operator new` no binário de teste); e, para cada preset de fábrica, uma renderização que verifica que soa, não ultrapassa 0 dBFS e não produz NaN/Inf.
- **Kotlin (JVM)**: parser e roteador MIDI, MIDI Learn, captura suave, leitura/gravação tolerante de presets, ordem do navegador.
- **Aparelho (manual, pelo usuário)**: `docs/DEVICE-TEST.md`, com latência usando o CK61, cada controle físico, troca de preset, carga com 16 vozes.

Regra de honestidade: latência e qualidade de som só são conhecidas pelo que o usuário mede e ouve no aparelho. Nenhuma tarefa deve afirmar um número de latência que não foi lido, nem que um preset "soa como" um clássico sem alguém ter ouvido.

## 9. O que reaproveitar do FM1 Droid

Raiz do projeto de referência: `C:\Users\criap\OneDrive\Documentos\Github\mvave fm1` (o caminho tem espaço; usar aspas).

| Arquivo de referência | Uso no Volt |
|---|---|
| `app/src/main/kotlin/com/criapix/fm1droid/midi/MidiInput.kt` | Abrir o dispositivo USB MIDI; copiar e adaptar |
| `.../midi/MidiParser.kt` (+ testes) | Separar o fluxo em mensagens; copiar quase sem mudança |
| `.../midi/MidiRouter.kt`, `MidiMap.kt` | Base do roteador e do mapa do Learn; os alvos mudam de botões/encoders para parâmetros |
| `.../ui/MidiMapSheet.kt`, `SettingsSheet.kt` | Modelo das telas de mapa MIDI e de diagnóstico |
| `.../panel/Haptics.kt` | Vibração |
| `.../data/Prefs.kt` | Preferências |
| `app/build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle.kts` | Versões: compileSdk 36, minSdk 26, JDK 17, NDK `30.0.16248370`, ABIs `arm64-v8a` e `x86_64` |
| `docs/plans/2026-11-11-audio-latency.md`, `docs/NOTES.md` | Política de buffer e de diagnóstico de latência já validada |
| `docs/DEVICE-TEST.md` | Modelo do roteiro de teste no aparelho |

Não reaproveitar: o emulador (`vendor/fm1-emu`), a camada Rust (`native/`), firmware, o painel desenhado em Rust. O Volt não usa Rust, portanto não precisa do `tools/vsenv.ps1`; o motor compila pelo CMake do NDK.

Leitura de latência do FM1 Droid no S25 Ultra pelo alto-falante: saída do Android ≈ 11,9 ms com bloco de 96 quadros a 48 kHz. É a base de comparação; o Volt não tem a parcela do emulador (10,7 ms).

## 10. Ordem de construção

Cada etapa termina com algo que toca e com testes passando.

1. Projeto Android + biblioteca C++ + Oboe: um oscilador soando com baixa latência, e o motor compilando e testando no PC.
2. Voz completa (osciladores, mixer, envelopes, LFOs, modos de voz) e polifonia, tocável pelo teclado MIDI.
3. Filtros: integração do `sst-filters` em arm64 e x86_64.
4. Tela: barra, abas e controles de toque, ligados à tabela de parâmetros.
5. Presets: formato, armazenamento, navegador.
6. MIDI Learn, captura suave e troca de preset pelo teclado.
7. Efeitos.
8. Arpejador e teclado na tela.
9. Banco de fábrica, diagnóstico em Configurações, README e roteiro de teste no aparelho.

## 11. Riscos

| Risco | Tratamento |
|---|---|
| `sst-filters` usa intrínsecos SSE; em arm64 depende do `simde` | Resolver na etapa 3, cedo. Plano B: portar para C++ escalar só os filtros necessários, mantendo a GPL-3 |
| `sst-effects` pode puxar dependências (`sst-basic-blocks` e outras) difíceis de isolar | Efeito a efeito; versão própria simples quando custar menos |
| Latência do caminho de áudio USB do Android com o CK61 como interface de som | Só o usuário mede. Se a saída ficar sem modo de baixa latência ou acima de ~30 ms só com o CK61, parar e levar os números ao planejamento; dirigir a interface USB direto do app é um trabalho separado |
| Fidelidade dos presets de fábrica | Testes garantem só que soam e não clipam; o ajuste fino depende da escuta do usuário |
| Pasta dentro do OneDrive | Manter `build/`, `.cxx/` e `.gradle/` no `.gitignore`; os artefatos são sincronizados pelo OneDrive e podem causar arquivos travados |
