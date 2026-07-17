#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
mvn clean verify
printf 'Built %s\n' "$(pwd)/target/base-test-toolkit-1.0.0.jar"
