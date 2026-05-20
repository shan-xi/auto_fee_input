#!/usr/bin/env bash
# Build a macOS .dmg installer for Auto Fee Input.
# Thin wrapper around package-macos.sh — equivalent to `./package-macos.sh dmg`.
#
# Output: dist/AutoFeeInput.app  and  dist/AutoFeeInput-<version>.dmg

set -euo pipefail
cd "$(dirname "$0")"
exec ./package-macos.sh dmg
