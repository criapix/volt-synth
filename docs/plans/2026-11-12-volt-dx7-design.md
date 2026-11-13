# Volt — motor FM compatível com o DX7 (design)

Data: 2026-11-12. Estado: design validado com o usuário; ainda sem código.

> **Atualização (publicação):** os cartuchos de fábrica da Yamaha não são distribuídos no repositório público. Ficam fora do git (`.gitignore`), cada pessoa coloca os seus em `app/src/main/assets/dx7/`, e os testes que dependem deles são pulados quando a pasta está vazia. Veja `docs/DX7-CARTUCHOS.md`. O resto deste documento descreve o design original, em que os cartuchos iam no APK.

Este documento é autossuficiente. Quem implementar não terá acesso à conversa que o gerou. Ele complementa o design original, [2026-11-11-volt-design.md](2026-11-11-volt-design.md), cujas regras (thread de áudio, tabela única de parâmetros, licença) continuam valendo.

## 1. O que é

O Volt ganha um segundo tipo de voz: FM de 6 operadores, fiel ao Yamaha DX7 (32 algoritmos, envelopes de 4 taxas/4 níveis, escala de teclado, LFO e envelope de pitch do DX7). Os 256 sons dos cartuchos de fábrica do DX7 (ROM1A–ROM4B) vêm dentro do app, e o usuário pode importar e exportar cartuchos `.syx`. Há um editor completo de patch FM na tela.

## 2. Decisões tomadas (não reabrir sem o usuário)

| Tema | Decisão |
|---|---|
| Forma | Segundo tipo de voz dentro do mesmo motor e do mesmo app; o preset escolhe Analógico ou FM |
| Origem do DSP FM | O núcleo `msfa` do Dexed (GPL-3), como submódulo em `third_party/dexed`; nós escrevemos só o adaptador |
| Presets | Os 8 cartuchos de fábrica (`rom1a.syx` … `rom4b.syx`, 256 vozes) vão no APK, sem conversão. Importar/exportar `.syx` no navegador |
| Edição | Editor completo de todos os parâmetros de uma voz DX7 |
| Nivelamento | Nenhum por preset; um ganho global do FM calibrado pela mediana |

**Risco registrado.** Os dados dos cartuchos de fábrica são da Yamaha. O usuário foi avisado de que distribuí-los num app GPL é um risco de direitos autorais e decidiu embuti-los. Os arquivos foram fornecidos pelo usuário (verificados: 4104 bytes cada, cabeçalho `F0 43 00 09 20 00`, checksum válido). Se for preciso retirá-los, basta apagar `app/src/main/assets/dx7/`: o motor, o editor e a importação continuam funcionando.

## 3. Arquitetura

```
 eventos → arpejador → alocador de vozes ─┬─ voz analógica (osc → filtro → VCA)
                                          └─ voz FM (msfa Dx7Note, 6 operadores)
                                                   │
                                     soma → cadeia de efeitos → limitador → saída
```

- Um tipo de voz por vez (sem camadas). Parâmetro `engine.type`: 0 Analógico, 1 FM. Trocar de tipo no meio de notas usa o fade rápido que o motor já usa ao trocar de preset.
- A voz FM não passa pelo filtro (o DX7 não tem). Vai direto para a cadeia de efeitos.
- Continuam valendo para o FM: arpejador, pitch bend, sustain, modos poly/mono/legato, regulador de polifonia, limite manual de vozes, pânico. Roda de modulação, aftertouch e breath (CC 2) entram nas entradas de modulação do DX7 (sensibilidades e destinos do patch).
- **msfa.** Submódulo `third_party/dexed`; compilar só os fontes do núcleo em `Source/msfa/` (`dx7note`, `env`, `fm_core`, `fm_op_kernel`, `lfo`, `pitchenv`, `freqlut`, `exp2`, `sin`, `porta` se necessário), com NEON no arm64. As tabelas (`Freqlut::init`, `Exp2::init`, `Tanh::init`, `Sin::init`, `Lfo::init`, `PitchEnv::init`, `Env::init_sr`) são inicializadas na partida, fora da thread de áudio. O msfa renderiza blocos de 64 amostras em ponto fixo (int32); o adaptador acumula/converte para o bloco `float` do Volt. Nenhuma alocação, trava ou E/S na thread de áudio.
- A escolha exata dos arquivos e dos ajustes do submódulo é decisão de implementação, registrada em `docs/NOTES.md`.

