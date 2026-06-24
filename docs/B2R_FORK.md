# b2r — деконструкция форка Primal-Android

Этот документ — карта переноса Primal-Android в b2r и живой трекер вивисекции.
Главный принцип: **Primal используется только как UI-оболочка**. Слои данных,
сети и кэширования переписываются под локальный кэш + прямую P2P-репликацию.

## Базовая ветка
`claude/primal-android-fork-decon-zf1ynu` (baseline = `PrimalHQ/primal-android-app@main`).

## Соответствие ТЗ ↔ реальная структура

ТЗ писалось под старую версию Primal. Актуальный upstream — Kotlin Multiplatform
с другими именами модулей:

| Термин ТЗ | Реальный модуль/класс |
|---|---|
| `:core:network` | `:core:networking-primal`, `:core:networking-http`, `:core:caching` |
| `:core:database` | `:data:caching:local`, `:data:shared:local` (Room + SQLCipher) |
| `:core:designsystem`, `:core:ext` | UI в `:app`/`:shared`, утилиты в `:core:utils` |
| `FeedRepository`, `UserRepository` | репозитории в `:data:caching:repository`, `:data:account:*` |
| `PrimalVertex`, `PrimalCacheWebSocket` | `PrimalApiClient` + сокеты в `:core:networking-primal` |
| Nostr SDK | библиотека **Quartz** + `:core:nips`, `:domain:nostr` |

## Архитектурные решения
- **Источник базы:** актуальный `main` upstream Primal.
- **Модель данных ленты:** **LWW (last-write-wins) + tombstones**, как в
  `FLEXIY0/todo/sync.js` (union-merge по полю `mt`, логические часы). Строгий
  append-only с `sequence_id` из ТЗ заменён на LWW по решению владельца; колонка
  `sequence_id` сохраняется как логический счётчик/`mt`, не как иммутабельный индекс.
- **P2P-транспорт (план):** публичные MQTT-брокеры как «брокеры обнаружения»
  + retained «почтовый ящик»; прямая репликация по WebRTC. На первом этапе —
  интерфейсы и заглушки (реальный транспорт позже).
- **Ключи:** secp256k1 в Android Keystore, отображение `npub…` → `b2r_pub…`.

## Blast radius (важно)
- `:core:networking-primal` / `:core:caching` — зависимы ~10 модулей.
- Nostr (Quartz) / `:domain:nostr` / `:core:nips` — вплетены в ~20 модулей (весь data-слой).
- Полное удаление модулей каскадно ломает компиляцию графа, поэтому вивисекция
  идёт **по шву** (стратегия ТЗ: интерфейсы репозиториев сохраняются, меняется
  реализация), а не сносом модулей целиком.
- ⚠️ Сборку KMP/Android-проекта нельзя верифицировать в dev-контейнере
  (нет Android SDK). Верификация — через CI (`.github/workflows/b2r-ci.yml`),
  job `compile` = `compileAospDebugKotlin` на ubuntu. **Спринты 1.2 + 1.3
  подтверждены зелёной Android-компиляцией** (commit `790cd1d`).
- ℹ️ Полный upstream `detekt` агрегирует все KMP-таргеты, включая iOS
  (Kotlin/Native Apple), которые не собираются на Linux-раннере (нужен macOS).
  b2r — Android-форк, поэтому detekt из CI убран; при желании вернуть отдельной
  macos-job или после удаления iOS-таргетов из сборки.

---

## Трекер спринтов

### Шаг 1 — Модульная вивисекция

- [ ] **1.1 Изоляция UI** — `:app`, дизайн-система, `:core:utils` не трогаем;
      выписать точки, где `:app` напрямую зовёт сеть/Nostr (швы для Шага 2).
- [x] **1.2 Вырезание Primal API (egress)**
  - [x] `core/app-config`: дефолтные эндпоинты Primal → `wss://disabled.b2r.invalid`
        (`.invalid` не резолвится — гарантированно ноль egress).
  - [x] `core/app-config`: отключена динамическая подгрузка `.well-known` с `primal.net`.
  - [x] `app` `NetworkingModule`: базовый хост Retrofit → `disabled.b2r.invalid`.
  - [x] Media/CDN: `BlossomRepository`, `MediaUploadsSettingsViewModel`,
        `OnboardingApi` (suggestions) → `disabled.b2r.invalid`.
  - [ ] *(остаётся)* Гард в сокет-клиенте: жёсткий запрет коннекта к `*.primal.net`
        на уровне `NostrSocketClientImpl` (defense-in-depth; сейчас egress уже
        перекрыт на уровне конфигов/DI).
  - _Примечание: `NetworkSettingsScreen` `wss://cache.primal.net/v1` — это
    превью-семпл (Compose @Preview), не реальный egress; не трогаем._
- [~] **1.3 Очистка Nostr SDK** — *в работе*
  - [x] Публикация в реле заглушена по шву: `RelaysSocketManager.publishEvent` /
        `publishNwcEvent` / `tryConnecting*` — no-op, сокеты к реле не открываются.
  - [ ] *(остаётся)* Снять зависимость Quartz / `:core:nips` / `:domain:nostr`
        из data-слоя (крупный рефакторинг ~20 модулей; делать по шву).
        Логику ключей secp256k1 сохранить (нужна в 2.2).
- [~] **1.4 Пересборка Room** (`:data:caching:local`) — *в работе*
  - [x] Введена b2r-схема лента-лога: entity `B2rFeedEntry` + DAO `B2rFeedEntryDao`
        (`authorPubkey`, `sequenceId`, `createdAt`, `modifiedAt`/LWW, `deleted`-tombstone,
        `signature`). Зарегистрирована в `PrimalDatabase`, версия 34→35.
        `fallbackToDestructiveMigration=true` уже включён → апгрейд безопасен.
  - [ ] *(остаётся)* По мере переноса репозиториев (Шаг 2) ретайрить старые
        Nostr-event entity (events/notes/reposts и пр.).

### Шаг 2 — Замена «сердца»
- [ ] **2.1 Репозитории** (`:data:caching:repository`) — `fetchFeed()` читает из Room
      + пингует `P2pReplicationService` (брокеры обнаружения → докачка по WebRTC).
- [ ] **2.2 Ключи профиля** (`:data:account:*`) — secp256k1 из Keystore, префикс `b2r_pub`.
- [ ] **2.3 Модуль `:core:p2p`** — `DiscoveryBroker` (MQTT), `DirectReplicator` (WebRTC),
      merge-слой (LWW). Сначала интерфейсы + заглушки.
