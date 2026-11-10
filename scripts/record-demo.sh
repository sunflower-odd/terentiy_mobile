#!/bin/bash
# Запись видеоролика с экрана эмулятора: приложение проходит сценарий само.
#
#   ./scripts/record-demo.sh dump   — выгрузить дерево интерфейса (для настройки)
#   ./scripts/record-demo.sh demo   — «Показ без сервера», сервер и агент не нужны
#   ./scripts/record-demo.sh live   — живой прогон: сервер, агент, настоящее падение
#   ./scripts/record-demo.sh zones  — критические зоны в «Показе без сервера»:
#                                     создание и режим тревоги (запись — только зоны)
#   ./scripts/record-demo.sh pitch  — сырая запись для короткого ролика на питчинг:
#                                     главная, зона, режим тревоги, падение, запись;
#                                     метки эпизодов — docs/video/pitch-marks.txt
#   ./scripts/record-demo.sh shots  — скриншоты всех экранов в docs/video/shots/
#                                     (проверка, что всё помещается без прокрутки)
#
# Кнопки ищет по тексту, а не по координатам, поэтому переживает смену
# разрешения и перестановку полей. Ничего не устанавливает и не коммитит.
# Сырая запись кладётся в docs/video/ (эта папка в .gitignore, кроме готовых
# роликов), монтаж — отдельно.
set -uo pipefail

PKG=ru.terentiy.mobile
USER_NAME="Olga"              # adb печатает только латиницу
SERVER_URL="http://10.0.2.2:8080"
INSTALL_CODE="HOME-SETUP-CODE"
PIN="246810"
ZONE_NAME="Door"              # adb печатает только латиницу
# Зона на полу перед дверью демо-кадра: вершины в тысячных долях кадра.
ZONE_POINTS="490,680 700,680 720,775 470,775"
FALL_CLIP="$HOME/PycharmProjects/terentiy_ai/agent/var/video-prepared/fall-08.mp4"
INBOX="$HOME/PycharmProjects/terentiy_ai/agent/var/video-fragments"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTDIR="$ROOT/docs/video"
mkdir -p "$OUTDIR"

# ---------- adb ----------
ADB="$(command -v adb || true)"
for c in "$HOME/Library/Android/sdk/platform-tools/adb" \
         "${ANDROID_HOME:-/nonexistent}/platform-tools/adb" \
         "${ANDROID_SDK_ROOT:-/nonexistent}/platform-tools/adb"; do
  if [ -z "$ADB" ] && [ -x "$c" ]; then ADB="$c"; fi
done
[ -z "$ADB" ] && { echo "adb не найден. Укажите путь в переменной ADB."; exit 1; }
SERIAL="$("$ADB" devices | awk '/emulator-.*device$/{print $1; exit}')"
[ -z "$SERIAL" ] && { echo "Эмулятор не запущен (adb devices пуст)."; exit 1; }
a() { "$ADB" -s "$SERIAL" "$@"; }
# Размер экрана: от него считаются свайпы и кадр зоны (эмуляторы бывают разные).
read -r SCREEN_W SCREEN_H <<< "$(a shell wm size | tr -d '\r' | awk -F'[: x]+' '{w=$(NF-1); h=$NF} END{print w, h}')"
DPI="$(a shell wm density | tr -d '\r' | awk -F': ' '{v=$2} END{print v}')"
echo "устройство: $SERIAL, экран ${SCREEN_W}×${SCREEN_H}, $DPI dpi"

# ---------- чтение интерфейса ----------
dump_ui() {
  local i out
  for i in 1 2 3 4 5; do
    a exec-out uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    out="$(a exec-out cat /sdcard/ui.xml 2>/dev/null)"
    [ -n "$out" ] && { printf '%s' "$out"; return 0; }
    sleep 1
  done
  return 1
}

