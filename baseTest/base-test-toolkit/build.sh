#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
mvn clean verify
mkdir -p ../dist
for artifact in target/base-test-toolkit-*.jar; do
  [ -f "$artifact" ] || exit 1
  name=$(basename "$artifact")
  cp "$artifact" "../dist/$name"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "../dist/$name" > "../dist/$name.sha256"
  else
    shasum -a 256 "../dist/$name" > "../dist/$name.sha256"
  fi
  printf 'Built %s/%s\n' "$(cd ../dist && pwd)" "$name"
done
