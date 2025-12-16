#!/bin/bash
# Long-running health check simulation with periodic heap dumps
# This simulates the Kubernetes liveness/readiness probes that hit GET /
#
# Usage: ./healthcheck_test.sh [duration_hours] [heap_interval_minutes]
#
# Defaults:
#   duration_hours=4 (run for 4 hours)
#   heap_interval_minutes=30 (heap dump every 30 minutes)
#
# Health checks run every 10 seconds (matching typical k8s config)

set -e

DURATION_HOURS="${1:-4}"
HEAP_INTERVAL_MINUTES="${2:-30}"
CONTAINER_NAME="loadtest-jeed-1"
HEALTH_CHECK_INTERVAL=10  # seconds, typical k8s probe interval
HEAP_INTERVAL_SECONDS=$((HEAP_INTERVAL_MINUTES * 60))

TOTAL_SECONDS=$((DURATION_HOURS * 3600))
EXPECTED_HEALTH_CHECKS=$((TOTAL_SECONDS / HEALTH_CHECK_INTERVAL))
EXPECTED_HEAP_DUMPS=$((TOTAL_SECONDS / HEAP_INTERVAL_SECONDS))

echo "========================================"
echo "Jeed Long-Running Health Check Test"
echo "========================================"
echo "Duration: ${DURATION_HOURS} hours ($TOTAL_SECONDS seconds)"
echo "Health check interval: ${HEALTH_CHECK_INTERVAL}s"
echo "Heap dump interval: ${HEAP_INTERVAL_MINUTES} minutes"
echo "Expected health checks: ~$EXPECTED_HEALTH_CHECKS"
echo "Expected heap dumps: ~$EXPECTED_HEAP_DUMPS"
echo ""
echo "Press Ctrl+C to stop early"
echo "========================================"
echo ""

# Ensure heapdumps directory exists
mkdir -p heapdumps

# Clear old log file
LOG_FILE="heapdumps/healthcheck_test.log"
> "$LOG_FILE"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

take_heap_dump() {
    local dump_num=$1
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local dump_file="/heapdumps/healthcheck_${dump_num}_${timestamp}.hprof"

    log "HEAP DUMP #$dump_num starting..."

    # Find Java PID
    local java_pid=$(docker exec "$CONTAINER_NAME" pgrep -x java 2>/dev/null)
    if [ -z "$java_pid" ]; then
        log "ERROR: Could not find Java process"
        return 1
    fi

    # Get heap info before dump
    log "Heap info before dump:"
    docker exec "$CONTAINER_NAME" jcmd "$java_pid" GC.heap_info 2>&1 | tee -a "$LOG_FILE"

    # Take heap dump
    docker exec "$CONTAINER_NAME" jcmd "$java_pid" GC.heap_dump "$dump_file" 2>&1 | tee -a "$LOG_FILE"

    log "HEAP DUMP #$dump_num saved to ./heapdumps/healthcheck_${dump_num}_${timestamp}.hprof"
}

do_health_check() {
    local check_num=$1
    local response
    local http_code

    # Make the health check request and capture both response and status
    response=$(curl -s -w "\n%{http_code}" http://localhost:8888/ 2>/dev/null)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n -1)

    if [ "$http_code" = "200" ]; then
        # Only log every 60th check (every 10 minutes) to reduce noise
        if [ $((check_num % 60)) -eq 0 ]; then
            log "Health check #$check_num: OK (every 10min log)"
        fi
        return 0
    else
        log "Health check #$check_num: FAILED (HTTP $http_code) - $body"
        return 1
    fi
}

# Start time tracking
START_TIME=$(date +%s)
health_check_count=0
heap_dump_count=0
last_heap_dump_time=$START_TIME
failed_checks=0

log "Test started"

# Take initial heap dump
heap_dump_count=$((heap_dump_count + 1))
take_heap_dump $heap_dump_count

while true; do
    current_time=$(date +%s)
    elapsed=$((current_time - START_TIME))

    # Check if we've exceeded duration
    if [ $elapsed -ge $TOTAL_SECONDS ]; then
        log "Duration reached. Stopping test."
        break
    fi

    # Do health check
    health_check_count=$((health_check_count + 1))
    if ! do_health_check $health_check_count; then
        failed_checks=$((failed_checks + 1))
    fi

    # Check if it's time for a heap dump
    time_since_last_dump=$((current_time - last_heap_dump_time))
    if [ $time_since_last_dump -ge $HEAP_INTERVAL_SECONDS ]; then
        heap_dump_count=$((heap_dump_count + 1))
        take_heap_dump $heap_dump_count
        last_heap_dump_time=$current_time
    fi

    # Wait for next health check
    sleep $HEALTH_CHECK_INTERVAL
done

# Take final heap dump
heap_dump_count=$((heap_dump_count + 1))
take_heap_dump $heap_dump_count

echo ""
log "========================================"
log "Test Complete"
log "========================================"
log "Total health checks: $health_check_count"
log "Failed health checks: $failed_checks"
log "Total heap dumps: $heap_dump_count"
log "Heap dumps saved in ./heapdumps/"
log "Log file: $LOG_FILE"
