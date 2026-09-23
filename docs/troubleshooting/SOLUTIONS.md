# Verified Solutions

## Preserve V3 data when installing test updates

- Configure the build with the original, ignored v3 release signing material; do not commit the key or credentials.
- Obtain the preserved v3 release signing material from the maintainer's private archive. Read its keystore and signing fields locally at signing time; never copy credentials into this repository or command output.
- Build the Release APK and inspect its certificate with Android Build Tools `apksigner verify --print-certs`.
- Compare its SHA-256 digest with the certificate of the APK already installed on the tablet.
- Run `adb install -r` only after the digests match.
- Verification: on 2026-09-05, the corrected v3 package matched the installed certificate, `adb install -r` returned `Success`, the package retained `/data/user/0/com.noteshadow.app3`, and `MainActivity` launched successfully.
- Ineffective attempt: installing `app-debug.apk`; it correctly failed because the Debug certificate differed.
- Safety rule: do not use uninstall/reinstall unless the user explicitly accepts deletion of private application data or a separately verified backup/restore has completed.

Use the same fields as `KNOWN_ISSUES.md` and link both entries when a durable fix is confirmed. Do not record guesses or one-off command history.
