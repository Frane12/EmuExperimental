# Persistent APK signing and safe migration

## Update — mandatory stable key for active pre-alpha builds (17 Oct 2026)

The current DRNAS pre-alpha build branches (`r3`, `r4` and glass UI/0.2)
no longer produce or publish **temporary/debug-key** APKs when the four
GitHub Actions secrets are absent. Instead, the signing job stops with a clear
error. This is intentional: generating a new runner key would make the next
APK impossible to install as an in-place update.

The private APK build now:
1. Restores the same PKCS12 keystore from encrypted GitHub Actions secrets.
2. Validates the alias and public certificate.
3. Sets `DRNAS_REQUIRE_STABLE_SIGNING=1` for APK packaging.
4. Validates that the final APK's certificate SHA-256 matches the keystore.
5. Exports `STABLE-SIGNED.apk` with the certificate digest in README-SIGNING.

Pull requests may run tests without distributing an APK; build/publish paths
cannot fall back to transient signatures.

### Easiest setup on your own computer

You can configure **one permanent identity** with the source-controlled helper
script, run locally on a trusted machine with JDK `keytool`, `openssl`, and
GitHub CLI (`gh`) installed and authenticated:

```bash
gh auth login
bash tools/setup_permanent_signing.sh
```

The script saves the **private** PKCS12 keystore and password under
`~/.drnas-signing/` and uploads them as four encrypted repository Secrets.
**Never commit the keystore/password, paste them into chat, or place them
in a public release.** Back up both local files to secure offline storage.

The setup is idempotent: it reuses those exact files if they exist, and refuses
to replace an incomplete identity. If a DRNAS signing identity has already been
used to publish an installable APK, **restore its original key instead of
creating a new one**.

To verify setup without exposing secret contents, you can open the workflow
in GitHub Actions and confirm that `Restore mandatory permanent signing key`
passes and that the attached APK is named `STABLE-SIGNED.apk`. Keep the
certificate SHA-256 from `README-SIGNING.txt` as a long-term public identity
reference.

**Important:** the older `...TRANSIENT-LAB...` Android packages were signed
with different ephemeral certificates and often different `applicationId`
suffixes, so even a future permanent key **cannot make them upgradable in
place**. Never uninstall an existing DRNAS app without protecting its Wine
prefix and imported components if they matter to you. Stable signed builds
start an upgradeable lineage **from their first install onward**.


## Why Android refuses an update
The installed debug APK and a new GitHub Actions APK share package ID
`com.drnas.console`, but different ephemeral debug signing certificates.
Android refuses an in-place update with mismatched certificates.

The current installed APK's private signing key is NOT recoverable from its APK
or certificate. Do not assume that moving to stable signing can preserve
installed data without migration.

## Signing: one key for every future build
Create a PKCS12 signing key **locally on your own computer** with JDK `keytool`:

```sh
keytool -genkeypair -storetype PKCS12 -keystore drnas-stable.p12 -alias drnas-stable -keyalg RSA -keysize 3072 -validity 3650 -dname "CN=DRNAS Console,O=DRNAS,C=HR"
```

Enter and securely save the password. Do not store the private keystore in Git.

Add these four GitHub Actions repository secrets to
`Frane12/EmuExperimental` under Settings > Secrets and variables > Actions:

| Secret | Value |
|---|---|
| `DRNAS_KEYSTORE_BASE64` | Base64 encoding of the local `drnas-stable.p12` file |
| `DRNAS_KEYSTORE_PASSWORD` | Password chosen for the keystore |
| `DRNAS_KEY_ALIAS` | `drnas-stable` |
| `DRNAS_KEY_PASSWORD` | Key password (normally same as store password for PKCS12) |

The build workflow now refuses to publish a debug APK if any secret is missing.
It restores the key to a temporary path during CI, passes credentials to the
Gradle `debug` signing configuration, verifies the signed APK with Android
`apksigner`, and publishes a stable-signed artifact.

**Keep the private keystore AND its password offline**. If they are lost or
changed, future APKs again cannot update the app in place.

## Migration from previous one-off debug signature

1. **Keep the installed app in place. Do not uninstall yet.**
2. Connect your Android phone via USB to your own computer with platform-tools
   installed. Enable USB debugging and authorize the connection.
3. Check `adb devices` and
   `adb shell run-as com.drnas.console id`. The old APK is a debug build;
   `run-as` may permit reading its app-private data. If this check fails,
   **stop here and do not uninstall**.
4. With a supported authorized local backup tool, back up the existing app
   `files` directory (contains WCP vault, Proton/FEX/DXVK/Turnip archives,
   extracted components and Wine prefix). Preserve directory structure and
   verify archive contents and available disk space. Treat backup as private.
5. Create and configure the **stable** signing key as above, then run the
   stable-signed Actions workflow and verify successful completion.
6. Only after a verified backup and signed replacement APK should you decide
   to uninstall the old differently signed app and install the stable signed
   version. **Uninstall deletes its original private data**.
7. Restore the backed-up private files into the new *debuggable* app's own
   private storage using a trusted local ADB workflow. Confirm that Components
   lists packages and that `wine --version` continues to return `wine-11.0`.
8. Future APKs from the same key should update in place without migration.

No tool can calculate or reconstruct the installed private signing key from
the APK public signature. Device backup/restore is not automatic, and the
repository does not contain the existing user's app-private files.

## Troubleshooting
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or "conflicts with existing package":
  certificates differ. Do not uninstall before backup.
- `run-as: Package not debuggable`: the installed APK cannot be migrated
  using normal `run-as`; stop and plan a different supported backup route.
- `Install failed: version downgrade`: build versionCode must be at least
  the installed version. Certificate matching is a separate requirement.
- GitHub Actions "Stable signing secrets missing": configure all four
  repository secrets, then rerun. Never commit keystore/passwords to Git.
