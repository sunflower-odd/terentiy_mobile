# План разработки. «Терентий» — Stage 01, мобильное приложение (Android)

**Статус:** актуален для контракта 1.0.0; по 1.1.0-draft см. [migration-to-contract-1.1.md](migration-to-contract-1.1.md)
**Версия:** 2.0
**Дата:** 13 сентября 2026 года
**Заменяет:** [`docs/archive/stage-01-mobile-plan-2026-08-13.md`](stage-01-mobile-plan-2026-08-13.md)
**Контракт:** [`contracts/stage01-mobile-api.yaml`](../../contracts/stage01-mobile-api.yaml) — копия из `terentiy_ai`, коммит `5cc250f`, 6 сентября 2026
**Связанные документы:** `terentiy_ai/docs/SDD-000/SDD-000_Terentiy.md`, `stage-01-specification.md`, `stage-01-mobile.md`, `contracts/api.md`

---

## 1. Почему версия 2.0

Версия 1.0 писалась contract-first в августе 2026, когда backend ещё не существовал.
Это было правильное решение — мобильная разработка не должна была ждать. Но контракт
не был согласован с обеих сторон, и к сентябрю API разошёлся практически полностью.

| Планировалось (v1.0) | Реализовано в `terentiy_ai` |
|---|---|
| `POST /auth/login` — email и пароль → access + refresh JWT | `POST /api/v1/mobile/activate` (код установки + `device_id` + PIN) и `POST /api/v1/mobile/login` (`device_id` + PIN) → `{access_token, token_type, household_id}` |
| `POST /auth/refresh` | Отсутствует. Токен opaque, без срока жизни |
| `GET /system/status` → `sites[]` с `camera`, `agent`, `person_presence`, `server_time` | `GET /api/v1/agents` → `{items: [AgentStatus]}`, только технические флаги |
| `GET /events`, `GET /events/{id}` | Отсутствуют. Ни таблицы, ни эндпоинта |
| Ошибки `{error: {code, message}}` | Плоский `{code, message}` |
| `PRESENT` / `NOT_PRESENT` / `UNKNOWN` | `person_present` только внутри `latest-image`, вместе с двумя base64-JPEG |

**Вывод для процесса:** оригинал контракта принадлежит `terentiy_ai`. В этом репозитории
лежит синхронизированная копия с явно указанным коммитом источника. Правки вносятся
в `terentiy_ai` и переносятся сюда целиком.

---

## 2. Объём первой версии приложения

Три экрана:

1. **Активация** — первое подключение к установке по коду, задание PIN.
2. **Вход** — PIN на уже активированном устройстве.
3. **Главная** — приветствие и текущий статус наблюдения.

Всё остальное — журнал событий, карточка события, медиа, push — вне объёма
и описано в [`docs/backlog-mobile.md`](../backlog-mobile.md).

---

## 3. Что backend отдаёт на самом деле

| Метод и путь | Тело запроса | Ответ |
|---|---|---|
| `POST /api/v1/mobile/activate` | `installation_code`, `device_id`, `pin` (`^\d{6}$`) | `201` `{access_token, token_type, household_id}` |
| `POST /api/v1/mobile/login` | `device_id`, `pin` | `200`, то же тело |
| `GET /api/v1/agents` | — | `{items: [AgentStatus]}` |
| `GET /api/v1/agents/{agent_id}/latest-image` | — | метаданные + два JPEG в base64 |

`AgentStatus` содержит: `id`, `household_id`, `name`, `camera_id`, `software_version`,
`created_at`, `online`, и nullable `reported_at`, `agent_online`, `camera_connected`,
`stream_active`, `ml_ready`, `queue_size`, `last_frame_at`.

**Присутствия человека в этом ответе нет.** `person_present` и `person_count` живут
в `detection` внутри `latest-image`, отдать их без картинок backend не умеет.

---

## 4. Принятые решения

### 4.1. Статус наблюдения = техническое состояние контура

До появления лёгкого источника присутствия главный экран показывает здоровье контура,
а не наличие человека. Пять состояний, вычисляются на клиенте в порядке приоритета:

| Состояние | Условие | Что видит пользователь |
|---|---|---|
| `NO_AGENT` | `items` пуст | «Агент не подключён» |
| `AGENT_OFFLINE` | `online == false` | «Локальный агент недоступен» |
| `CAMERA_UNAVAILABLE` | `camera_connected != YES` или `stream_active != YES` | «Камера недоступна» |
| `ML_NOT_READY` | `ml_ready != YES` | «Анализ не выполняется» |
| `WATCHING` | остальное | «Наблюдение активно» |

