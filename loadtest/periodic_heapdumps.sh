#!/bin/bash
# Script to take periodic heap dumps from the running Jeed container
# Usage: ./periodic_heapdumps.sh [interval_seconds] [count]
#
# Defaults: interval=300 (5 minutes), count=12 (1 hour of dumps)
# The dumps will be saved to ./heapdumps/

set -e

INTERVAL="${1:-300}"
COUNT="${2:-12}"
CONTAINER_NAME="loadtest-jeed-1"

echo "Starting periodic heap dumps"
echo "  Interval: ${INTERVAL}s"
echo "  Count: $COUNT"
echo "  Container: $CONTAINER_NAME"
echo ""
echo "Dumps will be saved to ./heapdumps/"
echo "Press Ctrl+C to stop early"
echo ""

mkdir -p heapdumps

for i in $(seq 1 $COUNT); do
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    DUMP_FILE="/heapdumps/periodic_${i}_${TIMESTAMP}.hprof"

    echo "[$i/$COUNT] Taking heap dump at $(date)"

    # Find the Java process PID (the actual java binary)
    JAVA_PID=$(docker exec "$CONTAINER_NAME" pgrep -x java)

    # Get memory stats before dump
    docker exec "$CONTAINER_NAME" jcmd "$JAVA_PID" VM.native_memory summary 2>/dev/null || true

    # Take the heap dump
    docker exec "$CONTAINER_NAME" jcmd "$JAVA_PID" GC.heap_dump "$DUMP_FILE"

    # Get heap stats
    docker exec "$CONTAINER_NAME" jcmd "$JAVA_PID" GC.heap_info

    echo "  Saved: ./heapdumps/periodic_${i}_${TIMESTAMP}.hprof"
    echo ""

    if [ $i -lt $COUNT ]; then
        echo "  Waiting ${INTERVAL}s for next dump..."
        sleep $INTERVAL
    fi
done

echo ""
echo "All $COUNT heap dumps completed!"
echo "Dumps saved in ./heapdumps/"