### 3.1 Parâmetros

Todos os parâmetros de uma voz DX7 entram em `engine/src/params.def` como parâmetros comuns, discretos (`Step`), na faixa original do DX7 (0–99 na maioria). Nomes estáveis, por exemplo:

- por operador (`fm.op1` … `fm.op6`): `r1`–`r4`, `l1`–`l4`, `kls.break`, `kls.ldepth`, `kls.rdepth`, `kls.lcurve`, `kls.rcurve`, `rate.scale`, `ams`, `vel`, `level`, `mode` (razão/fixo), `coarse`, `fine`, `detune`, `on`;
- globais: `fm.algorithm` (1–32), `fm.feedback` (0–7), `fm.oscsync`, `fm.transpose`, `fm.pitchenv.r1`–`r4`/`l1`–`l4`, `fm.lfo.speed`, `.delay`, `.pmd`, `.amd`, `.sync`, `.wave`, `.pms`;
- controladores (no DX7 são da função, não da voz; aqui ficam no preset): alcance e destinos (pitch, amp, EG bias) da roda, do aftertouch e do breath.

A tabela passa de 67 para cerca de 225 parâmetros. Tela, MIDI Learn, captura suave e JSON de preset passam a cobrir o FM sem código específico. A ordem dos blocos de operador é fixa (um *stride*, como `kOscStride`).

Atenção à numeração: no `.syx` os operadores vêm na ordem OP6…OP1. O codec converte; dentro do Volt, `fm.op1` é sempre o OP1 do painel do DX7.

### 3.2 Codec `.syx`

Implementado duas vezes, com o mesmo comportamento: C++ (`engine/src/dx7_codec`, usado nos testes do PC) e Kotlin (`preset/Dx7Codec.kt`, usado pelo app).

- Lê banco de 32 vozes (formato empacotado "VMEM", 4104 bytes) e voz única ("VCED", 163 bytes).
- Grava banco de 32 vozes e voz única. Ida e volta deve ser idêntica byte a byte para os 8 cartuchos de fábrica.
- Nome do patch: 10 caracteres ASCII. Ao exportar, nomes maiores são cortados e o app avisa.

## 4. Fluxo de dados

1. Kotlin: `.syx` ou JSON → `Dx7Codec` → mapa `fm.*` → valor → `LoadPatch` (vetor normalizado de sempre) pela fila.
2. Thread de áudio: com `engine.type` = FM, faz o fade das vozes antigas e monta o patch do msfa (156 bytes, formato VCED) a partir dos parâmetros, num buffer fixo.
3. `SetParam` de um parâmetro `fm.*` atualiza o byte e chama `Dx7Note::update()` nas vozes FM ativas (como o Dexed: o envelope continua de onde estava).

**Ganho.** Nenhum nivelamento por preset (fidelidade). Um ganho global de saída do FM, constante, calibrado para que a mediana de nível dos 256 presets de fábrica fique igual à mediana do banco analógico. O limitador do fim da cadeia segura picos.

## 5. Presets

- `app/src/main/assets/dx7/rom1a.syx` … `rom4b.syx`, lidos na partida. O navegador mostra 8 categorias "DX7 ROM1A" … "DX7 ROM4B", 32 vozes cada, com o nome original. Somente leitura, como os de fábrica.
- Os presets DX7 entram na biblioteca depois dos 64 de fábrica. As posições fixadas para Program Change não mudam.
- Busca por nome e favoritos funcionam nas 256 vozes.
- **Importar** aceita `.json` e `.syx`. Um banco `.syx` vira uma categoria de usuário com o nome do arquivo (32 presets de usuário).
- **Exportar** um preset FM: como `.json` (padrão) ou como `.syx` de voz única. Exportar uma categoria FM inteira como banco `.syx` de 32 vozes.
- Salvar um preset FM grava JSON como qualquer preset; os parâmetros `fm.*` ausentes assumem o padrão (o "INIT VOICE" do DX7).

