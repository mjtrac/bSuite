#!/bin/bash
# reset_scan.sh — Reset bCounter database and restore image filenames
# Run from any directory; paths are relative to bSuite root.
#
# Usage:
#   ./reset_scan.sh                          # use default images dir
#   ./reset_scan.sh --images /custom/path    # use custom images dir

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BSUITE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BCOUNTER_DIR="$BSUITE_DIR/bCounter"
IMAGES_DIR="$BSUITE_DIR/test-harness/images"  # default

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --images) IMAGES_DIR="$2"; shift 2 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

echo "── Reset bCounter scan state ─────────────────────────────────"
echo "   bSuite:  $BSUITE_DIR"
echo "   DB dir:  $BCOUNTER_DIR"
echo "   Images:  $IMAGES_DIR"
echo ""

# 1. Remove SQLite database files
echo "Step 1 — Removing counter database..."
removed=0
for f in "$BCOUNTER_DIR/counter_results.db" \
          "$BCOUNTER_DIR/counter_results.db-shm" \
          "$BCOUNTER_DIR/counter_results.db-wal"; do
    if [ -f "$f" ]; then
        rm -f "$f"
        echo "  Removed: $(basename $f)"
        removed=$((removed + 1))
    fi
done
[ $removed -eq 0 ] && echo "  (no database files found)"

# 2. Remove adjusted YAML files from image tree
echo ""
echo "Step 2 — Removing adjusted YAML files..."
if [ -d "$IMAGES_DIR" ]; then
    adj_count=$(find "$IMAGES_DIR" -name "*_adjusted.yaml" | wc -l | tr -d ' ')
    if [ "$adj_count" -gt 0 ]; then
        find "$IMAGES_DIR" -name "*_adjusted.yaml" -delete
        echo "  Removed $adj_count adjusted YAML file(s)"
    else
        echo "  (no adjusted YAML files found)"
    fi
else
    echo "  (images directory not found: $IMAGES_DIR)"
fi

# 3. Restore .counted filenames to .png
echo ""
echo "Step 3 — Restoring .counted files to .png..."
if [ -d "$IMAGES_DIR" ]; then
    count=$(find "$IMAGES_DIR" -name "*.counted" | wc -l | tr -d ' ')
    if [ "$count" -gt 0 ]; then
        find "$IMAGES_DIR" -name "*.counted" \
            -exec sh -c 'mv "$1" "${1%.counted}"' _ {} \;
        echo "  Restored $count file(s)"
    else
        echo "  (no .counted files found)"
    fi
else
    echo "  ⚠  Images directory not found: $IMAGES_DIR"
fi

echo ""
# 4. Restore .review files to .png
review_count=$(find "$IMAGES_DIR" -name "*.review" 2>/dev/null | wc -l | tr -d ' ')
if [ "$review_count" -gt 0 ]; then
    echo ""
    echo "Step 4 — Restoring $review_count .review file(s) to .png..."
    find "$IMAGES_DIR" -name "*.review" -exec sh -c 'mv "$1" "${1%.review}"' _ {} \;
    echo "  Restored $review_count file(s)"
fi

# 5. Restart bCounter
echo ""
echo "Step 5 — Restarting bCounter..."
BCOUNTER_PORT=8081
pid=$(lsof -ti tcp:$BCOUNTER_PORT 2>/dev/null || true)
if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null || true
    echo "  Stopped bCounter (pid $pid)"
    sleep 3
fi
nohup bash -c "cd $BCOUNTER_DIR && ./mvnw -q spring-boot:run > $BCOUNTER_DIR/bCounter.log 2>&1" &
echo "  bCounter starting (log: bCounter/bCounter.log)"
printf "  Waiting for bCounter"
for i in $(seq 1 30); do
    sleep 2
    if curl -s -o /dev/null http://localhost:$BCOUNTER_PORT/login 2>/dev/null; then
        echo " ready"
        break
    fi
    printf "."
done
echo ""
echo "✓ Reset complete — ready to run ./run_all.sh"
