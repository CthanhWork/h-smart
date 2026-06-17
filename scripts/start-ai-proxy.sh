#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <laptop-tailscale-ip> [listen-port]" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export AI_PROXY_TARGET_HOST="$1"
export AI_PROXY_TARGET_PORT="8002"
export AI_PROXY_HOST="0.0.0.0"
export AI_PROXY_PORT="${2:-18002}"

exec python3 "$SCRIPT_DIR/ai_tailscale_proxy.py"
