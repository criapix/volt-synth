# Volt

Volt é um sintetizador analógico virtual polifônico para Android, tocado por teclado MIDI USB com baixa latência.

## Estado

Em desenvolvimento. O plano 4 está implementado: motor C++ de 16 vozes (3 osciladores, filtro Ladder / Roland 12 dB / Roland 24 dB / Prophet, 2 envelopes, 2 LFOs, poly-mod, modos poly/mono/legato/unison), saída de áudio Oboe de baixa latência, entrada MIDI USB com mapa de controladores de fábrica, a tela de toque (abas OSC · FILTRO · ENV · MOD · ARP · FX, knobs, curva do filtro e envelopes ao vivo), presets (43 de fábrica, os seus, navegador, favoritos, importar/exportar, sessão restaurada) e MIDI Learn (associar controles do teclado a parâmetros e ações, captura suave, Program Change, canal MIDI). Também há seis efeitos (distorção, phaser, flanger, chorus, delay, reverb), o arpejador e um teclado deslizante na tela (botão "Teclas"). Falta só o banco de fábrica final e ajustado. A tela foi vista só no emulador; o som, o toque, os efeitos, o arpejador e o MIDI Learn no aparelho ainda não foram avaliados: veja `docs/DEVICE-TEST.md` e `docs/NOTES.md`.

## Compilar (Windows, PowerShell)

```powershell
git submodule update --init          # sst-filters, sst-basic-blocks, sst-effects, fmt, simde
. .\tools\env.ps1                    # MSVC do VS 2022 Build Tools, JAVA_HOME, ANDROID_HOME
.\tools\test-engine.ps1              # compila e roda os testes do motor no PC
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

O APK sai em `app\build\outputs\apk\debug\app-debug.apk`.

## Teclado MIDI

Conecte o teclado por cabo USB (celular como host) antes ou depois de abrir o app; a tela mostra "MIDI: N conectado(s)" e a última mensagem recebida. Mapa de fábrica: modulação (CC 1), volume (CC 7), sustain (CC 64), ressonância (CC 71), cutoff (CC 74), release (CC 72), ataque (CC 73) e decay (CC 75) do envelope de amplitude, reverb (CC 91), chorus (CC 93) e phaser (CC 95; mexer acima de zero liga o efeito), pitch bend, e CC 120/123 como pânico.

**MIDI Learn.** Toque no botão MIDI da barra superior: o app entra no modo aprender (botão laranja "APRENDER"). Toque num controle da tela (knob, seletor, botão, ◀ ▶ ou Pânico) e depois mexa num controle do teclado; a associação vale na hora e fica salva. Notas só podem ser associadas a ações (preset anterior, próximo, pânico). Uma associação aprendida vale mais que o mapa de fábrica. A **captura suave** evita saltos: depois de trocar de preset, o controle só assume o parâmetro quando passa pelo valor atual (a tela diz até onde levá-lo); dá para desligá-la. O **Program Change** carrega o preset na posição correspondente da biblioteca. O mapa (lista de associações, canal Omni ou 1–16, captura suave, última mensagem) fica em ⋮ → Controle MIDI, ou em "Mapa…" no modo aprender.

## Presets

Os presets do usuário ficam dentro do app (pasta privada); use **Exportar** no navegador de presets para fazer backup e **Importar** para trazê-los de volta. Um preset é um arquivo JSON com `name`, `category` e `params`, que mapeia o nome permanente de cada parâmetro ao seu valor real (Hz, segundos, semitons); o que faltar assume o padrão do motor. Os presets de fábrica são somente leitura: para alterar um, salve como novo.

## Licença

GPL-3.0-or-later (arquivo `LICENSE`). Bibliotecas de terceiros, em `third_party/`:
- sst-filters: GPL-3
- sst-basic-blocks: GPL-3
- sst-effects: GPL-3
- fmt: MIT
- simde: MIT

Design: [docs/plans/2026-11-11-volt-design.md](docs/plans/2026-11-11-volt-design.md)
