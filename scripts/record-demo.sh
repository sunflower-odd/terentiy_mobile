#!/bin/bash
# Запись видеоролика с экрана эмулятора: приложение проходит сценарий само.
#
#   ./scripts/record-demo.sh dump   — выгрузить дерево интерфейса (для настройки)
#   ./scripts/record-demo.sh demo   — «Показ без сервера», сервер и агент не нужны
#   ./scripts/record-demo.sh live   — живой прогон: сервер, агент, настоящее падение
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
echo "устройство: $SERIAL"

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

# find_center <режим> <значение>   режимы: text | checkable
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
      if (index(attr($0, "text"), val) > 0 || index(attr($0, "content-desc"), val) > 0) {
        print cx, cy; exit
      }
    }'
}

# ---------- экранная клавиатура ----------
# Выключаем на время записи: она перекрывает нижние поля, съедает тапы и
# показывает свои подсказки поверх приложения. input text работает без неё.
IME_LIST=""
ime_off() {
  IME_LIST="$(a shell ime list -s 2>/dev/null | tr -d '\r' | tr '\n' ' ')"
  local i
  for i in $IME_LIST; do a shell ime disable "$i" >/dev/null 2>&1; done
  echo "· клавиатура отключена на время записи"
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

tap() { a shell input tap "$1" "$2"; }
scroll_down() { a shell input swipe 540 1500 540 1050 250; sleep 1.2; }

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
    ime_off
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
    ime_off
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

  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