# find_center <режим> <значение>   режимы: text | exact | checkable
# Разбор через awk: python3 в PATH бывает сломанным шимом pyenv.
find_center() {
  dump_ui | tr '>' '\n' | awk -v mode="$1" -v val="$2" '
    function attr(s, name,   re, p, rest, q) {
      re = name "=\""
      p = index(s, re)
      if (p == 0) return ""
      rest = substr(s, p + length(re))
      q = index(rest, "\"")
      if (q == 0) return ""
      return substr(rest, 1, q - 1)
    }
    /<node/ {
      b = attr($0, "bounds"); if (b == "") next
      n = split(b, c, /[^0-9]+/)
      x1 = c[2] + 0; y1 = c[3] + 0; x2 = c[4] + 0; y2 = c[5] + 0
      if (x2 <= x1 || y2 <= y1) next
      cx = int((x1 + x2) / 2); cy = int((y1 + y2) / 2)
      if (mode == "checkable") {
        if (attr($0, "checkable") == "true") { print cx, cy; exit }
        next
      }
      if (mode == "exact") {
        if (attr($0, "text") == val) { print cx, cy; exit }
        next
      }
      if (index(attr($0, "text"), val) > 0 || index(attr($0, "content-desc"), val) > 0) {
        print cx, cy; exit
      }
    }'
}

# ---------- экранная клавиатура ----------
# Выключаем на время записи: она перекрывает нижние поля, съедает тапы и
# показывает свои подсказки поверх приложения. input text работает без неё.
IME_LIST=""
# Эмулятор иногда на секунду пропадает из adb — тогда клавиатура остаётся
# включённой и закрывает нижние поля. Поэтому повторяем и проверяем результат.
ime_enabled() { a shell ime list -s 2>/dev/null | tr -d '\r' | grep '/'; }
ime_off() {
  local i try
  a wait-for-device
  for try in 1 2 3; do
    [ -z "$IME_LIST" ] && IME_LIST="$(ime_enabled | tr '\n' ' ')"
    for i in $(ime_enabled); do a shell ime disable "$i" >/dev/null 2>&1; done
    [ -n "$IME_LIST" ] && [ -z "$(ime_enabled)" ] && { echo "· клавиатура отключена на время записи"; return 0; }
    sleep 2; a wait-for-device
  done
  echo "  ! клавиатуру отключить не удалось — отключите её на эмуляторе вручную и запустите снова"
  return 1
}
ime_on() {
  local i first=""
  for i in $IME_LIST; do
    a shell ime enable "$i" >/dev/null 2>&1
    [ -z "$first" ] && first="$i"
  done
  [ -n "$first" ] && a shell ime set "$first" >/dev/null 2>&1
  [ -n "$IME_LIST" ] && echo "· клавиатура возвращена"
}
trap ime_on EXIT

# find_bounds <текст или описание> — границы элемента: x1 y1 x2 y2
find_bounds() {
  dump_ui | tr '>' '\n' | awk -v val="$1" '
    function attr(s, name,   re, p, rest, q) {
      re = name "=\""
      p = index(s, re)
      if (p == 0) return ""
      rest = substr(s, p + length(re))
      q = index(rest, "\"")
      if (q == 0) return ""
      return substr(rest, 1, q - 1)
    }
    /<node/ {
      if (index(attr($0, "text"), val) == 0 && index(attr($0, "content-desc"), val) == 0) next
      b = attr($0, "bounds"); if (b == "") next
      split(b, c, /[^0-9]+/)
      print c[2], c[3], c[4], c[5]; exit
    }'
}

tap() { a shell input tap "$1" "$2"; }
scroll_down() {
  a shell input swipe $(( SCREEN_W / 2 )) $(( SCREEN_H * 70 / 100 )) $(( SCREEN_W / 2 )) $(( SCREEN_H * 40 / 100 )) 300
  sleep 1.2
}

# tap_sel <режим> <значение> <подпись> — ищет, при неудаче прокручивает
tap_sel() {
  local xy attempt
  for attempt in 1 2 3; do
    xy="$(find_center "$1" "$2")"
    if [ -n "$xy" ]; then echo "  → $3  ($xy)"; tap $xy; sleep 1; return 0; fi
    [ "$attempt" -lt 3 ] && scroll_down
  done
  echo "  ! не нашёл: $3"; return 1
}

# field <подпись поля> <что напечатать>
field() {
  tap_sel text "$1" "поле «$1»" || return 1
  sleep 0.5
  # поле может быть не пустым: адрес сервера подставляется сохранённый
  a shell input keyevent 123 >/dev/null 2>&1
  a shell input keyevent 67 67 67 67 67 67 67 67 67 67 67 67 67 67 67 67 \
                         67 67 67 67 67 67 67 67 67 67 67 67 67 67 67 67 \
                         67 67 67 67 67 67 67 67 >/dev/null 2>&1
  sleep 0.3
  a shell input text "$2"; sleep 0.7
}

