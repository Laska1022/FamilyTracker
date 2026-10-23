#!/usr/bin/env bash
# Source this file in each fresh cloud shell. No secrets are stored here.
export ANDROID_HOME=/workspace/android-sdk
export ANDROID_USER_HOME=/workspace/android-user
export GRADLE_USER_HOME=/workspace/gradle-cache
export npm_config_cache=/workspace/npm-cache
export XDG_CACHE_HOME=/workspace/cache
export XDG_CONFIG_HOME=/workspace/config
export FIREBASE_EMULATORS_PATH=/workspace/firebase-emulators
export JAVA_HOME=/workspace/jdk21
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
export JAVA_TOOL_OPTIONS="-Duser.home=/workspace/android-user -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
