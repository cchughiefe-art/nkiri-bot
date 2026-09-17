#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

mkdir -p "$HOME/.nkiri-signing"
cd "$HOME/.nkiri-signing"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool is missing."
  echo "Install OpenJDK in Termux first:"
  echo "  pkg install openjdk-17"
  exit 1
fi

if [ -f thenkiri-release.jks ]; then
  echo "Refusing to overwrite existing permanent key:"
  echo "$HOME/.nkiri-signing/thenkiri-release.jks"
  exit 1
fi

PASSWORD="$(python - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
)"
ALIAS="thenkiri"

keytool -genkeypair \
  -v \
  -keystore thenkiri-release.jks \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=TheNkiri, OU=Mobile, O=TheNkiri, L=Lagos, ST=Lagos, C=NG"

base64 -w 0 thenkiri-release.jks > thenkiri-release.jks.base64

cat > github-secrets.txt <<EOF
ANDROID_KEYSTORE_PASSWORD=$PASSWORD
ANDROID_KEY_ALIAS=$ALIAS
ANDROID_KEY_PASSWORD=$PASSWORD
ANDROID_KEYSTORE_BASE64=<copy contents of thenkiri-release.jks.base64>
EOF

chmod 600 thenkiri-release.jks thenkiri-release.jks.base64 github-secrets.txt

echo
echo "Permanent release key created locally."
echo "DO NOT delete or regenerate this key."
echo
echo "Files:"
echo "  $HOME/.nkiri-signing/thenkiri-release.jks"
echo "  $HOME/.nkiri-signing/thenkiri-release.jks.base64"
echo "  $HOME/.nkiri-signing/github-secrets.txt"
echo
echo "Add the four values in github-secrets.txt to:"
echo "GitHub repo -> Settings -> Secrets and variables -> Actions -> New repository secret"
echo
echo "Do not commit any of these files."
