#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "deploy.sh must run as root" >&2
  exit 1
fi

RELEASE_SOURCE="${1:?usage: deploy.sh <extracted-release-dir> <java17-bin>}"
JAVA_BIN="${2:?usage: deploy.sh <extracted-release-dir> <java17-bin>}"
ROOT=/opt/wenchang-brain
VERSION="$(awk -F= '$1=="release.version"{print $2}' "${RELEASE_SOURCE}/release-info.txt")"
RELEASE_DIR="${ROOT}/releases/${VERSION}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

[[ "${RELEASE_SOURCE}" = /* ]] || RELEASE_SOURCE="$(readlink -f "${RELEASE_SOURCE}")"
[[ -n "${VERSION}" ]] || { echo "release.version missing" >&2; exit 1; }

[[ -x "${JAVA_BIN}" ]] || { echo "Java executable not found: ${JAVA_BIN}" >&2; exit 1; }
"${JAVA_BIN}" -version 2>&1 | head -1
[[ -f "${RELEASE_SOURCE}/wenchang-brain.jar" ]] || { echo "main jar missing" >&2; exit 1; }
[[ -f "${RELEASE_SOURCE}/wenchang-mcp.jar" ]] || { echo "mcp jar missing" >&2; exit 1; }
[[ -d "${RELEASE_SOURCE}/knowledge" ]] || { echo "knowledge missing" >&2; exit 1; }
[[ -d "${RELEASE_SOURCE}/data-seed" ]] || { echo "data seed missing" >&2; exit 1; }

if ! id wenchang >/dev/null 2>&1; then
  useradd --system --home-dir "${ROOT}" --shell /sbin/nologin wenchang
fi
install -d -o wenchang -g wenchang -m 0750 \
  "${ROOT}" "${ROOT}/app" "${ROOT}/mcp" "${ROOT}/config" "${ROOT}/data" \
  "${ROOT}/data/chat" "${ROOT}/data/artifacts" "${ROOT}/data/research" \
  "${ROOT}/knowledge" "${ROOT}/logs" "${ROOT}/runtime" "${ROOT}/releases" "${ROOT}/backups"

[[ ! -e "${RELEASE_DIR}" ]] || { echo "release already exists: ${RELEASE_DIR}" >&2; exit 1; }
install -d -o wenchang -g wenchang -m 0750 "${RELEASE_DIR}"
cp -a "${RELEASE_SOURCE}/wenchang-brain.jar" "${RELEASE_DIR}/wenchang-brain.jar"
cp -a "${RELEASE_SOURCE}/wenchang-mcp.jar" "${RELEASE_DIR}/wenchang-mcp.jar"
cp -a "${RELEASE_SOURCE}/release-info.txt" "${RELEASE_DIR}/release-info.txt"
cp -a "${RELEASE_SOURCE}/checksums.sha256" "${RELEASE_DIR}/checksums.sha256"
chown -R wenchang:wenchang "${RELEASE_DIR}"
(cd "${RELEASE_SOURCE}" && sha256sum -c checksums.sha256)

if [[ -n "$(find "${ROOT}/knowledge" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  tar -C "${ROOT}" -czf "${ROOT}/backups/knowledge-${STAMP}.tar.gz" knowledge
fi
find "${ROOT}/knowledge" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
cp -a "${RELEASE_SOURCE}/knowledge/." "${ROOT}/knowledge/"

for seed in "${RELEASE_SOURCE}"/data-seed/*; do
  [[ -f "${seed}" ]] || continue
  name="$(basename "${seed}")"
  if [[ -f "${ROOT}/data/${name}" ]]; then
    cp -a "${ROOT}/data/${name}" "${ROOT}/backups/${name}.${STAMP}"
  fi
  cp -a "${seed}" "${ROOT}/data/${name}"
done
chown -R wenchang:wenchang "${ROOT}/knowledge" "${ROOT}/data" "${ROOT}/logs"

if [[ ! -f "${ROOT}/config/local-secrets.properties" ]]; then
  install -o wenchang -g wenchang -m 0600 \
    "${RELEASE_SOURCE}/config/local-secrets.properties.example" \
    "${ROOT}/config/local-secrets.properties"
fi
chmod 0600 "${ROOT}/config/local-secrets.properties"

cat >"${ROOT}/config/mcp-servers.yml" <<'EOF'
spring:
  ai:
    mcp:
      client:
        enabled: true
        initialized: true
        type: SYNC
        request-timeout: 20s
        toolcallback.enabled: true
        streamable-http:
          connections:
            wenchang-public-resource:
              url: http://127.0.0.1:18091
              endpoint: /mcp
EOF

cat >"${ROOT}/config/runtime.env" <<EOF
JAVA_BIN=${JAVA_BIN}
SERVER_ADDRESS=127.0.0.1
WENCHANG_KNOWLEDGE_DIR=${ROOT}/knowledge
WENCHANG_VECTOR_STORE_FILE=${ROOT}/data/wenchang-vector-store.json
WENCHANG_OFFICIAL_SOURCE_REGISTRY_FILE=${ROOT}/data/official-source-registry.json
WENCHANG_PLACES_FILE=${ROOT}/data/wenchang-places.json
WENCHANG_POLICIES_FILE=${ROOT}/data/wenchang-policies.json
WENCHANG_PUBLIC_SERVICES_FILE=${ROOT}/data/wenchang-public-services.json
WENCHANG_TRACE_FILE=${ROOT}/logs/agent-trace.jsonl
WENCHANG_ARTIFACT_ROOT=${ROOT}/data/artifacts
WENCHANG_RESEARCH_DIR=${ROOT}/data/research
WENCHANG_DATA_ROOT=${ROOT}/data
WENCHANG_TOWNSHIPS_FILE=wenchang-townships.json
WENCHANG_SOURCES_INDEX_FILE=${ROOT}/knowledge/SOURCES_INDEX.csv
WENCHANG_ARTIFACT_DOWNLOAD_BASE_URL=/wenchang-brain/api/artifacts
EOF
chown wenchang:wenchang "${ROOT}/config/mcp-servers.yml" "${ROOT}/config/runtime.env"
chmod 0640 "${ROOT}/config/mcp-servers.yml" "${ROOT}/config/runtime.env"

PREVIOUS_APP="$(readlink -f "${ROOT}/app/current.jar" 2>/dev/null || true)"
PREVIOUS_MCP="$(readlink -f "${ROOT}/mcp/current.jar" 2>/dev/null || true)"
printf 'previous.app=%s\nprevious.mcp=%s\nrelease.version=%s\n' \
  "${PREVIOUS_APP}" "${PREVIOUS_MCP}" "${VERSION}" >"${ROOT}/backups/rollback-${STAMP}.properties"
ln -sfn "${RELEASE_DIR}/wenchang-brain.jar" "${ROOT}/app/current.jar"
ln -sfn "${RELEASE_DIR}/wenchang-mcp.jar" "${ROOT}/mcp/current.jar"
chown -h wenchang:wenchang "${ROOT}/app/current.jar" "${ROOT}/mcp/current.jar"

for unit in wenchang-mcp.service wenchang-brain.service; do
  if [[ -f "/etc/systemd/system/${unit}" ]]; then
    cp -a "/etc/systemd/system/${unit}" "${ROOT}/backups/${unit}.${STAMP}"
  fi
  sed "s|@@JAVA_BIN@@|${JAVA_BIN}|g" "${RELEASE_SOURCE}/deploy/${unit}" >"/etc/systemd/system/${unit}"
done
if command -v logrotate >/dev/null 2>&1; then
  install -o root -g root -m 0644 "${RELEASE_SOURCE}/deploy/wenchang-logrotate" /etc/logrotate.d/wenchang-brain
fi
systemctl daemon-reload
systemctl enable wenchang-mcp.service wenchang-brain.service >/dev/null
systemctl restart wenchang-mcp.service

for _ in $(seq 1 30); do
  curl --fail --silent --show-error --max-time 3 http://127.0.0.1:18091/actuator/health >/dev/null && break
  sleep 2
done
curl --fail --silent --show-error --max-time 5 http://127.0.0.1:18091/actuator/health >/dev/null
systemctl restart wenchang-brain.service
for _ in $(seq 1 45); do
  curl --fail --silent --show-error --max-time 3 http://127.0.0.1:18080/api/health >/dev/null && break
  sleep 2
done
curl --fail --silent --show-error --max-time 5 http://127.0.0.1:18080/api/health >/dev/null
echo "DEPLOYED ${VERSION}; rollback metadata: ${ROOT}/backups/rollback-${STAMP}.properties"
