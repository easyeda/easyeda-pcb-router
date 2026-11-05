#!/bin/sh
# Run the packaged facade in the foreground; the modern worker uses its own JVM.
set -eu
ROUTER_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROUTER_DIR"
if [ -n "${JAVA_HOME:-}" ]; then
    ROUTER_JAVA="$JAVA_HOME/bin/java"
elif [ "$(uname -s)" = Darwin ] && [ -f jre/mac64/bin/java ]; then
    chmod 755 jre/mac64/bin/java
    ROUTER_JAVA="$ROUTER_DIR/jre/mac64/bin/java"
elif [ -f jre/lin64/bin/java ]; then
    chmod 755 jre/lin64/bin/java
    ROUTER_JAVA="$ROUTER_DIR/jre/lin64/bin/java"
else
    ROUTER_JAVA=$(command -v java || true)
fi
if [ -z "$ROUTER_JAVA" ] || [ ! -x "$ROUTER_JAVA" ]; then
    echo "Java is required. Set JAVA_HOME; modern routing also requires Java 25 via FREEROUTING_JAVA_HOME." >&2
    exit 1
fi
exec "$ROUTER_JAVA" -XX:+UseG1GC -Dcom.easyeda.env=local -jar bin/bootstrap.jar
