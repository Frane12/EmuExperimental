# DRNAS Console — persistent Android signing

The v0.5.49 CI workflow **supports** permanently signed APK updates, but it
must never embed a private signing key in repository source code, an Actions log,
an APK or a downloadable build artifact.

## Configure once (repository Settings → Secrets and variables → Actions)

Set these four repository *secrets*:

- `DRNAS_KEYSTORE_BASE64`: base64 of your PKCS#12 keystore (no spaces/newline).
- `DRNAS_KEYSTORE_PASSWORD`: its store password.
- `DRNAS_KEY_ALIAS`: its alias.
- `DRNAS_KEY_PASSWORD`: its private key password.

To keep updates working, ideally use the **same existing DRNAS Console stable
certificate** and original application ID `com.drnas.console`.
Changing either the signing key or application ID means Android cannot
update the installed app in place.

To generate a **new** signing identity, only if the old signing key is lost,
run this LOCALLY and keep the keystore privately backed up:

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore drnas-signing.p12 \
  -alias drnas -keyalg RSA -keysize 3072 -validity 10000
base64 -w0 drnas-signing.p12
```

**Never paste the base64 private key or its password into an issue,
README, shared chat, release notes or public pull request.**
Save the base64 only into the GitHub Actions encrypted secret.

## What the CI workflow does

- When all 4 secrets are available, it verifies the keystore and signs
  the regular `com.drnas.console` application with the persistent certificate,
  producing `*-STABLE-SIGNED.apk`.
- If any secret is unavailable, it deliberately builds a side-by-side
  `.labv0549` experiment signed with a temporary CI key.
  **Transient LAB builds are NOT upgradable**; they are only for safe tests.
- Before replacing an old installation, compare `apksigner verify
  --print-certs` fingerprints of both APKs.

Android updates also require an increased versionCode (v0.5.49 uses 64).
