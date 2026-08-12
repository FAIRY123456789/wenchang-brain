#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:18091/mcp}"
TMP_DIR="$(mktemp -d /tmp/wenchang-mcp-smoke.XXXXXX)"
trap 'rm -rf "${TMP_DIR}"' EXIT

post() {
  local body="$1" output="$2" headers="$3" session="${4:-}"
  local args=(-sS -D "${headers}" -o "${output}" -X POST "${BASE_URL}"
    -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream')
  [[ -z "${session}" ]] || args+=(-H "Mcp-Session-Id: ${session}")
  curl "${args[@]}" --data-binary "${body}"
}

event_json() {
  python3 - "$1" <<'PY'
import json, re, sys
text = open(sys.argv[1], encoding='utf-8').read()
match = re.search(r'^data:\s*(.*)$', text, re.M)
print(match.group(1) if match else text)
PY
}

post '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"production-smoke","version":"1.0"}}}' \
  "${TMP_DIR}/init.body" "${TMP_DIR}/init.headers"
SESSION="$(python3 - "${TMP_DIR}/init.headers" <<'PY'
import sys
for line in open(sys.argv[1], encoding='latin1'):
    if line.lower().startswith('mcp-session-id:'):
        print(line.split(':', 1)[1].strip())
        break
PY
)"
[[ -n "${SESSION}" ]]
printf 'INITIALIZE_HTTP=%s\n' "$(head -1 "${TMP_DIR}/init.headers" | tr -d '\r' | cut -d' ' -f2)"
printf 'SESSION_CREATED=true\n'
event_json "${TMP_DIR}/init.body" | python3 -c 'import json,sys; x=json.load(sys.stdin); print("PROTOCOL="+x["result"]["protocolVersion"])'

post '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  "${TMP_DIR}/notify.body" "${TMP_DIR}/notify.headers" "${SESSION}"
post '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' \
  "${TMP_DIR}/list.body" "${TMP_DIR}/list.headers" "${SESSION}"
printf 'TOOLS_LIST_HTTP=%s\n' "$(head -1 "${TMP_DIR}/list.headers" | tr -d '\r' | cut -d' ' -f2)"
event_json "${TMP_DIR}/list.body" | python3 -c 'import json,sys; x=json.load(sys.stdin); t=x["result"]["tools"]; print("TOOL_COUNT="+str(len(t))); print("TOOLS="+",".join(sorted(v["name"] for v in t)))'

post '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"searchTownshipProfile","arguments":{"town":"龙楼镇"}}}' \
  "${TMP_DIR}/call.body" "${TMP_DIR}/call.headers" "${SESSION}"
printf 'TOOLS_CALL_HTTP=%s\n' "$(head -1 "${TMP_DIR}/call.headers" | tr -d '\r' | cut -d' ' -f2)"
event_json "${TMP_DIR}/call.body" | python3 -c 'import json,sys; x=json.load(sys.stdin); r=x["result"]; print("TOOLS_CALL_ERROR="+str(bool(r.get("isError"))).lower()); print("TOOLS_CALL_HAS_LONGLOU="+str("龙楼" in json.dumps(r,ensure_ascii=False)).lower())'
