# YewPlay Motion Design System

Sistema centralizado de animações do YewPlay. Toda animação nova **deve** usar estes tokens e presets — não crie durações ou curvas avulsas.

## Onde vive

| Camada | Arquivo | Conteúdo |
|---|---|---|
| `shared` | `net.newpipe.app.theme.Motion.kt` | `MotionDurations`, `MotionEasings` (fonte única dos valores) |
| `shared` | `net.newpipe.app.theme.MotionAccessibility.kt` | `LocalReduceMotion`, `rememberPlatformReduceMotion()`, `motionSpec()` |
| `shared` | `net.newpipe.app.theme.MotionTransitions.kt` | Presets de entrada/saída (`fade`, `slideUp`, `slideInFromBottom`, `expand`, `pop`) |
| `core/designsystem` | `io.github.yewplay.core.designsystem.Motion.kt` | Modifiers: `ypPressFeedback`, `ypAnimateItem`, `ypAnimateGridItem`, `ypShimmer` |
| `app` (Views) | `org.schabi.newpipe.util.YpViewMotion` | Tokens como `Long`, interpoladores framework, `duration(context, base)` |

## Durações

| Token | Valor | Uso |
|---|---|---|
| `EXTRA_FAST` | 100 ms | microinterações, swaps de ícone, press |
| `FAST` | 150 ms | fades simples, seleção |
| `NORMAL` | 250 ms | visibilidade, expansões, abas |
| `MEDIUM` | 350 ms | transições de tela, diálogos, sheets |
| `LONG` | 450 ms | grandes superfícies (chrome do player, mini player) |

## Curvas (Material 3)

`Emphasized` (superfícies grandes), `Standard` (padrão), `StandardDecelerate` (entrando), `StandardAccelerate` (saindo), `Linear` (apenas progresso/loaders).

## Como usar (Compose)

```kotlin
// 1. Spec avulsa respeitando redução de movimento
val alpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = motionSpec(LocalReduceMotion.current, MotionDurations.FAST, MotionEasings.Standard),
    label = "fade"
)

// 2. Presets de transição
val (enter, exit) = MotionTransitions.slideUp()
AnimatedVisibility(visible = show, enter = enter, exit = exit) { /* ... */ }

// 3. Modifiers
Row(Modifier.ypAnimateItem(this)) { /* item de LazyColumn */ }
Box(Modifier.ypShimmer()) { /* skeleton */ }
Column(Modifier.ypPressFeedback(interactionSource)) { /* superfície clicável sem ripple */ }
```

## Como usar (Views legadas)

```kotlin
view.animate(true, YpViewMotion.NORMAL)          // duração tokenizada
animator.interpolator = YpViewMotion.standard    // curva M3
val d = YpViewMotion.duration(context, YpViewMotion.MEDIUM) // 0 se redução de movimento
```

## Acessibilidade

`AppTheme` provê `LocalReduceMotion` lendo a configuração do sistema (Android: *Remover animações*; iOS: *Reduzir movimento*). Todos os presets e modifiers colapsam para mudança instantânea — **o estado final é sempre aplicado**, nenhuma funcionalidade depende de animação. Em Views, use `YpViewMotion.duration(...)` ou verifique `isReduceMotionEnabled(context)`.

## Diretrizes

- Listas grandes (feed, busca): **não** anime itens. Use `ypAnimateItem` só em listas gerenciadas pelo usuário (playlists, fila, inscrições).
- Nada de elasticidade exagerada, rotações decorativas ou loops que distraiam da leitura.
- Loops contínuos (`rememberInfiniteTransition`) apenas para skeleton/carregamento, sempre respeitando redução de movimento.
- Player: animações só na camada de UI (chrome, capa); nunca no pipeline de reprodução.

## Exceções documentadas

- `YpVideoDetailFragment`: fade lento do hint "toque" (`1500ms` + delay `1000ms`) é intencional — um affordance passageiro, não uma transição de estado.
- `SecondsView` (fast seek ±10s): ciclo de 750ms herdado do NewPipe, ligado à física do gesto.
