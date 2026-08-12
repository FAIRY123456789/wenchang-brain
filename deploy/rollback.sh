#!/usr/bin/env bash
set -euo pipefail
[[ "${EUID}" -eq 0 ]] || { echo "rollback.sh must run as root" >&2; exit 1; }
ROOT=/opt/wenchang-brain
META="${1:?usage: rollback.sh /opt/wenchang-brain/backups/rollback-*.properties}"
[[ -f "${META}" ]] || { echo "rollback metadata missing" >&2; exit 1; }
PREVIOUS_APP="$(awk -F= '$1=="previous.app"{print substr($0,index($0,"=")+1)}' "${META}")"
PREVIOUS_MCP="$(awk -F= '$1=="previous.mcp"{print substr($0,index($0,"=")+1)}' "${META}")"
[[ -f "${PREVIOUS_APP}" && -f "${PREVIOUS_MCP}" ]] || { echo "previous release jars unavailable" >&2; exit 1; }
ln -sfn "${PREVIOUS_APP}" "${ROOT}/app/current.jar"
ln -sfn "${PREVIOUS_MCP}" "${ROOT}/mcp/current.jar"
systemctl restart wenchang-mcp.service
for _ in $(seq 1 30); do
  curl --fail --silent --show-error --max-time 3 http://127.0.0.1:18091/actuator/health >/dev/null && break
  sleep 2
done
systemctl restart wenchang-brain.service
curl --fail --silent --show-error --max-time 10 http://127.0.0.1:18091/actuator/health >/dev/null
curl --fail --silent --show-error --max-time 15 http://127.0.0.1:18080/api/health >/dev/null
echo "ROLLBACK_OK"
