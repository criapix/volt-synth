# Cartuchos do DX7

## Por que não estão no repositório

Os oito cartuchos de fábrica do Yamaha DX7 (`rom1a.syx` a `rom4b.syx`, 4104 bytes cada, 32 vozes por arquivo) são dados da Yamaha. O Volt não os distribui: `app/src/main/assets/dx7/*.syx` está no `.gitignore`.

## Como usar os seus

1. Coloque os arquivos em `app/src/main/assets/dx7/`.
2. Os nomes precisam terminar em `.syx`. Qualquer banco de 32 vozes do DX7 serve, não só os de fábrica (`rom1a.syx` a `rom4b.syx`).
3. O app carrega todo `.syx` dessa pasta como uma prateleira chamada `DX7 <NOME>`, com o nome do arquivo em maiúsculas (`rom1a.syx` vira `DX7 ROM1A`). Os ids são `factory:dx7-<nome>-NN` (nome do arquivo em minúsculas, sem a extensão, e `NN` de 01 a 32).
4. Recompile (`gradlew :app:assembleDebug`): a pasta `assets` entra no APK. Tudo que estiver nela vai junto, então não deixe nada ali que você não queira distribuir.

## Sem os cartuchos

- O app continua com o motor FM completo; só não há prateleiras de fábrica do DX7.
- **Importar** funciona com qualquer `.syx` (banco de 32 vozes ou voz única).
- O build e os testes funcionam com a pasta vazia ou ausente. Os testes que precisam dos arquivos são pulados com um aviso:
  - motor (`tools\test-engine.ps1`): linhas `    SKIP <motivo>` (o teste aparece como `ok`);
  - Kotlin (`gradlew :app:testDebugUnitTest`): testes reportados como *skipped* (`Assume`).
- Os testes do codec, da biblioteca e da importação/exportação também rodam com bancos montados à mão, sem dados da Yamaha.
- Com 1 a 7 arquivos (conjunto próprio), as contagens são conferidas contra o que existe (n arquivos, 32·n vozes) em vez de 8 / 256. Os testes de valores conhecidos (BRASS 1, E.PIANO 1) exigem o `rom1a.syx` e são pulados sem ele.