Это чистая функция без Android-зависимостей. Когда backend научится отдавать
присутствие, она расширяется, а не переписывается. См. [ADR-0001](../adr/0001-istochnik-statusa.md).

### 4.2. Кадр — по кнопке «Получить вид»

`latest-image` возвращает два JPEG в base64 в одном JSON — сотни килобайт. Опрашивать
такое каждые 20 секунд ради одного булева поля нельзя. Кадр запрашивается только по
явному нажатию. См. [ADR-0002](../adr/0002-kadr-po-zaprosu.md).

### 4.3. `Boolean?` — это три состояния

Nullable-флаги в `AgentStatus` означают «агент ни разу не присылал heartbeat», а не
«выключено». Доменная модель использует `enum Tri { YES, NO, UNKNOWN }`. Это тот же
принцип, ради которого в контракте v1.0 существовал `PersonPresenceState` с тремя
значениями (SDD-000 §6). См. [ADR-0003](../adr/0003-tri-vmesto-nullable-boolean.md).

### 4.4. Свежесть данных считает сервер

`server_time` в API нет — и не нужен. `TerentiyService.list_agents` уже сравнивает
`reported_at` с текущим временем по порогу `agent_offline_after_seconds` (30 с) и кладёт
результат в `online`. Часы телефона в расчёте не участвуют вообще, что надёжнее
исходного замысла. `last_frame_at` показывается справочно, UTC → локальная зона.

### 4.5. `device_id` генерируется приложением

UUIDv4 при первом запуске, хранится в EncryptedSharedPreferences. `ANDROID_ID`
не используется: это идентификатор устройства, а не приложения.
См. [ADR-0004](../adr/0004-proiskhozhdenie-device-id.md).

### 4.6. Адрес сервера вводится в приложении

Backend на Stage 01 живёт на ноутбуке разработчика, IP меняется от сети к сети.
Поле «Адрес сервера» на экране активации, предзаполненное из `BuildConfig.DEFAULT_SERVER_URL`,
сохраняется в DataStore. Иначе под каждый прогон пришлось бы пересобирать APK.

Cleartext-трафик разрешён точечно через `network_security_config.xml`
(`10.0.2.2` и приватные подсети), а не глобальным `usesCleartextTraffic`.

### 4.7. Имя пользователя хранится локально

Backend после входа отдаёт только `household_id`. Имя спрашивается на экране активации
и живёт в DataStore. Backend ради приветствия не дорабатывается.

---

## 5. Технологический стек

### Используется сейчас

| Область | Выбор | Роль |
|---|---|---|
| Язык | Kotlin | Корутины и Flow нужны для polling |
| minSdk / targetSdk | 26 / 37 | Как в шаблоне репозитория |
| UI | Jetpack Compose + Material 3 | Экранов мало, состояний много |
| Архитектура | MVVM, однонаправленный поток UiState → ViewModel → Repository | Тестируемо, расширяемо |
| Модульность | Один модуль `:app`, package-by-feature | Мультимодульность для одного разработчика преждевременна |
| DI | Hilt | Стандарт, нужен для подмены источников в тестах |
| Сеть | Retrofit + OkHttp + kotlinx.serialization | Интерцепторы для токена и адреса сервера |
| Асинхронность | Coroutines + Flow | Lifecycle-aware polling |
| Секреты | AndroidKeyStore + SharedPreferences | `device_id` и токен, AES/GCM. Jetpack Security не используется: библиотека помечена устаревшей, а два значения умещаются в один класс без внешней зависимости |
| Настройки | DataStore Preferences | Имя, `household_id`, адрес сервера |
| Навигация | Navigation Compose | Четыре маршрута |
| Сборка | Gradle KTS + version catalog | Один источник версий |
| Тесты | JUnit, MockWebServer, Turbine, Compose UI Test | Контрактные и state-тесты дают основную ценность |
| Диагностика | Timber | Технические коды пользователю не показываются |

### Отложено до появления журнала событий

Room, Paging 3, Coil, Media3 и refresh-токены исключены осознанно: им нечего
обслуживать, пока backend не отдаёт события и медиа. Подробности и условия
возврата — в [`docs/backlog-mobile.md`](../backlog-mobile.md).

---

## 6. Структура репозитория

