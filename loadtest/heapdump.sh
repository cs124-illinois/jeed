#!/bin/bash
# Script to take a heap dump from the running Jeed container
# Usage: ./heapdump.sh [dump_name]
#
# The dump will be saved to ./heapdumps/<name>_<timestamp>.hprof

set -e

DUMP_NAME="${1:-manual}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
CONTAINER_NAME="loadtest-jeed-1"
DUMP_FILE="/heapdumps/${DUMP_NAME}_${TIMESTAMP}.hprof"

echo "Taking heap dump from container: $CONTAINER_NAME"
echo "Output file: $DUMP_FILE"

# Find the Java process PID inside the container (the actual java binary, not shell wrapper)
JAVA_PID=$(docker exec "$CONTAINER_NAME" pgrep -x java)
echo "Java PID: $JAVA_PID"

docker exec "$CONTAINER_NAME" jcmd "$JAVA_PID" GC.heap_dump "$DUMP_FILE"

echo ""
echo "Heap dump saved to: ./heapdumps/${DUMP_NAME}_${TIMESTAMP}.hprof"
echo ""
echo "To analyze with VisualVM or Eclipse MAT:"
echo "  - VisualVM: File -> Load -> select the .hprof file"
echo "  - Eclipse MAT: File -> Open Heap Dump -> select the .hprof file"
