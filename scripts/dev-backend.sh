#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
if [[ -x "$ROOT/backend/mvnw" ]]; then
  MVN=("$ROOT/backend/mvnw")
else
  MVN=(mvn)
fi
exec "${MVN[@]}" -f "$ROOT/backend/pom.xml" spring-boot:run
