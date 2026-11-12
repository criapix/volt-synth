# Teste no aparelho (plano 2: tela e presets)

Instalar: `adb install -r app\build\outputs\apk\debug\app-debug.apk`. Depois do teste, rodar `tools\pull-log.ps1`.

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 1 | Abrir o app | Tela cheia em paisagem; barra superior com preset; abas OSC · FILTRO · ENV · MOD | |
| 2 | Girar knobs arrastando para cima/baixo | Responde ao dedo sem pular; o valor aparece grande acima do knob | |
| 3 | Arrastar para o lado depois de tocar | Ajuste fino (bem mais lento) | |
| 4 | Dois toques num knob | Volta ao valor padrão | |
| 5 | Dois knobs ao mesmo tempo com dois dedos | Os dois se movem | |
| 6 | Tamanho dos knobs e textos | Anotar: confortável / pequeno demais; alguma aba rola? (no emulador, só o LFO 1 rola na aba MOD) | |
| 7 | Aba FILTRO: mexer em cutoff e ressonância | A curva acompanha; o som acompanha | |
| 8 | Aba ENV: arrastar os pontos do desenho | Os knobs abaixo acompanham | |
| 9 | ◀ ▶ com uma nota soando (pedal) | Troca de som sem estalo; a nota continua | |
| 10 | Ouvir os presets de fábrica com o CK61 (os 10 primeiros já foram vistos; os 30 novos de 012 a 086 são escritos por valores, sem audição) | Anotar por preset: bom / ajustar (o quê) / ruim | |
| 11 | Lead Sync: tocar e segurar | O timbre "varre" no ataque sem mudar a nota (sync com poly-mod) | |
| 12 | Trocar o modelo do filtro com o mesmo preset | Volume parecido entre os quatro modelos | |
| 13 | CK61: mexer no knob que manda CC 74 (cutoff) estando na aba OSC | A aba FILTRO pisca; na aba FILTRO, o knob Cutoff gira junto | |
| 14 | Salvar como novo, renomear, favoritar, apagar | Tudo funciona; o preset apagado some da lista | |
| 15 | Exportar e importar (navegador de presets) | Arquivo criado; importar de volta cria cópias "(2)" | |
| 16 | Fechar o app (botão home) com edição não salva e abrir de novo | Volta no mesmo preset, com a edição e o "• editado" | |
| 17 | Vibração | Tique nos seletores, abas e knobs de passo; desligável em ⋮ | |
| 18 | Salvar preset com o teclado virtual aberto | Anotar se o botão "Salvar como novo" fica escondido atrás do teclado (visto no emulador) | |

---

# Teste no aparelho (plano 1)

Instalar: `adb install -r app\build\outputs\apk\debug\app-debug.apk`. Anotar os resultados em `docs/NOTES.md`.

## Som e latência pelo alto-falante do celular
| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 1 | Abrir o Volt e tocar em "Tocar Lá" | Soa um Lá contínuo; "Soltar" silencia em menos de 1 s | |
| 2 | Ler a linha de diagnóstico | Taxa 48000, "Baixa latência: sim"; anotar Bloco, Buffer, Latência de saída, Estouros | |
| 3 | Mover `filter.cutoff` e `filter.reso` com a nota soando | O timbre abre e fecha sem estalos nem "degraus" | |
| 4 | Trocar `filter.model` entre 0, 1, 2 e 3 | Os quatro soam; anotar se algum estala na troca | |

## Com o Yamaha CK61 (cabo USB-B → USB-C na porta USB TO HOST)
| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 5 | Plugar o CK61 | "MIDI: 1 conectado(s)" | |
| 6 | Tocar teclas, forte e fraco | Notas com dinâmica; acordes de até 16 notas | |
| 7 | Sensação de atraso entre tecla e som (som pelo celular) | Anotar: imperceptível / perceptível / incomoda | |
| 8 | Roda de pitch bend | Sobe e desce 2 semitons | |
| 9 | Pedal de sustain | Segura as notas | |
| 10 | Mexer em cada controle do painel do CK61 e ler "última mensagem" no topo | Anotar em NOTES.md o que cada controle envia (CC e canal) ou "nada" | |
| 11 | Desplugar o CK61 com notas soando | As notas param | |

## Com o CK61 também como saída de som
| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 12 | Som saindo pelo CK61; ler o diagnóstico | Anotar Baixa latência (sim/NÃO), Bloco, Buffer, Latência de saída, Reinícios | |
| 13 | Sensação de atraso | Anotar como no passo 7 | |

## Carga
| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 14 | `osc2.level` e `osc3.level` em 1, segurar 16 notas (pedal), para cada `filter.model` | Anotar "Carga do motor" e se há estouros; meta: abaixo de 30 % | |
| 15 | `voice.mode` em 1 (mono), 2 (legato) e 3 (unison) | Uma nota por vez; em unison o som engrossa | |

Observação: Ladder (0) e Prophet (3) saem ~6 dB mais baixos que os Roland (1 e 2) no mesmo preset; ao trocar `filter.model` o volume muda por isso.
