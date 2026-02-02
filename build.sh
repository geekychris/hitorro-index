#!/bin/bash

# Hitorro Index Build Script
# Builds and installs the hitorro-index module to local Maven repository

set -e

echo "Building hitorro-index..."
mvn clean install

echo ""
echo "Build complete! Module installed to local Maven repository."
echo "Artifact: com.hitorro:hitorro-index:3.0.0"