```
terentiy_mobile/
├── app/src/main/java/ru/terentiy/mobile/
│   ├── core/            — сеть (Retrofit, интерцепторы), ошибки, время, DI-модули
│   ├── data/
│   │   ├── remote/      — API-интерфейс, DTO, мапперы DTO → domain
│   │   ├── local/       — SecureTokenStorage, AppPreferences
│   │   └── repository/  — AuthRepository, AgentsRepository
│   ├── domain/          — AgentStatus, Tri, WatchState, функция сводного статуса
│   └── ui/
│       ├── theme/
│       ├── activation/
│       ├── pin/
│       └── home/
├── contracts/           — синхронизированная копия OpenAPI из terentiy_ai
└── docs/
    ├── stage-01-mobile-plan.md   — этот документ
    ├── backlog-mobile.md         — отложенное и запросы к backend
    ├── adr/                      — принятые архитектурные решения
    └── archive/                  — план v1.0, написанный до готовности backend
```

---

## 7. Этапы работ

| # | Этап | Содержание |
|---|---|---|
| M0 | Документы и контракт | Синхронизация контракта, архив плана v1.0, этот документ, backlog, ADR |
| M1 | Каркас | Version catalog, Hilt, KSP, serialization, Navigation, Timber, BuildConfig, network security config |
| M2 | Сеть и ошибки | `TerentiyApi`, DTO, мапперы, интерцепторы, `ErrorMapper`, sealed `AppError` |
| M3 | Хранилище | `SecureTokenStorage`, `AppPreferences` |
| M4 | Активация | Экран, валидация, `POST /mobile/activate` |
| M5 | Вход по PIN | Экран, `POST /mobile/login`, splash-маршрутизация |
| M6 | Главная | `AgentsRepository`, сводный статус, polling 20 с, pull-to-refresh |
| M7 | «Получить вид» | `latest-image` по требованию, person / motion |
| M8 | Тесты | Unit, MockWebServer, Compose UI |
| M9 | Сквозная проверка | Сборка и прогон с живым backend и агентом |

---

## 8. Проверка результата

**Автоматически:**

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Unit-тесты: маппер `null → Tri.UNKNOWN`, функция сводного статуса по всей матрице,
`ErrorMapper` на трёх формах тела ответа, конвертация времени UTC → локальная зона.
MockWebServer: `201`, `200`, `401`, `409`, `422` в формате pydantic, `404`, `500`,
таймаут, повреждённый JSON. Compose UI: три экрана в основных состояниях.

**Вручную,** при запущенных `run_db_provision.py`, `run_server.py` и `run_agent.py`:

| Сценарий | Ожидаемо |
|---|---|
| Активация кодом `TEST-INSTALLATION-CODE-001` | Успех, главная, приветствие по имени |
| Активация неверным кодом | `401 invalid_installation_code` → «Неверный код установки» |
| Повторная активация того же устройства | `409 device_already_activated`, понятный текст без крэша |
| PIN из пяти цифр | Остановлен клиентской валидацией, запрос не уходит |
| Перезапуск приложения | Открывается главная, **не** экран PIN: токен переживает перезапуск. Экран PIN после активации сейчас недостижим — см. [backlog §5](../backlog-mobile.md) |
| Неверный PIN | `401 invalid_pin`, инлайн-ошибка |
| Агент работает | «Наблюдение активно», время кадра в локальной зоне |
| Остановить агента, подождать >30 с | «Локальный агент недоступен» |
| Отключить камеру от RTSP | «Камера недоступна», агент остаётся online |
| Агент ни разу не слал heartbeat | Флаги `UNKNOWN`, не «выключено» |
| «Получить вид» после публикации | Две картинки, `person_count`, время кадра |
| «Получить вид» до первой публикации | `404 image_not_available` → «Агент ещё не присылал кадр» |
| Остановить backend | Баннер отсутствия связи поверх последних данных |

**На реальном телефоне:** `host = "0.0.0.0"` в `config/server.toml` репозитория
`terentiy_ai`, LAN-адрес ноутбука в поле «Адрес сервера», одна сеть.

---

## 9. Риски

| Риск | Смягчение |
|---|---|
| Контракт снова разъедется | Владелец — `terentiy_ai`, здесь копия с коммитом источника; изоляция DTO от доменных моделей локализует правки в мапперах |
| Статус наблюдения не показывает главного — присутствия человека | Явно проговорено в UI и документации; функция сводного статуса спроектирована под расширение |
| PIN подбирается перебором | Ограничение нужно на сервере; клиентская задержка — паллиатив. Запрос к backend зафиксирован в backlog |
| Токен не истекает и не отзывается | При `401` чистим локальный токен и просим PIN. Запрос к backend зафиксирован в backlog |
| HTTP без TLS в локальной сети | Приемлемо для Stage 01, точечный network security config вместо глобального разрешения |
