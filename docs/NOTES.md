# Notas de execução

## Estado (plano 1: motor e app tocável)
- O que existe: motor C++ (16 vozes, 3 osciladores, filtro Ladder/Roland 12/Roland 24/Prophet, 2 envelopes, 2 LFOs, poly-mod, poly/mono/legato/unison), saída Oboe de baixa latência, entrada MIDI USB com o mapa de fábrica, tela de depuração com um controle por parâmetro.
- O que não existe ainda: a tela de toque com abas, presets, MIDI Learn, Program Change, efeitos, arpejador, teclado na tela, banco de fábrica.
- Verificado por: 48 testes do motor no PC (`.\tools\test-engine.ps1`), 16 testes Kotlin (`gradlew :app:testDebugUnitTest`), e a tela no emulador x86_64 (stream aberto, `Vozes: 1` depois de "Tocar Lá", sem crash no logcat). **Nada foi ouvido nem medido no celular.** A "latência de saída" mostrada no emulador (~112 ms) é do emulador e não diz nada sobre o S25 Ultra.

## Diferenças em relação ao design
- Oscilador 3 como fonte de modulação: não feito (os dois LFOs e o poly-mod cobrem).
- Poly-mod do oscilador 2 para o cutoff: não feito (os coeficientes do filtro são atualizados a cada 16 amostras).
- Hard sync sem anti-aliasing no reinício.
- Ganho dos filtros: Ladder e Prophet saem ~6 dB mais baixos que os Roland (ver "Filtros"). Não compensado.
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
- Ganho na banda passante (medido a 440 Hz, ressonância 0.1, cutoff aberto): Ladder 0,51, Roland 12 dB 1,00, Roland 24 dB 0,99, Prophet 0,51. Ou seja, Ladder e Prophet soam ~6 dB mais baixos que os Roland no mesmo preset. Por isso o teste `engine_plays_a_note_at_concert_pitch` passou de RMS > 0,05 para > 0,02 (a afinação em 440 Hz continua checada). Possível melhoria futura: compensar o ganho por modelo.

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
