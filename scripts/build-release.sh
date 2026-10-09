#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=1.2.0
./gradlew :common:testDebugUnitTest :mobile:testDebugUnitTest :libadb:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintRelease :wear:lintRelease :mobile:assembleRelease :wear:assembleRelease
destination="artifacts/v${version}"
mkdir -p "$destination"
cp mobile/build/outputs/apk/release/mobile-release.apk "$destination/wearinstaller-phone-${version}.apk"
cp wear/build/outputs/apk/release/wear-release.apk "$destination/wearinstaller-watch-${version}.apk"
cd "$destination"
sha256sum ./*.apk > SHA256SUMS
printf 'Release-Dateien: %s\n' "$PWD"