# wait_for <текст> <секунд>
wait_for() {
  local deadline=$(( SECONDS + $2 ))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if [ -n "$(find_center text "$1")" ]; then echo "  ✓ дождались: $1"; return 0; fi
    sleep 2
  done
  echo "  ! не дождался за $2 с: $1"; return 1
}

# ---------- запись ----------
REC_PIDS=(); CHUNKS=1
start_rec() {   # start_rec <секунд>
  local total part left
  total="$1"; part=0; left="$total"
  ( while [ "$left" -gt 0 ]; do
      chunk=$(( left > 170 ? 170 : left ))
      a shell screenrecord --bit-rate 8000000 --time-limit "$chunk" "/sdcard/rec-$part.mp4"
      part=$((part+1)); left=$((left-chunk))
    done ) &
  REC_PIDS+=($!)
  CHUNKS=$(( (total + 169) / 170 ))
  sleep 2
}
stop_rec() {    # stop_rec <имя>
  sleep 1
  a shell pkill -INT screenrecord 2>/dev/null || a shell killall -INT screenrecord 2>/dev/null
  wait "${REC_PIDS[@]}" 2>/dev/null
  sleep 3
  local i f list
  list="$OUTDIR/$1-parts.txt"; : > "$list"
  for ((i=0;i<CHUNKS;i++)); do
    f="$OUTDIR/$1-part$i.mp4"
    if a shell "[ -f /sdcard/rec-$i.mp4 ]"; then
      a pull "/sdcard/rec-$i.mp4" "$f" >/dev/null && echo "file '$f'" >> "$list"
      a shell rm "/sdcard/rec-$i.mp4"
    fi
  done
  echo; echo "Готово. Куски записи:"; cat "$list"
}

# ---------- зоны ----------
# Кадр в режиме рисования без подписи, поэтому его границы считаются от
# кнопки «Развернуть кадр»: она в правом верхнем углу кадра с отступом 6 dp.
# Кадр по центру экрана, пропорции демо-кадра 640×480.
draw_zone() {
  local btn pad l t r frame_w frame_h p fx fy x y
  btn="$(find_bounds "Развернуть кадр")"
  [ -z "$btn" ] && { echo "  ! не нашёл кадр для рисования"; return 1; }
  pad=$(( 6 * DPI / 160 ))
  set -- $btn
  r=$(( $3 + pad )); t=$(( $2 - pad )); l=$(( SCREEN_W - r ))
  frame_w=$(( r - l )); frame_h=$(( frame_w * 480 / 640 ))
  echo "  · кадр: $l,$t – $r,$((t + frame_h))"
  for p in $ZONE_POINTS; do
    fx="${p%,*}"; fy="${p#*,}"
    x=$(( l + frame_w * fx / 1000 )); y=$(( t + frame_h * fy / 1000 ))
    echo "  → точка ($x $y)"; tap "$x" "$y"; sleep 1.2
  done
}

# alert_mode <«вошёл»|«вышел»> <always|night> — окно «Режим тревоги» уже открыто
alert_mode() {
  sleep 1.5
  tap_sel text "Человек $1" "«Человек $1 …»"; sleep 1
  if [ "$2" = night ]; then
    tap_sel text "Только в заданное время" "«Только в заданное время»"
  else
    tap_sel text "Всегда" "«Всегда»"
  fi
  sleep 2.5   # зритель успевает прочитать «с 22:00 / до 07:00»
  tap_sel exact "Сохранить" "«Сохранить» в окне"
  sleep 2
}

# Активация и переход к зонам — до записи: в ролике только критические зоны.
zones_open() {
  echo "· профиль → критические зоны (без записи)"
  wait_for "Профиль" 30 || return 1
  tap_sel text "Профиль" "вкладка «Профиль»"; sleep 2
  tap_sel text "Критические зоны" "«Критические зоны»" || return 1
  wait_for "Получить вид" 15
}

# В показе без сервера зона не проверяется, поэтому её переключатель
# неактивен — в ролик он не входит.
zones_scenario() {
  sleep 2
  tap_sel text "Получить вид" "«Получить вид»"
  wait_for "Обновить вид" 20; sleep 2

  echo "· новая зона"
  tap_sel text "Добавить зону" "«Добавить зону»"; sleep 2
  draw_zone || return 1
  sleep 1
  field "Название" "$ZONE_NAME"
  tap_sel text "Режим тревоги" "«Режим тревоги» новой зоны"
  alert_mode "вышел" night
  sleep 2
  tap_sel text "Сохранить зону" "«Сохранить зону»"
  wait_for "$ZONE_NAME" 15; sleep 3

  echo "· режим тревоги из списка"
  tap_sel text "Режим тревоги" "«Режим тревоги» в списке"
  alert_mode "вошёл" always
  sleep 4
}

