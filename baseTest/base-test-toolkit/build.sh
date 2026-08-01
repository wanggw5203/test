#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
mvn clean verify
mkdir -p ../dist
cp target/base-test-toolkit-1.0.0.jar ../dist/base-test-toolkit-1.0.0.jar
shasum -a 256 ../dist/base-test-toolkit-1.0.0.jar > ../dist/base-test-toolkit-1.0.0.jar.sha256
printf 'Built %s\n' "$(cd ../dist && pwd)/base-test-toolkit-1.0.0.jar"
