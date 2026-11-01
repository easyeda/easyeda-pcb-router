#!/bin/sh
# Run in the foreground; press Ctrl+C to stop the local router.
set -eu

usage() {
    echo "Usage: $0 [modern|legacy]"
    echo "       $0 --help"
    echo
    echo "Start the EasyEDA router. The default engine is modern."
    echo "Use 'legacy' to select the historical in-process engine."
}

ENGINE="${EASYEDA_ROUTER_ENGINE:-modern}"
case "$#" in
    0) ;;
    1)
        case "$1" in
            modern|legacy) ENGINE="$1" ;;
            -h|--help|help) usage; exit 0 ;;
            *) echo "Unknown engine or option: $1" >&2; usage >&2; exit 2 ;;
        esac
        ;;
    *) echo "Expected at most one engine argument." >&2; usage >&2; exit 2 ;;
esac

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -n "${JAVA_HOME:-}" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
else
    JAVA_BIN=$(command -v java || true)
fi

if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
    echo "Java is required. Install Java or set JAVA_HOME to your Java installation." >&2
    exit 1
fi

# Support both the default Ant output and the existing isolated client build.
ROUTER_DIR=""
if [ -n "${EASYEDA_ROUTER_DIR:-}" ]; then
    ROUTER_DIR="$EASYEDA_ROUTER_DIR"
fi
for candidate in \
    "$PROJECT_DIR/.build/modern-client/EasyEDA Router v0.8.11" \
    "$PROJECT_DIR/.build/modern-diagnostics/EasyEDA Router v0.8.11" \
    "$PROJECT_DIR/.build/EasyEDA Router v0.8.11" \
    "$PROJECT_DIR/.build/client/EasyEDA Router v0.8.11"
do
    if [ -n "$ROUTER_DIR" ]; then
        break
    fi
    if [ -f "$candidate/bin/bootstrap.jar" ]; then
        ROUTER_DIR="$candidate"
        break
    fi
done

if [ -z "$ROUTER_DIR" ] || [ ! -f "$ROUTER_DIR/bin/bootstrap.jar" ]; then
    echo "Router build not found. Run 'ant build-client' in $PROJECT_DIR first." >&2
    exit 1
fi

cd "$ROUTER_DIR"
export EASYEDA_ROUTER_ENGINE="$ENGINE"
echo "Starting EasyEDA Router with engine=$EASYEDA_ROUTER_ENGINE and config/local/main.json (default: 127.0.0.1:3579)."
echo "Keep this terminal open. Press Ctrl+C to stop."
exec "$JAVA_BIN" -XX:+UseG1GC -Dcom.easyeda.env=local -jar bin/bootstrap.jar
