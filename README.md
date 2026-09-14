# Терентий — мобильное приложение (Android)

Клиент системы поведенческого мониторинга «Терентий» (SDD-000). Работает поверх
backend из репозитория [`terentiy_ai`](../terentiy_ai).

**Stage 01, объём первой версии:** активация устройства по коду установки, вход по PIN,
главный экран со статусом наблюдения и просмотром кадра по запросу.

---

## Что нужно для запуска

1. **Android Studio** с JDK 25 (версия задана в `gradle/gradle-daemon-jvm.properties`).
2. **Запущенный backend** из `terentiy_ai`:

   ```bash
   python run_db_provision.py   # один раз
   python run_server.py         # оставить работающим
   python run_agent.py          # оставить работающим
   ```

3. Код установки. Для локальной разработки он лежит в Git:
   `TEST-INSTALLATION-CODE-001` из `terentiy_ai/activation-codes.test.toml`.

## Адрес backend

Адрес вводится **на экране активации** и сохраняется на устройстве — пересобирать APK
под каждую сеть не нужно. Значение по умолчанию берётся из `BuildConfig.DEFAULT_SERVER_URL`.

| Где запускается приложение | Адрес |
|---|---|
| Эмулятор Android | `http://10.0.2.2:8080` — это хост-машина с точки зрения эмулятора |
| Реальный телефон | `http://<LAN-адрес ноутбука>:8080` |

Для реального телефона backend должен слушать не только loopback. В
`terentiy_ai/config/server.toml`:

```toml
[server]
host = "0.0.0.0"
```

Телефон и ноутбук должны быть в одной сети.

## Первый запуск

1. Собрать и установить: `./gradlew :app:installDebug` либо кнопкой Run.
2. На экране активации: адрес сервера, код установки, имя, PIN из шести цифр и его повтор.
3. Дальше приложение открывается по PIN.

Если PIN забыт, на экране входа есть «Активировать заново». Важно: для backend это
**новое устройство**, прежняя запись `mobile_devices` останется в базе. Смены PIN
на Stage 01 нет — см. [`docs/backlog-mobile.md`](docs/backlog-mobile.md) §5.

---

## Что показывает главный экран

Статус наблюдения на Stage 01 — это **техническое состояние контура**, а не присутствие
человека в кадре. Backend отдаёт `person_present` только внутри
`GET /api/v1/agents/{id}/latest-image`, вместе с двумя JPEG в base64; опрашивать такое
каждые 20 секунд ради одного булева поля нельзя.

| Что видно | Когда |
|---|---|
| Наблюдение активно | Агент на связи, камера открыта, поток идёт, модель готова |
| Анализ не выполняется | Кадры поступают, но модель распознавания не запущена |
| Камера недоступна | Агент работает, видеопоток не поступает |
| Локальный агент недоступен | Backend давно не получал heartbeat |
| Агент не подключён | К установке не привязано ни одного агента |

Кадр с камеры запрашивается кнопкой **«Получить вид»** на карточке агента —
по одному нажатию, без автообновления.

Подробности решений: [`docs/adr/`](docs/adr).

---

## Структура

```
app/src/main/java/ru/terentiy/mobile/
├── core/       — сеть (интерцепторы, вызов API), ошибки, форматирование времени
├── data/
│   ├── remote/ — Retrofit-интерфейс, DTO по контракту, мапперы DTO → domain
│   ├── local/  — защищённое хранилище (AndroidKeyStore), настройки (DataStore)
│   └── repository/
├── di/         — модули Hilt
├── domain/     — Agent, Tri, WatchState, вычисление состояния наблюдения
└── ui/         — activation, pin, home, navigation, theme
```

Экраны разделены на stateful-обёртку (`HomeScreen`) и stateless-содержимое
(`HomeContent`): содержимое проверяется Compose-тестами без графа зависимостей
и работает в превью Android Studio.

## Контракт

[`contracts/stage01-mobile-api.yaml`](contracts/stage01-mobile-api.yaml) — **копия**.
Оригинал принадлежит `terentiy_ai`
(`docs/SDD-000/stage-01-person-detection/contracts/mobile-server.openapi.yaml`).
В шапке файла указан коммит источника. Правки вносятся в `terentiy_ai` и переносятся
сюда целиком.

## Проверка

```bash
./gradlew :app:testDebugUnitTest      # маппинг, состояния, разбор ошибок, контракт на MockWebServer
./gradlew :app:connectedDebugAndroidTest  # экраны, нужен эмулятор или устройство
./gradlew :app:lintDebug
```

Сценарии ручного прогона — в [`docs/stage-01-mobile-plan.md`](docs/stage-01-mobile-plan.md) §8.

## Документация

| Документ | О чём |
|---|---|
| [`docs/stage-01-mobile-plan.md`](docs/stage-01-mobile-plan.md) | Актуальный план: решения, стек, этапы, проверка |
| [`docs/backlog-mobile.md`](docs/backlog-mobile.md) | Отложенное и запросы к backend: журнал событий, presence, токены, rate-limit |
| [`docs/adr/`](docs/adr) | Принятые архитектурные решения |
| [`docs/archive/`](docs/archive) | План v1.0, написанный до готовности backend |
