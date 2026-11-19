#!/usr/bin/env bash
set -euo pipefail
swift sdk install https://download.swift.org/swift-6.4.2-release/android-sdk/swift-6.4.2-RELEASE/swift-6.4.2-RELEASE_android.artifactbundle.tar.gz --checksum 4d6ad63ea74becd58e123ff58b1b48c3be5ec0db0dff0ea5abe37ac007dd3233
export ANDROID_NDK_HOME="${ANDROID_HOME}/ndk/30.0.16248370"
# Swift's native build system needs these NDK links on Linux.
if [[ "$(uname -s)" == "Linux" ]]; then
    typing_sdk_root=$(swift sdk configure --show-configuration swift-6.4.2-RELEASE_android aarch64-unknown-linux-android28 | sed -n 's/^sdkRootPath: //p')
    bash "${typing_sdk_root%/ndk-sysroot}/scripts/setup-android-sdk.sh"
fi
