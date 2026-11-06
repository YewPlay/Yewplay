# Auditoria Técnica — YewPlay MultiEngine + AndroidX Media3

**Data:** 2026-11-06 · **Base:** `yewplay/dev` @ `7252042b` · **Branch de trabalho:** `yewplay/multiengine`

---

## 1. Arquitetura atual do YewPlay

- **App**: fork NewPipe 0.29.1, GPL-3.0. Módulos KMP: `core/{data,designsystem,model}`, `feature/*` (9 módulos), `shared`, `app`, `desktopApp`, `iosApp`.
- **Extração**: `NewPipeExtractor` (commit `e8f89145`, via JitPack) — YouTube, SoundCloud, PeerTube, Bandcamp, media.ccc.de. Toda extração passa por `util/ExtractorHelper.java` (RxJava3 `Single`s) e `NewPipe.getService(id)`.
- **Reprodução**: **ExoPlayer 2.19.1 legado** (`com.google.android.exoplayer`, 8 artefatos incl. extension-mediasession). Superfície: **46 arquivos / 308 referências** ao pacote exoplayer2 — `player/Player.java` (motor), `playqueue`, `mediasource`, `resolver`, `datasource`, `notification`, `mediasession`, `helper`.
- **UI**: Kotlin+Compose (migração concluída) + Views legadas (detalhe, settings host, RouterActivity).
- **Downloads**: `us/shandian/giga` (gerenciador próprio) — streaming progressivo HTTP, independente do player.

## 2. Arquitetura do NewTube (aleixrodriala/newtube)

- **"SmartTube para celulares"**, Java, **MIT**, ativo (push 2026-10-29).
- Monorepo com submódulos: **`MediaServiceCore`** ("Unofficial Java API for YouTube") + `SharedModules`, mais módulos de UI (`smarttubetv`, `sabr-media3`, `doubletapplayerview-media3`).
- **MediaServiceCore** (o motor em si):
  - `mediaserviceinterfaces/` — contratos limpos: `MediaItemService`, `ContentService`, `CommentsService`, `SignInService` + modelos (`MediaItem`, `MediaFormat`, `MediaSubtitle`, `MediaGroup`…).
  - `youtubeapi/` (~1070 arquivos, pacote `com.liskovsoft.youtubeapi`) — implementação InnerTube: retrofit2 + gson + json-path + commons-io. Biblioteca Android (`com.android.library`), Java 17, minSdk herdável.
- **É um motor de extração real e independente** (não usa NewPipeExtractor). Baseado na API interna do YouTube (InnerTube), mesma linhagem SmartTube.

## 3. Arquitetura do FlowTube (Flow-Tube/Flow)

- Kotlin, **GPL-3.0**, muito ativo (push 2026-11-06), app monomódulo (~2060 arquivos Kotlin).
- **Dois mecanismos de extração**:
  1. Pacote próprio `innertube/` (143 arquivos: `InnerTube.kt`, `YouTube.kt`, `models/`, `pages/`) — cliente InnerTube em Kotlin com **ktor client + kotlinx.serialization** (linhagem OuterTune/InnerTune).
  2. `NewPipeExtractor v0.26.5` como dependência (uso complementar — o pacote innertube importa `org.schabi`).
- **Player: AndroidX Media3 1.11.0 já em produção** (session, ui, exoplayer, hls, dash, datasource-okhttp, muxer) — referência de arquitetura para a nossa migração.
- Acoplamento do pacote `innertube` ao app: ~20 imports externos (`FlowApplication`, `PlayerPreferences`, `data.model.*`, `utils.*`, `player.sabr.proto`). Não é uma biblioteca pronta — exigiria vendor + adaptação.

## 4. Componentes aproveitáveis

| Origem | Componente | Como aproveitar |
|---|---|---|
| NewTube | `MediaServiceCore` (mediaserviceinterfaces + youtubeapi) | Dependência JitPack → adaptador `NewTubeEngine` |
| FlowTube | Pacote `innertube/` (GPL-3.0) | Vendor seletivo → adaptador `FlowEngine` |
| FlowTube | Arquitetura Media3 (PlayerManager, service, resolver) | Referência de design para `MediaPlaybackCore` |
| YewPlay | NewPipeExtractor + ExtractorHelper | `NewPipeEngine` (preservado, sem tocar internals) |

## 5. Componentes incompatíveis

- **NewTube UI** (`smarttubetv`, leanback/TV patterns): incompatível com a identidade YewPlay — fora.
- **FlowTube app inteiro**: 2060 arquivos acoplados (sync P2P, widgets, discord, TV) — não copiar.
- **ExoPlayer2 × Media3**: API surfaces diferentes; coexistência temporária possível mas a meta é substituir (regra: não misturar indevidamente).
- **`SharedModules` (submódulo do MediaServiceCore)**: necessário no build — JitPack inicializa submódulos, mas é o principal risco de integração (mitigação: fallback para vendor do módulo).

