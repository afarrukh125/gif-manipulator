#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

JAR="target/giftools.jar"
PORT="${PORT:-8091}"

if [[ "${1:-}" =~ ^[0-9]+$ ]]; then
    PORT="$1"
    shift
fi

URL="http://localhost:$PORT"

probe() {
    command -v curl >/dev/null 2>&1 || return 1
    curl -sS --max-time 5 "$1" 2>/dev/null
}

open_url() {
    if command -v xdg-open >/dev/null 2>&1; then xdg-open "$1" >/dev/null 2>&1 &
    elif command -v open >/dev/null 2>&1; then open "$1" >/dev/null 2>&1 &
    elif command -v cmd.exe >/dev/null 2>&1; then cmd.exe //c start "" "$1" >/dev/null 2>&1 &
    fi
}

if page="$(probe "$URL/")"; then
    if [[ "$page" != *"GIF Tools"* ]]; then
        echo "Port $PORT is taken by something that is not the GIF editor. Try another: ./run.sh 9000" >&2
        exit 1
    fi
    echo "GIF editor already running at $URL"
    if [[ " $* " != *" --no-open "* ]]; then
        open_url "$URL"
    fi
    exit 0
fi

if [ ! -f "$JAR" ]; then
    echo "Building $JAR ..."
    mvn -q package
fi

echo "GIF editor: $URL"
exec java -jar "$JAR" serve --port "$PORT" "$@"
