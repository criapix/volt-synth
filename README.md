# Volt

Volt é um sintetizador analógico virtual polifônico para Android, tocado por teclado MIDI USB com baixa latência.

## Estado

Em desenvolvimento. O plano 1 está implementado: motor C++ de 16 vozes (3 osciladores, filtro Ladder / Roland 12 dB / Roland 24 dB / Prophet, 2 envelopes, 2 LFOs, poly-mod, modos poly/mono/legato/unison), saída de áudio Oboe de baixa latência, entrada MIDI USB com mapa de controladores de fábrica e uma tela de depuração com um controle por parâmetro. Ainda não existem a tela de toque, presets, MIDI Learn, efeitos e arpejador. Nada foi medido no aparelho ainda: veja `docs/DEVICE-TEST.md` e `docs/NOTES.md`.

## Compilar (Windows, PowerShell)

```powershell
git submodule update --init          # sst-filters, sst-basic-blocks, simde
. .\tools\env.ps1                    # MSVC do VS 2022 Build Tools, JAVA_HOME, ANDROID_HOME
.\tools\test-engine.ps1              # compila e roda os testes do motor no PC
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

O APK sai em `app\build\outputs\apk\debug\app-debug.apk`.

## Teclado MIDI

Conecte o teclado por cabo USB (celular como host) antes ou depois de abrir o app; a tela mostra "MIDI: N conectado(s)" e a última mensagem recebida. Mapa de fábrica: modulação (CC 1), volume (CC 7), sustain (CC 64), ressonância (CC 71), cutoff (CC 74), pitch bend, e CC 120/123 como pânico.

## Licença

GPL-3.0-or-later (arquivo `LICENSE`). Bibliotecas de terceiros, em `third_party/`:
- sst-filters: GPL-3
- sst-basic-blocks: GPL-3
- simde: MIT

Design: [docs/plans/2026-11-11-volt-design.md](docs/plans/2026-11-11-volt-design.md)
