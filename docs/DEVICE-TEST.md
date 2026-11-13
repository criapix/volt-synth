# Teste no aparelho (plano 5: versão 1.0)

Antes de tudo: a versão assinada precisa existir. Rode `.\tools\make-keystore.ps1`, crie o `keystore.properties` (instruções no topo do script), guarde **uma cópia do `.jks` e das senhas** e rode `.\gradlew.bat :app:assembleRelease`. O APK sai em `app\build\outputs\apk\release\app-release.apk`.

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 1 | No app instalado hoje (debug antigo): navegador de presets → **Exportar**. Anotar as associações do MIDI Learn (⋮ → Controle MIDI), porque não sobrevivem | Arquivo `volt-presets.json` salvo | |
| 2 | Desinstalar o Volt antigo; `adb install app\build\outputs\apk\release\app-release.apk` | Ícone novo (raio laranja) na gaveta de apps, nome "Volt" | |
| 3 | Abrir, **Importar** o arquivo do passo 1 | Os seus presets voltam | |
| 4 | ⋮ → Sobre o Volt | Mostra 1.0.0, licença e bibliotecas | |
| 5 | Ouvir os presets com o CK61, de 044 a 064 (os novos) e os marcados "ajustar" em `docs/PRESET-REVIEW.md` | Anotar na folha | |
| 6 | Trocar de preset com ◀ ▶ tocando sempre a mesma frase | Volume parecido entre presets (o banco foi nivelado por medição; Caixa, Laser e Ondas do Mar ficam mais baixos por serem ruído) | |
| 7 | Carregar `104 Trompas` (o mais pesado na última medição no PC) e segurar 16 notas (pedal) por 10 s | Anotar "Carga do motor" em ⋮; se passar de 80 %, a barra mostra "x/12 vozes" em vermelho e o som continua sem estalos | |
| 8 | ⋮ → Polifonia máxima 8 (rolar o diálogo); tocar um acorde de 10 notas | Só 8 soam; a barra mostra "8/8 vozes" em vermelho; voltar para 16 | |
| 9 | Plano 4: efeitos (aba FX, ligar cada um com uma nota soando), arpejador (preset 041 e 064, Hold) e teclado na tela | Sem estalo ao ligar/desligar; arpejo no tempo; anotar como soam | |
| 10 | Plano 3: passos 3–5 da tabela do plano 3 (nomes dos controles do CK61, aprender um knob, aprender ▶) | Anotar CC de cada controle em NOTES | |
| 11 | ⋮ → Salvar log… | Arquivo salvo; mandar junto com o resultado | |
| 12 | Salvar preset com o teclado virtual aberto | O botão "Salvar como novo" fica alcançável (rolando ou depois do ✓ do teclado) | |

Posições: 044 = `093 Baixo Três Osciladores` (o primeiro novo), 041 = `090 Arpejo Clássico`, 064 = `113 Arpejo com Eco`.

---

# Teste no aparelho (plano 4: efeitos, arpejador, teclado)

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 1 | Aba FX: ligar cada efeito sozinho com uma nota soando | Liga e desliga sem estalo; cada um soa como o nome diz | |
| 2 | Chorus: modos I, II e I+II num pad | I e II largos e lentos (II mais rápido); I+II um tremor rápido e sutil, como num Juno | |
| 3 | Delay: tempo 375 ms, repetições 50 % | Ecos regulares que somem aos poucos | |
| 4 | Reverb: duração 1 s e depois 8 s | Cauda curta e depois longa; "Abafar" escurece a cauda | |
| 5 | Distorção num baixo: drive de 0 a 100 % | De limpo a saturado; "Tom" escurece | |
| 6 | Todos os efeitos ligados, 16 notas (pedal) | Anotar "Carga do motor" (⋮ → Configurações) e se há estouros | |
| 7 | Knob do CK61 que manda CC 91 (reverb) num preset sem reverb | O reverb liga e o mix acompanha | |
| 8 | Aba ARP: ligar, segurar um acorde | Arpejo no tempo; mudar modo, oitavas, divisão, tempo e duração enquanto toca | |
| 9 | Hold: tocar um acorde, soltar | Continua; um acorde novo substitui o anterior | |
| 10 | Ligar/desligar o arpejador com teclas apertadas | Nenhuma nota fica presa | |
| 11 | Preset "Arpejo Clássico" (041) | Arpejo com delay | |
| 12 | Botão "Teclas": tocar com vários dedos, deslizar entre teclas, trocar de oitava | Notas certas, sem notas presas; mais embaixo na tecla = mais forte | |
| 13 | Ouvir os presets com efeitos novos (pads, cordas, piano elétrico, órgão, leads) | Anotar por preset: melhorou / ajustar (o quê) | |