## 6. Tela

Com um preset FM, as abas OSC · FILTRO · ENV · MOD dão lugar a **ALGO · OPERADOR · PITCH/LFO**; ARP e FX ficam. A barra superior não muda. Sem rolagem dentro de uma aba.

| Aba | Conteúdo |
|---|---|
| ALGO | Diagrama do algoritmo atual (portadoras embaixo, moduladores em cima, laço de feedback). ◀ ▶ e seletor 1–32 com miniaturas. Feedback (0–7), transposição, osc key sync, liga/desliga de cada operador |
| OPERADOR | Fichas OP1…OP6 no topo (portadora/moduladora, nível, mini-medidor de atividade); tocar seleciona. Duas subpáginas: **Envelope** (desenho 4 taxas/4 níveis com pontos arrastáveis, reaproveitando `EnvelopeGeometry`; nível de saída; sensibilidade à velocity; escala de taxa) e **Freq/Escala** (razão/fixo, coarse, fine, detune, frequência resultante escrita — "×1,41" ou "440 Hz"; escala de teclado com gráfico; AMS) |
| PITCH/LFO | Envelope de pitch desenhado; LFO (forma, velocidade, atraso, PMD, AMD, sync, sensibilidade de pitch); roteamento de roda, aftertouch e breath (alcance; destinos pitch, amp, EG bias) |

Knobs do FM mostram o inteiro do DX7 (0–99 etc.), não porcentagem.

Os desenhos dos 32 algoritmos vêm de uma tabela de dados (quem modula quem, quais são portadoras, onde está o feedback), derivada da tabela de algoritmos do próprio msfa, não de imagens.

## 7. Erros

- Checksum errado: importa assim mesmo, com aviso (muitos cartuchos da internet têm checksum errado).
- Tamanho ou cabeçalho que não é de DX7: recusa com mensagem clara.
- Bytes fora da faixa: mascarados e limitados como o DX7 faz.
- Cartucho embutido que falhe ao carregar: some do navegador e vai para o log; o app não cai.

## 8. Desempenho

16 vozes × 6 operadores a 48 kHz. O regulador de polifonia existente vale para o FM. O benchmark do PC ganha o pior caso FM (16 notas, algoritmo com feedback, todos os operadores ligados). Meta igual à do design original: menos de ~30 % de um núcleo no S25 Ultra, com todos os efeitos ligados.

## 9. Testes

No PC (`tools/test-engine.ps1`):
- Ida e volta `.syx` idêntica byte a byte para os 8 cartuchos.
- Codec C++ e Kotlin produzem os mesmos parâmetros para os 8 cartuchos (arquivo de referência gerado pelo C++, conferido pelo teste Kotlin).
- Fidelidade do adaptador: para cada uma das 256 vozes, a saída do Volt (sem efeitos, ganho 1) coincide com o msfa chamado diretamente, dentro do erro da conversão para `float`, para uma nota e velocity fixas.
- As 256 vozes soam, não geram NaN, liberam as vozes após o release e ficam abaixo do pico máximo.
- Troca Analógico ↔ FM no meio de notas sem estalo (pico do fade limitado).
- `tools/render-presets.ps1` inclui os 256; `-Stress` inclui o pior caso FM.

Kotlin (`:app:testDebugUnitTest`): `Dx7Codec` (leitura, gravação, voz única, checksum errado, cabeçalho inválido) e a classificação dos cartuchos no navegador.

No aparelho (acrescentar a `docs/DEVICE-TEST.md`): ouvir uma amostra dos cartuchos, editar um operador durante uma nota, importar/exportar `.syx`, CPU com 16 vozes FM.

## 10. Fora do escopo

Camadas/split analógico + FM; filtro nas vozes FM; macros de edição; outros formatos (TX81Z, DX7II/TX802 com parâmetros extras); recebimento de SysEx pelo cabo MIDI.