# mark <эпизод> — секунда от начала записи, по ней ролик режется при монтаже
REC_T0=0
mark() { echo "$1 $(( SECONDS - REC_T0 ))" >> "$OUTDIR/pitch-marks.txt"; echo "  ⏱ $1"; }

pitch_scenario() {
  : > "$OUTDIR/pitch-marks.txt"
  wait_for "Профиль" 30 || { echo "  активация не прошла"; return 1; }
  REC_T0=$SECONDS
  start_rec 170
  sleep 2; mark home; sleep 3
  tap_sel text "Профиль" "вкладка «Профиль»"; sleep 1.5
  tap_sel text "Критические зоны" "«Критические зоны»"; sleep 1.5
  tap_sel text "Получить вид" "«Получить вид»"; wait_for "Обновить вид" 20; sleep 1
  tap_sel text "Добавить зону" "«Добавить зону»"; sleep 1.5
  mark draw
  draw_zone || return 1
  field "Название" "$ZONE_NAME"
  tap_sel text "Режим тревоги" "«Режим тревоги»"; sleep 1
  mark dialog
  alert_mode "вышел" night
  tap_sel text "Сохранить зону" "«Сохранить зону»"; wait_for "$ZONE_NAME" 15
  mark list; sleep 3
  a shell input keyevent 4; sleep 1.5
  tap_sel text "Главная" "вкладка «Главная»"; sleep 1
  wait_for "Открыть событие" 90 || return 1
  mark alert; sleep 3
  tap_sel text "Открыть событие" "«Открыть событие»"; sleep 3
  mark event; sleep 2
  tap_sel text "Смотреть видео" "«Смотреть видео»"; sleep 1
  mark video; sleep 8
}

shot() {   # shot <имя>
  mkdir -p "$OUTDIR/shots"
  a exec-out screencap -p > "$OUTDIR/shots/$1.png" && echo "  📷 $1"
}

shots_scenario() {
  a shell pm clear "$PKG" >/dev/null; sleep 1
  a shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 5
  ime_off || return 1
  shot 01-activation
  activation yes || return 1
  wait_for "Профиль" 30 || { echo "  активация не прошла — дальше не снимаю"; return 1; }
  sleep 3
  shot 02-home
  wait_for "Падение" 60; sleep 3
  shot 03-home-alert
  if tap_sel text "Открыть событие" "«Открыть событие»"; then
    sleep 6; shot 04-event
    a shell input keyevent 4; sleep 3
  fi
  tap_sel text "История" "вкладка «История»"; sleep 4
  shot 05-history
  tap_sel text "Профиль" "вкладка «Профиль»"; sleep 3
  shot 06-profile
  tap_sel text "Критические зоны" "«Критические зоны»"; sleep 3
  shot 07-zones-empty
  tap_sel text "Получить вид" "«Получить вид»"; wait_for "Обновить вид" 20; sleep 2
  shot 08-zones-frame
  tap_sel text "Добавить зону" "«Добавить зону»"; sleep 2
  draw_zone; sleep 1
  shot 09-zone-draw
  tap_sel text "Режим тревоги" "«Режим тревоги»"; sleep 2
  tap_sel text "Только в заданное время" "«Только в заданное время»"; sleep 2
  shot 10-alert-dialog
  a shell input keyevent 4; sleep 1
  field "Название" "$ZONE_NAME"
  tap_sel text "Сохранить зону" "«Сохранить зону»"; wait_for "$ZONE_NAME" 15; sleep 2
  shot 11-zones-list
  tap_sel text "Проверить" "«Проверить»"; sleep 4
  shot 12-zone-check
  # Экран PIN — после выхода из аккаунта. «Назад» закрывает проверку, затем зоны.
  a shell input keyevent 4; sleep 2
  a shell input keyevent 4; sleep 2
  tap_sel text "Профиль" "вкладка «Профиль»"; sleep 2
  tap_sel text "Выйти из аккаунта" "«Выйти из аккаунта»"; sleep 1
  tap_sel exact "Продолжить" "подтверждение выхода"; sleep 4
  shot 13-pin
  echo; echo "Скриншоты: $OUTDIR/shots/"
}

