#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."
adb=${ADB:-adb}
mkdir -p "$PWD/work"
out=$(mktemp -d "$PWD/work/device.XXXXXX")
echo "诊断保存到 $out"
if [[ $("$adb" get-state) != device ]]; then
    "$adb" devices -l >&2
    exit 1
fi

for prop in ro.product.model ro.product.device ro.build.fingerprint ro.build.display.id \
    ro.build.version.incremental ro.build.version.release ro.build.version.sdk \
    ro.mi.os.version.name ro.mi.os.version.code ro.build.ab_update; do
    printf '%s=%s\n' "$prop" "$("$adb" shell getprop "$prop" | tr -d '\r')"
done | tee "$out/build.txt"

"$adb" shell pm list packages -s -f > "$out/packages.txt"
"$adb" shell service list > "$out/services.txt"
"$adb" shell cmd package query-activities --brief -a android.settings.SYSTEM_UPDATE_SETTINGS \
    > "$out/system-update-activities.txt" 2>&1 || true
"$adb" shell cmd package query-activities --brief -a android.settings.SYSTEM_UPDATE_SETTINGS | head -30
rg -i 'updat|ota|recovery' "$out/packages.txt" "$out/services.txt" || true

if [[ $# -eq 1 ]]; then
    package=$1
    [[ $package =~ ^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$ ]] || exit 2
    "$adb" shell dumpsys package "$package" > "$out/package.txt"
    "$adb" shell dumpsys activity services "$package" > "$out/activity-services.txt"
    "$adb" shell pm path "$package" | tr -d '\r' > "$out/apk-paths.txt"
    index=0
    while IFS= read -r path; do
        [[ $path == package:/*.apk ]] || continue
        index=$((index + 1))
        "$adb" pull "${path#package:}" "$out/$index.apk"
        shasum -a 256 "$out/$index.apk"
    done < "$out/apk-paths.txt" | tee "$out/apk-sha256.txt"
fi
