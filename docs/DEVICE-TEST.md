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
