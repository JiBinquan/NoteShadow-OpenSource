# Known Issues

## V3 Debug APK cannot update the tablet installation

- Symptoms: `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- Trigger: attempting to install a Debug-signed `com.noteshadow.app3` APK over the dedicated v3 release-signed installation.
- Root cause: Android requires an update APK to use the same signing certificate as the installed package.
- Risk: uninstalling as a workaround deletes the application's private notes and session data.
- Verified solution: [SOLUTIONS.md](SOLUTIONS.md#preserve-v3-data-when-installing-test-updates).
- Affected versions: v3 test builds using package id `com.noteshadow.app3`.

For a recurring issue, record: problem, symptoms, trigger conditions, root cause, verified solution, ineffective attempts, verification method, related modules, and affected versions. Mark a solution as verified only after reproducing the fix.
