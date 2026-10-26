#!/usr/bin/env bash
# Sets one setting in .secrets/production.env from a hidden prompt, so a password never appears on screen, in shell
# history or in a chat. Takes effect on the server with the next scripts/deploy.sh (or scripts/push-config.sh).
#   scripts/set-secret.sh MAIL_PASSWORD
set -euo pipefail
cd "$(dirname "$0")/.."
key=${1:?usage: scripts/set-secret.sh KEY}
[[ "$key" =~ ^[A-Z][A-Z0-9_]*$ ]] || { echo "KEY must be upper case, e.g. MAIL_PASSWORD" >&2; exit 1; }
file=.secrets/production.env
read -rsp "$key: " value
echo
[[ -n "$value" ]] || { echo "Nothing entered; $key left as it was." >&2; exit 1; }
KEY="$key" VALUE="$value" python3 - "$file" <<'PY'
import os, sys
path, key, value = sys.argv[1], os.environ["KEY"], os.environ["VALUE"]
lines = open(path).read().splitlines()
found = False
for i, line in enumerate(lines):
    if line.startswith(key + "="):
        lines[i] = f"{key}={value}"
        found = True
if not found:
    lines.append(f"{key}={value}")
fd = os.open(path, os.O_WRONLY | os.O_TRUNC)
os.write(fd, ("\n".join(lines) + "\n").encode())
os.close(fd)
PY
echo "Saved $key in $file."