---

# Teste no aparelho (plano 3: MIDI Learn)

Com o CK61 ligado por USB. Depois do teste, rodar `tools\pull-log.ps1` (o log registra cada associação aprendida e cada ação).

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 1 | Na aba FILTRO, sem aprender nada, girar o knob do CK61 que manda CC 74 | O Cutoff na tela gira junto (depois de passar pelo valor atual, pela captura suave) | |
| 2 | Girar os knobs que mandam CC 73 e CC 72 | Ataque e release do envelope de amplitude mudam (aba ENV) | |
| 3 | Mexer em cada controle do painel do CK61 e ler ⋮ → Controle MIDI → "Última mensagem" | **Anotar em NOTES.md o nome de cada controle físico e o CC que ele manda** | |
| 4 | Tocar em MIDI na barra, tocar no knob Drive (aba OSC) e mexer num controle do CK61 | Aparece "Drive · MIXER ← CC xx"; o Drive passa a seguir esse controle na hora | |
| 5 | Aprender um botão do CK61 para ▶ (próximo preset) | Cada toque no botão avança um preset | |
| 6 | Se no passo 5 só um toque a cada dois funcionar | Em Controle MIDI, ligar "qualquer valor dispara" nessa associação; agora todo toque avança | |
| 7 | Aprender uma tecla para "Pânico" | A tecla para de tocar nota e corta o som | |
| 8 | Captura suave: com o Cutoff associado, trocar de preset e girar o knob do CK61 longe do valor | O som não pula; aparece "Cutoff: leve o controle até …"; ao passar pelo valor, assume | |
| 9 | Desligar a captura suave e repetir | O valor pula para a posição do knob | |
| 10 | Program Change pelo CK61 (se o teclado manda) | O preset da posição correspondente carrega | |
| 11 | Canal: escolher 2 em Controle MIDI | O CK61 (canal 1) para de tocar; voltar para Omni | |
| 12 | Fechar e abrir o app | As associações continuam lá | |
| 13 | Apagar uma associação e depois "Apagar todas" | Somem da lista; o mapa de fábrica continua funcionando | |

---

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

## Plano 6: motor FM / DX7
Parecer geral já dado por você em 12/11/2026 ("Já está funcionando perfeitamente"). Os passos abaixo são o que ainda não foi medido ou conferido com detalhe.

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 16 | Navegador: as prateleiras "DX7 ROM1A" … "ROM4B" (com os `.syx` em `app/src/main/assets/dx7/` ao compilar) | 32 vozes cada, nomes originais; ROM1A 1 é a posição 065 | |
| 17 | Carregar ROM1A 11 (E.PIANO 1) e tocar com velocity fraca e forte | O timbre muda com a velocity; sem estalo | |
| 18 | Uma voz com LFO e PMD (por exemplo TAKE OFF) com a roda de modulação do CK61 | A roda controla a profundidade do vibrato/efeito do LFO | |
| 19 | Aftertouch no CK61, se ele enviar (anotar se não envia) | Chega ao motor (pressão de canal); sem efeito se o patch não roteia para aftertouch | |
| 20 | Pedal de sustain | Segura as notas; ao soltar elas morrem no release do patch | |
| 21 | 16 notas FM seguradas (pedal), ler "Carga do motor" | Anotar o pico; meta abaixo de 30 %; sem estouros | |
| 22 | Trocar o seletor Motor (Analógico ↔ FM) com uma nota segurada | Sem travar nem estalar alto; a nota some e a próxima toca no motor novo | |
| 23 | Editar o nível do OP1 (aba OPERADOR) durante uma nota | O som muda sem pular; o preset passa a "editado" | |
| 24 | Importar um `.syx` pelo botão Importar (da pasta Downloads do celular) | Mensagem "N presets importados" dentro do navegador; nova prateleira "DX7 <nome do arquivo>" | |
| 25 | Exportar uma voz (segurar o cartão, Exportar .syx) e importar de volta | Arquivo de 163 bytes; volta como "DX7 Importados" com o sufixo "(2)" no nome | |
| 26 | Program Change 65 no CK61 | Carrega ROM1A 1 (BRASS 1) | |
| 27 | Reabrir o app depois de fechar | Volta o último preset (inclusive FM) e as abas FM | |
