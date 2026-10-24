#!/usr/bin/env bash
set -euo pipefail
# Pin the SDK manager instead of relying on changing hosted-runner PATH/CLI versions.
: "${ANDROID_HOME:?Hosted runner must provide ANDROID_HOME}"
: "${RUNNER_TEMP:?Hosted runner must provide RUNNER_TEMP}"
tools_dir="$RUNNER_TEMP/family-tracker-sdk-tools-19"
mkdir -p "$tools_dir"
curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$tools_dir/tools.zip"
printf '%s\n' "5fdcc763663eefb86a5b8879697aa6088b041e70  $tools_dir/tools.zip" | sha1sum -c -
unzip -q -o "$tools_dir/tools.zip" -d "$tools_dir"
python3 - "$tools_dir/cmdline-tools/bin/sdkmanager" <<'PY'
import os,sys,subprocess
cmd=[sys.argv[1], '--sdk_root='+os.environ['ANDROID_HOME']]
subprocess.run(cmd+['--licenses'],input='y\n'*100,text=True,check=True)
subprocess.run(cmd+['platforms;android-36','build-tools;36.0.0'],check=True)
PY
