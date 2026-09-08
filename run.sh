#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

JAR="target/giftools.jar"
PORT="${PORT:-8080}"

if [ ! -f "$JAR" ]; then
    echo "Building $JAR ..."
    mvn -q package
fi

echo "GIF editor: http://localhost:$PORT"
exec java -jar "$JAR" serve --port "$PORT" "$@"
