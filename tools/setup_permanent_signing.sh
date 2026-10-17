#!/usr/bin/env bash
# Bootstrap one long-lived Android signing identity for DRNAS EMU.
# Run locally on an OWNER-controlled machine with JDK keytool + openssl + gh.
# This script never uploads private keystore/password to git.
set -euo pipefail
umask 077

repo="Frane12/EmuExperimental"
dir="${DRNAS_SIGNING_DIR:-$HOME/.drnas-signing}"
file="$dir/drnas-stable.p12"
pass_file="$dir/drnas-stable.password"
alias_name="drnas-stable"

for cmd in keytool openssl gh base64 sha256sum; do
  command -v "$cmd" >/dev/null || { echo "Missing tool: $cmd" >&2; exit 1; }
done
gh auth status >/dev/null || { echo "Authenticate with: gh auth login" >&2; exit 1; }
mkdir -p "$dir"
chmod 700 "$dir"

# Never silently rotate: doing so breaks Android in-place upgrades.
if [[ -e "$file" || -e "$pass_file" ]]; then
  [[ -s "$file" && -s "$pass_file" ]] || {
    echo "Incomplete existing signing identity in $dir. Stop and recover your original backup." >&2
    exit 1
  }
  echo "Reusing EXISTING DRNAS signing key (no rotation)."
else
  echo "Creating a NEW long-lived DRNAS signing identity in $dir."
  openssl rand -hex 32 > "$pass_file"
  chmod 600 "$pass_file"
  export DRNAS_SIGNING_PASSWORD
  DRNAS_SIGNING_PASSWORD="$(cat "$pass_file")"
  keytool -genkeypair -noprompt -storetype PKCS12 \
    -keystore "$file" -alias "$alias_name" -keyalg RSA -keysize 3072 -validity 9125 \
    -dname "CN=DRNAS EMU,O=DRNAS,C=HR" \
    -storepass:env DRNAS_SIGNING_PASSWORD -keypass:env DRNAS_SIGNING_PASSWORD
  unset DRNAS_SIGNING_PASSWORD
  chmod 600 "$file"
fi

# Keep credentials out of shell argv and GitHub source code.
base64 "$file" | tr -d '\n' | gh secret set DRNAS_KEYSTORE_BASE64 --repo "$repo"
gh secret set DRNAS_KEYSTORE_PASSWORD --repo "$repo" < "$pass_file"
printf '%s' "$alias_name" | gh secret set DRNAS_KEY_ALIAS --repo "$repo"
gh secret set DRNAS_KEY_PASSWORD --repo "$repo" < "$pass_file"

echo "Configured all four encrypted GitHub Actions signing secrets."
echo "Make an OFFLINE backup of BOTH files (keystore and password) from $dir."
echo "DO NOT delete or regenerate these: all future APK updates depend on this key."
echo "Public certificate fingerprint (no private key information):"
export DRNAS_SIGNING_PASSWORD
DRNAS_SIGNING_PASSWORD="$(cat "$pass_file")"
keytool -list -v -keystore "$file" -storetype PKCS12 -storepass:env DRNAS_SIGNING_PASSWORD -alias "$alias_name" | grep 'SHA256:'
unset DRNAS_SIGNING_PASSWORD
