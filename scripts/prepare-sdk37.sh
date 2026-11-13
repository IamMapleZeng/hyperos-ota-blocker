#!/usr/bin/env bash
set -euo pipefail

sdk=${ANDROID_HOME:-"${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"}
source_dir="$sdk/platforms/android-37.0"
compat_dir="$sdk/platforms/android-37"

if [[ -f "$compat_dir/android.jar" ]]; then
    printf '%s\n' "API 37 compatibility path already exists: $compat_dir"
    exit 0
fi
if [[ ! -f "$source_dir/android.jar" ]]; then
    printf '%s\n' "Install Android SDK Platform 37.0 in SDK Manager first." >&2
    exit 1
fi
if [[ -e "$compat_dir" ]]; then
    printf '%s\n' "Compatibility destination already exists: $compat_dir" >&2
    exit 1
fi

stage=$(mktemp -d "$sdk/platforms/.android-37.XXXXXX")
trap 'rm -rf "$stage"' EXIT
cp -R "$source_dir/." "$stage/"
rm -f "$stage/package.xml"
sed -i '' 's/^AndroidVersion.ApiLevel=37\.0$/AndroidVersion.ApiLevel=37/' "$stage/source.properties"
mv "$stage" "$compat_dir"
printf '%s\n' "Prepared API 37 compatibility path: $compat_dir"