# ---------- сценарии ----------
activation() {  # activation <демо: yes|no>
  echo "· активация (демо=$1)"
  if ! field "Адрес сервера" "$SERVER_URL"; then
    echo "  Экрана подключения нет — прерываю, чтобы не снимать впустую."
    return 1
  fi
  field "Код установки"        "$INSTALL_CODE"
  field "Как к вам обращаться" "$USER_NAME"
  field "PIN из шести цифр"    "$PIN"
  field "Повторите PIN"        "$PIN"
  if [ "$1" = yes ]; then
    tap_sel checkable "" "переключатель «Показ без сервера»"
  fi
  tap_sel text "Подключиться" "кнопка «Подключиться»"
  sleep 5
}

open_event_and_clip() {
  if ! tap_sel text "Открыть событие" "«Открыть событие» в карточке тревоги"; then
    tap_sel text "История" "вкладка «История»"; sleep 3
    tap_sel text "Падение" "верхнее событие"
  fi
  sleep 6
  tap_sel text "Смотреть видео" "«Смотреть видео»"
  sleep 16
  a shell input keyevent 4; sleep 3
  tap_sel text "К истории" "«К истории»" || a shell input keyevent 4
  sleep 5
}

finish_on_home_and_profile() {
  echo "· главная и профиль"
  tap_sel text "Главная" "вкладка «Главная»"
  sleep 3
  tap_sel text "Закрыть" "закрыть карточку тревоги"
  sleep 8
  tap_sel text "Профиль" "вкладка «Профиль»"
  sleep 10
}

case "${1:-}" in
  dump)
    dump_ui > "$OUTDIR/ui-dump.xml" && echo "дерево интерфейса: docs/video/ui-dump.xml"
    ;;

  demo)
    a shell pm clear "$PKG" >/dev/null; sleep 1
    a shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 5
    ime_off || exit 1
    start_rec 170
    activation yes || { stop_rec demo; exit 1; }
    echo "· главная: ждём падения"
    wait_for "Падение" 60
    sleep 4
    open_event_and_clip
    finish_on_home_and_profile
    stop_rec demo
    ;;

  live)
    [ -f "$FALL_CLIP" ] || { echo "нет файла $FALL_CLIP"; exit 1; }
    [ -d "$INBOX" ]     || { echo "нет инбокса $INBOX"; exit 1; }
    a shell pm clear "$PKG" >/dev/null; sleep 1
    a shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 5
    ime_off || exit 1
    start_rec 340
    activation no || { stop_rec live; exit 1; }
    # Ролик подкладываем ТОЛЬКО после первого опроса событий: иначе событие
    # родится раньше, попадёт под водяной знак «просмотрено» и тревоги не будет.
    echo "· ждём первый опрос событий, прежде чем подкладывать ролик"
    sleep 14
    # Имя обязательно новое: агент помнит пути обработанных файлов до
    # перезапуска и файл с прежним именем молча проигнорирует.
    NAME="live-$(date +%H%M%S).mp4"
    echo "· подкладываю ролик агенту: $NAME"
    cp "$FALL_CLIP" "$INBOX/.$NAME.tmp" && mv "$INBOX/.$NAME.tmp" "$INBOX/$NAME"
    echo "· ждём настоящее событие: агент проигрывает ролик, сервер распознаёт,"
    echo "  приложение замечает при следующем опросе (раз в 30 с)"
    if ! wait_for "Падение" 240; then
      echo "  событие не пришло — смотри лог агента и docker compose logs worker"
      stop_rec live; exit 1
    fi
    sleep 4
    open_event_and_clip
    finish_on_home_and_profile
    stop_rec live
    ;;

  zones)
    a shell pm clear "$PKG" >/dev/null; sleep 1
    a shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 5
    ime_off || exit 1
    activation yes || exit 1
    zones_open || { echo "  экран зон не открылся — запись не начата"; exit 1; }
    start_rec 120
    zones_scenario || { stop_rec zones; exit 1; }
    stop_rec zones
    ;;

  shots)
    shots_scenario
    ;;

  pitch)
    a shell pm clear "$PKG" >/dev/null; sleep 1
    a shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 5
    ime_off || exit 1
    activation yes || exit 1
    pitch_scenario || { stop_rec pitch; exit 1; }
    stop_rec pitch
    echo "Метки эпизодов:"; cat "$OUTDIR/pitch-marks.txt"
    ;;

  *) sed -n '2,17p' "$0"; exit 1 ;;
esac