## 6. Dependências necessárias

- `com.github.aleixrodriala.MediaServiceCore:youtubeapi:<commit>` (traz retrofit2, gson, json-path, commons-io).
- Vendor `innertube/` do FlowTube: ktor-client-okhttp + kotlinx-serialization-json (kotlinx.serialization já existe no projeto).
- Media3 (fase 10+): `media3-exoplayer`, `media3-session`, `media3-ui` (parcial), `media3-exoplayer-hls`, `media3-exoplayer-dash`, `media3-datasource-okhttp`, `media3-common` — versão única centralizada no catalog.

## 7. Conflitos de bibliotecas

- **gson** (MediaServiceCore) × **kotlinx.serialization** (FlowTube/YewPlay): coexistem sem conflito (serializadores distintos).
- **okhttp**: já presente (extractor/downloader); alinhar versões no catalog.
- **exoplayer2 × media3**: pacotes diferentes (`com.google.android.exoplayer2` × `androidx.media3`) — não conflitam no classpath; conflito é arquitetural (dois players). Remoção do exo2 só após paridade Media3 comprovada.
- **gson × NewPipeExtractor**: extractor usa nanojson — sem conflito.

## 8. Compatibilidade de licenças

| Componente | Licença | Compatível com GPL-3.0? |
|---|---|---|
| YewPlay/NewPipe | GPL-3.0 | — (base) |
| NewPipeExtractor | GPL-3.0 | ✅ mesma |
| MediaServiceCore (NewTube) | **MIT** | ✅ com atribuição |
| FlowTube innertube | GPL-3.0 | ✅ mesma (manter copyright aedev) |
| AndroidX Media3 | Apache-2.0 | ✅ |

Ações: manter cabeçalhos de copyright nos arquivos vendidos; registrar origem em `docs/` e no THIRD_PARTY/notices se houver.

## 9. Estratégia de integração

1. **Contrato único** `ExtractionEngine` (Kotlin, coroutines) no app: `search`, `getStreamInfo`, `getChannelInfo`, `getPlaylistInfo`, `suggestions`, `capabilities`.
2. **Modelos padronizados** (`engine/model`): `YpStream`, `YpVideoInfo`, `YpChannelInfo`, `YpMediaFormat`, `YpSubtitle`… Cada motor converte seus tipos.
3. **EngineManager**: registry + seleção persistida (SharedPreferences) + capabilities + fallback configurável (opt-in, sem loop, sem mudar preferência do usuário).
4. **Motores**: `NewPipeEngine` (adapta o extractor existente — internals intocados), `NewTubeEngine` (MediaServiceCore), `FlowEngine` (innertube vendido).
5. **UI**: nova seção *Configurações → Motor de Extração* (Compose, padrão YpPreferences); telas consomem modelos padronizados via adaptadores — sem condicionais de motor espalhadas.
6. Sem remoção do NewPipeExtractor; NewPipeEngine segue padrão e sempre disponível.

## 10. Plano de migração Media3

- **Escala**: 46 arquivos / 308 refs exoplayer2; núcleo em `player/Player.java` (~1600 linhas), playqueue, mediasource, datasource, notification, mediasession.
- **Estratégia**: camada nova `player/media3/` (`MediaPlaybackCore`, `MediaItemFactory`, `MediaSourceFactory`) construída ao lado; `Player.java` passa a delegar para o núcleo Media3 **somente** quando a paridade (queue, resolver, recuperação de erro, background, notificação) estiver comprovada por build+testes; exo2 removido ao final.
- **MediaSession**: substituir `extension-mediasession` (exo2) por `media3-session` + `MediaSessionService` (player service atual adaptado).
- **Manter**: identidade do chrome Compose (já migrado), gestos, queue semantics, downloads giga (independentes do player).
- **Riscos**: `extension-mediasession` exo2 → reescrita da ponte de sessão; `PlayerDataSource`/`resolver` custom (headers por stream) → portar para `media3-datasource-okhttp` + `ResolvingDataSource`; seekbar preview (storyboard) → adaptar ao modelo Media3.

---

### Conclusão da auditoria

A integração é **tecnicamente viável** com esforço real e delimitado: NewTube entra como biblioteca (risco baixo/médio — build JitPack com submódulo), FlowTube entra como vendor seletivo do pacote `innertube` (risco médio — ~20 acoplamentos a resolver), Media3 migra por camada paralela (risco controlado, sem tocar no motor de extração).

---

## Adendo — FASE 6 (NewTube): decisão de integração por vendor

