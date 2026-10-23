#!/usr/bin/env bash
set -euo pipefail
cd /workspace/FamilyTracker
mkdir -p /workspace/tool-downloads /workspace/android-sdk/cmdline-tools/19.0 /workspace/android-user /workspace/jdk21 /workspace/cache /workspace/config /workspace/gradle-cache
if [[ ! -x /workspace/jdk21/bin/javac ]]; then
  curl -fsSL 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz' -o /workspace/tool-downloads/jdk21.tar.gz
  printf '%s\n' 'd7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c  /workspace/tool-downloads/jdk21.tar.gz' | sha256sum -c -
  tar -xzf /workspace/tool-downloads/jdk21.tar.gz -C /workspace/jdk21 --strip-components=1
fi
if [[ ! -x /workspace/android-sdk/cmdline-tools/19.0/bin/sdkmanager ]]; then
  curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o /workspace/tool-downloads/android-tools-19.zip
  printf '%s\n' '5fdcc763663eefb86a5b8879697aa6088b041e70  /workspace/tool-downloads/android-tools-19.zip' | sha1sum -c -
  unzip -q /workspace/tool-downloads/android-tools-19.zip -d /workspace/tool-downloads/sdk-extract
  mv /workspace/tool-downloads/sdk-extract/cmdline-tools/* /workspace/android-sdk/cmdline-tools/19.0/
fi
source scripts/cloud-env.sh
# Use the platform's proxy without copying credentials or disabling certificate checks.
python3 - <<'PY'
import os,pathlib,urllib.parse,subprocess
proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
props=pathlib.Path('/workspace/gradle-cache/gradle.properties')
# Update only this script's block, preserving unrelated existing user Gradle settings.
start='# BEGIN FAMILY TRACKER CLOUD PROXY\n';end='# END FAMILY TRACKER CLOUD PROXY\n'
text=props.read_text() if props.exists() else ''
if start in text and end in text:
 a,b=text.split(start,1);_,c=b.split(end,1);text=a+c
if proxy.hostname:
 text+=start+f'systemProp.https.proxyHost={proxy.hostname}\nsystemProp.https.proxyPort={proxy.port or 80}\nsystemProp.http.proxyHost={proxy.hostname}\nsystemProp.http.proxyPort={proxy.port or 80}\nsystemProp.http.nonProxyHosts=localhost|127.*\n'+end
props.write_text(text)
cmd=['/workspace/android-sdk/cmdline-tools/19.0/bin/sdkmanager','--sdk_root=/workspace/android-sdk']
if proxy.hostname:cmd+=['--proxy=http','--proxy_host='+proxy.hostname,'--proxy_port='+str(proxy.port or 80)]
subprocess.run(cmd+['--licenses'],input='y\n'*100,text=True,check=True)
subprocess.run(cmd+['platforms;android-36','build-tools;36.0.0','platform-tools'],check=True)
PY
# SDK location is supplied by ANDROID_HOME; existing local.properties is preserved.
npm --prefix firebase ci
./gradlew :app:assembleEmulatorDebug :app:testEmulatorDebugUnitTest :app:lintEmulatorDebug
