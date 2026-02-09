#!/bin/bash
set -e

echo "Building Java plugin..."
./gradlew clean build jar

mkdir -p luanext/plugins/share
cp build/libs/extension-luanext-*.jar luanext/plugins/share/extension-luanext.jar

echo "Plugin build complete!"
