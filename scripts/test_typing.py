"""Run device tests and preserve public-fixture screenshots/reports before cleanup."""
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))


def main():
    result = subprocess.run([
        str(ROOT / 'gradlew'), 'connectedDebugAndroidTest',
        '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true',
    ], cwd=ROOT)
    destination = ROOT / 'app/build/typing-artifacts'
    destination.mkdir(parents=True, exist_ok=True)
    copied = subprocess.run([
        str(SDK / 'platform-tools/adb'), 'pull',
        '/sdcard/Android/data/com.cdcg.convolab.debug/files/typing-test-output/.',
        str(destination),
    ])
    sys.exit(result.returncode or copied.returncode)


if __name__ == '__main__':
    main()
