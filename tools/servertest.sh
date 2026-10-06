#!/bin/bash
# Серверная самопроверка без клиента: запускает выделенный сервер с -Dsuchki.audit=true,
# выполняет /suchki_test [фильтр] в консоли и печатает отчёт. Пример: bash tools/servertest.sh "Магия: Огонь"
# Сборки и запуски идут под общим замком (параллельно работают несколько человек/агентов).
cd "$(dirname "$0")/.." || exit 1
FILTER="$1"
LOG=$(mktemp /tmp/claude-0/suchki-servertest.XXXXXX.log)
FIFO=$(mktemp -u /tmp/claude-0/suchki-servertest.XXXXXX.fifo)
mkfifo "$FIFO"
mkdir -p run
[ -f run/eula.txt ] || echo "eula=true" > run/eula.txt
grep -q "level-type" run/server.properties 2>/dev/null || printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nview-distance=6\nsimulation-distance=6\n' > run/server.properties
(
	exec 3<>"$FIFO"
	flock /tmp/claude-0/suchki-gradle.lock bash ./gradlew runServer --no-daemon < "$FIFO" > "$LOG" 2>&1 &
	G=$!
	for i in $(seq 1 600); do grep -q 'Done (' "$LOG" && break; grep -q 'BUILD FAILED' "$LOG" && break; sleep 1; done
	if grep -q 'Done (' "$LOG"; then
		echo "suchki_test $FILTER" >&3
		for i in $(seq 1 900); do grep -q 'Итого:' "$LOG" && break; sleep 1; done
		sleep 1
		echo stop >&3
	fi
	wait $G
)
rm -f "$FIFO"
if grep -q 'BUILD FAILED' "$LOG" && ! grep -q 'Итого:' "$LOG"; then
	grep -E "error:|BUILD FAILED|What went wrong" -A3 "$LOG" | head -60
	echo "Лог: $LOG"
	exit 2
fi
grep -E "\(Minecraft\) (OK|FAIL|SKIP|Итого)" "$LOG" | sed -E "s/^.*\(Minecraft\) //"
grep -q "FAIL " "$LOG" && { echo "Лог: $LOG"; exit 1; }
echo "Лог: $LOG"
exit 0