- **JitPack descartado**: o build do `aleixrodriala/newtube` falha no JitPack
  (`publishToMavenLocal` inexistente; submódulos git não resolvidos). Log consultado em
  2026-11-06. Alternativa por fork próprio foi rejeitada para não criar dependência externa frágil.
- **Vendor em `engine/newtube/`** (MIT, atribuição mantida nos fontes):
  - `mediaserviceinterfaces` + `youtubeapi` de `aleixrodriala/MediaServiceCore` @ `52fecea0`
    (NewTube é fork de yuliskov/SmartTube; copyright yuliskov 2020-present, MIT).
  - `sharedutils` de `aleixrodriala/SharedModules` @ `25213805` (pin real do app NewTube;
    o repo `yuliskov/SharedModules` ainda é RxJava2 e não compila com o MediaServiceCore do fork, que é RxJava3), MIT.
- **Adaptações mínimas** (nenhuma no código-fonte): builds Groovy → `build.gradle.kts` do padrão
  YewPlay; remoção do atributo `package` do manifest do sharedutils (obrigatório desde AGP 8);
  j2v8 consumido do Maven Central (`com.eclipsesource.j2v8:j2v8:6.2.1`) em vez do módulo local
  com 116 MB de `.so`; nanojson resolvido pelo fork TeamNewPipe já presente no app
  (mesmo pacote `com.grack.nanojson`, evita classe duplicada no dex).
- **Tamanho**: o j2v8 (V8) adiciona dezenas de MB ao APK, mas é exigido em runtime pelos
  resolvedores sig/nsig/poToken do youtubeapi — sem ele o motor não reproduz.
- **Inicialização**: `GlobalPreferences.instance(context)` é obrigatório antes do primeiro uso
  (`MediaServiceData` lança `IllegalStateException` sem ele); feito lazy no `NewTubeEngine`.

## Adendo — FASE 7 (FlowTube): decisão de integração por vendor

- **Fonte**: `Flow-Tube/Flow` (GPL-3.0), commit `7ecb4766877b` (main, 2026-11-06), vendored em
  `engine/flow/` (módulo único, pacotes originais `io.github.aedev.flow.*` preservados).
- **Escopo vendored**: `innertube/` completo (143 arquivos) + suporte mínimo e auto-contido:
  `data/model/{Models,RichText}.kt`, `data/local/ThumbnailQuality.kt`,
  `data/localmedia/LocalMediaIds.kt`, `utils/` (7 arquivos), `utils/cipher/` (5),
  `network/` (proxy, 3), `player/sabr/proto/ProtobufReader.kt`,
  `player/stream/{SignatureCipher,PrerollWait,PlayabilityVerdict}.kt`.
- **Fora do escopo** (substituídos pela infra do YewPlay, não pelo engine): stack SABR de
  reprodução, poToken WebView, `InnerTubeVideoStreamExtractor` e toda a UI/player do Flow.
- **Adaptações documentadas** (mínimas, marcadas "YewPlay adaptation" no código):
  1. `InnerTube.kt`: flag RYTD lida de `PlayerPreferences` (DataStore) virou
     `var returnYouTubeDislikeEnabled` no `InnerTube` — o módulo é headless, sem DataStore.
  2. `PaidPromotionResponse.kt`: anotação `@Immutable` (Compose) removida — módulo sem Compose.
  3. `VpnStateMonitor.kt` (Hilt) deletado — não referenciado por nenhum arquivo do escopo.
  4. `yewplay/FlowYewPlayFacade.kt` (arquivo NOVO, não-upstream): fachada pública que expõe
     `searchParams`, `initialize` e `resolveStreams` (caminho TV_TIZEN com solução de
     signature/`n` via `PipePipeNsigDecoder`), mantendo os helpers `internal` do upstream
     (`YouTubeSearchParams`, `SignatureCipher`) encapsulados no módulo.
- **Dependências**: ktor client 3.5.2 (core/okhttp/content-negotiation/encoding/serialization),
  kotlinx-serialization-json 1.12.0 (catálogo), okhttp 5.5.0, NewPipeExtractor (o próprio Flow
  já usa `YoutubeParsingHelper`), annotation.
- **Adapter**: `FlowEngine` (`engine/impl/`), id `flow`, registrado no `App.kt` após NewTube.
  Busca de vídeos/canais/playlists via `YouTube.videoSearch` (params protobuf do próprio Flow);
  músicas via `YouTube.search(FILTER_SONG)`; streams via `FlowYewPlayFacade.resolveStreams`;
  legendas via `PlayerResponse.captions`; paginação por continuation tokens opacos.

## Adendo — FASE 8 (preservação NewPipe): verificação

