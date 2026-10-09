#!/bin/bash
# Серверная самопроверка без клиента: собирает мод, запускает выделенный сервер с -Dsuchki.audit=true,
# выполняет /suchki_test [фильтр] в консоли и печатает отчёт. Пример: bash tools/servertest.sh "Магия: Огонь"
#
# Под общим замком идёт только сборка и снимок классов; сам сервер работает в своей временной папке и на своём
# порту, так что несколько проверок могут идти одновременно (параллельно работают несколько человек/агентов).
cd "$(dirname "$0")/.." || exit 1
ROOT=$(pwd)
FILTER="$1"
LOCK=/tmp/claude-0/suchki-gradle.lock
W=$(mktemp -d /tmp/claude-0/suchki-st.XXXXXX)
LOG="$W/server.log"

# 1. Сборка и снимок классов и ресурсов (чтобы чужая пересборка не подменила их посреди проверки).
if ! flock "$LOCK" bash -c "bash ./gradlew compileJava processResources --no-daemon -q > '$W/build.log' 2>&1 \
		&& cp -r build/classes/java/main '$W/classes' && cp -r build/resources/main '$W/resources'"; then
	grep -E "error:|What went wrong" -A3 "$W/build.log" | grep -v JAVA_TOOL | head -60
	echo "Сборка не удалась, лог: $W/build.log"
	exit 2
fi
ARGS="$ROOT/build/loom-cache/argFiles/runServer"
CFG="$ROOT/.gradle/loom-cache/launch.cfg"
if [ ! -f "$ARGS" ] || [ ! -f "$CFG" ]; then
	flock "$LOCK" bash ./gradlew configureLaunch --no-daemon -q > /dev/null 2>&1
fi
sed -e "s#$ROOT/build/classes/java/main#$W/classes#g" -e "s#$ROOT/build/resources/main#$W/resources#g" "$ARGS" > "$W/args"
sed -e "s#$ROOT/build/classes/java/main#$W/classes#g" -e "s#$ROOT/build/resources/main#$W/resources#g" "$CFG" > "$W/launch.cfg"

# 2. Своя папка мира и свой порт.
mkdir -p "$W/run"
echo "eula=true" > "$W/run/eula.txt"
PORT=$((26000 + RANDOM % 3000))
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nserver-port=%s\nenable-query=false\nenable-rcon=false\nsync-chunk-writes=false\n' "$PORT" > "$W/run/server.properties"

# 3. Запуск, команда, остановка.
FIFO="$W/in"
mkfifo "$FIFO"
(
	cd "$W/run" || exit 1
	exec 3<>"$FIFO"
	java -Xmx1600m -Dfabric.dli.config="$W/launch.cfg" -Dfabric.dli.env=server \
		-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer -Dsuchki.audit=true @"$W/args" \
		-Dfile.encoding=UTF-8 -Duser.country=US -Duser.language=en net.fabricmc.devlaunchinjector.Main nogui \
		< "$FIFO" > "$LOG" 2>&1 &
	J=$!
	for i in $(seq 1 300); do
		grep -q 'Done (' "$LOG" && break
		kill -0 $J 2>/dev/null || break
		sleep 1
	done
	if grep -q 'Done (' "$LOG"; then
		echo "suchki_test $FILTER" >&3
		for i in $(seq 1 1200); do
			grep -q 'Итого:' "$LOG" && break
			kill -0 $J 2>/dev/null || break
			sleep 1
		done
		sleep 1
		echo stop >&3
	fi
	for i in $(seq 1 60); do kill -0 $J 2>/dev/null || break; sleep 1; done
	kill $J 2>/dev/null
	wait $J 2>/dev/null
)
grep -E "\(Minecraft\) (OK|FAIL|SKIP|Итого)" "$LOG" | sed -E "s/^.*\(Minecraft\) //"
if ! grep -q 'Итого:' "$LOG"; then
	echo "Проверка не дошла до конца. Ошибки из лога:"
	grep -E "Exception|Error|ERROR" "$LOG" | grep -v "Yggdrasil\|yggdrasil\|key layers\|BEGIN_OBJECT\|allowlist\|MinecraftClientException\|JsonSyntax" | head -30
	echo "Лог: $LOG"
	exit 3
fi
# Временную папку (снимок классов и мир) чистим, лог оставляем рядом.
cp "$LOG" "$W.log"
rm -rf "$W"
echo "Лог: $W.log"
grep -q "FAIL " "$W.log" && exit 1
exit 0