- **Zonas protegidas intactas**: comparação `main...yewplay/multiengine` (API compare) não mostra
  nenhuma alteração em extrator/parsers, núcleo do player/ExoPlayer, `us/shandian/giga` (downloads)
  nem banco de dados. Todas as mudanças estão em `engine/`, `app/.../engine/`, settings e docs.
- **Engine padrão**: `EngineManager` continua com `newpipe` como padrão na ausência de preferência
  persistida (coberto por `EngineManagerTest`); nenhuma tela consome engines fora da tela de
  seleção, então o comportamento do app é idêntico ao de antes até o usuário trocar manualmente.
- **Extrator pinado**: `NewPipeExtractor` permanece no commit `e8f89145` do catálogo; o FlowEngine
  apenas consome `YoutubeParsingHelper` do mesmo artefato, sem alterá-lo.

## Adendo — FASE 9 (auditoria do player): mapa de migração Media3

**Estado atual**: ExoPlayer2 `2.19.1` (7 artefatos) + `androidx.media:media:1.7.1` (media1,
MediaSessionCompat/MediaBrowserServiceCompat). 46 arquivos com imports `com.google.android.exoplayer2`,
~308 referências. Upstream NewPipe dev também segue em ExoPlayer2 — não há migração pronta para portar.

**Versão alvo**: Media3 `1.11.1` (estável mais recente no Google Maven).

**Inventário por camada**

1. **Camada mecânica** (mesma API, troca de pacote `exoplayer2.*` → `media3.*`): `Player.java`
   (59 refs), `datasource/YoutubeHttpDataSource` (21), `helper/PlayerDataSource` (16),
   `ui/VideoPlayerUi.kt` (13), `resolver/PlaybackResolver` (11), `mediasource/*` (11+),
   `helper/{PlayerHelper,CacheFactory,LoadController,CustomRenderersFactory,
   CustomMediaCodecVideoRenderer,AudioReactor,PlayerHolder}`, `datasource/NonUriHlsDataSourceFactory`,
   UIs (`PlayerUi`, `MainPlayerUi`, `PopupPlayerUi`, `YpPlayerViews`), `ExpandableSurfaceView`,
   event listeners, `MediaItemTag`/`StreamInfoTag`, `ErrorInfo`, fragments de detalhe.
2. **Camada arquitetural** (API não existe no Media3, exige redesign real):
   - `MediaSessionConnector` (ext-mediasession) + `MediaSessionCompat` +
     `MediaBrowserServiceCompat` → `androidx.media3.session.MediaSession` +
     `MediaLibraryService`. Arquivos: `PlayerService` (extends MediaBrowserServiceCompat),
     `mediasession/{MediaSessionPlayerUi,PlayQueueNavigator,SessionConnectorActionProvider}`,
     `mediabrowser/{MediaBrowserImpl,MediaBrowserPlaybackPreparer,PackageValidator}`,
     `notification/NotificationUtil` (MediaStyle), `helper/PlayerHolder`.
   - `PlayQueueNavigator extends TimelineQueueNavigator` — sem equivalente Media3; a navegação
     da fila passa a `MediaSession.Callback`/comandos customizados.
   - `R` da ext-mediasession (ícones) e `exoplayer2.ui.R.string` (28 refs) — strings/ícones
     passam a vir do `media3-ui` (nomes equivalentes) ou de recursos próprios.
3. **Zona excluída com 1 import**: `us/shandian/giga/util/Utility.java` usa apenas
   `exoplayer2.util.Util.toByteArray(InputStream)`. Decisão: troca de import para
   `androidx.media3.common.util.Util` (método idêntico, zero mudança de lógica) — a única
   alternativa seria duplicar código de IO, o que é pior. Documentada como exceção mínima.

**Decisões**

- **Estratégia incremental com CI verde a cada lote**: (a) FASE 10 adiciona Media3 lado a lado
  (os dois coexistem no classpath) + `MediaPlaybackCore`/`MediaItemFactory` novos, sem tocar no
  existente; (b) FASE 11 migra em lotes — datasource/mediasource/resolver/helper → Player/UIs →
  sessão/notificações → remoção do ExoPlayer2.
- **MediaPlaybackCore** (novo, independente de engines): contrato `engines → StreamResolver →
  MediaItemFactory → MediaPlaybackCore → Media3 → UI`. O `PlaybackResolver` atual ganha uma ponte
  que converte `YpMediaFormat`/`YpVideoInfo` do engine ativo em `MediaItem` Media3; o caminho
  NewPipe (`StreamInfo`) permanece intacto enquanto o engine padrão é `newpipe`.
- **Sem fake**: enquanto a sessão Media3 não estiver completa, a camada antiga continua ativa —
  nenhuma das duas é removida antes da substituta estar funcional e com CI verde.
