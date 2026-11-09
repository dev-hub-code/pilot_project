#!/usr/bin/env bash
# One-time move of the app's database from the Lightsail database (database.tf) to RDS (rds.tf):
#   terraform -chdir=infra/lightsail apply     creates RDS; the app keeps using the Lightsail database
#   infra/lightsail/migrate-to-rds.sh          copies the data across and switches the app to RDS
#
# The API is stopped while the data is copied (a few minutes), so nothing written meanwhile is lost; the rest of the
# site stays up and shows errors for that time. Every table's row count is compared before switching. If anything
# fails before the switch, the API is started again on the Lightsail database, which this script never changes.
set -euo pipefail
cd "$(dirname "$0")/../.."
here=infra/lightsail
tf() { terraform -chdir=$here output "$@"; }

if [[ "$(tf -raw database_url)" != "$(tf -raw lightsail_database_url)" ]]; then
  echo "The app already uses RDS: nothing to migrate." >&2
  exit 1
fi
host=$(tf -raw static_ip)
ssh_opts=(-i ~/.ssh/sealease_lightsail -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15)
# remote reads nothing from this terminal (so the prompt below gets the answer); send_to pipes its input to the server.
remote() { ssh "${ssh_opts[@]}" "ubuntu@$host" "$@" </dev/null; }
send_to() { ssh "${ssh_opts[@]}" "ubuntu@$host" "$@"; }
host_of() { sed -E 's#jdbc:postgresql://([^:/]+).*#\1#' <<<"$1"; }
src="host=$(host_of "$(tf -raw lightsail_database_url)") dbname=sealease user=sealease sslmode=require"
dst="host=$(host_of "$(tf -raw rds_database_url)") dbname=sealease user=sealease sslmode=require"

# The passwords go to the server in files only the deploy user can read, never on a command line.
work=/opt/sealease/migrate
remote "umask 077 && mkdir -p $work"
echo "PGPASSWORD=$(tf -raw lightsail_database_password)" | send_to "umask 077; cat > $work/source.env"
echo "PGPASSWORD=$(tf -raw rds_database_password)" | send_to "umask 077; cat > $work/target.env"
psql_on() { remote "docker run --rm --env-file $work/$1.env postgres:18 psql '$2' -v ON_ERROR_STOP=1 -tA -c \"$3\""; }

switched=false
api_stopped=false
cleanup() {
  remote "rm -rf $work" || true
  if $api_stopped && ! $switched; then
    echo "Starting the API again on the Lightsail database…" >&2
    remote "cd /opt/sealease && docker compose up -d --wait backend" >&2 || true
  fi
}
trap cleanup EXIT

echo "Checking that the server can reach RDS and that it is empty…"
tables=$(psql_on target "$dst" "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")
if [[ "$tables" != "0" ]]; then
  echo "RDS already has $tables table(s) in it. Refusing to copy over them." >&2
  exit 1
fi

# CONFIRM=yes skips the question (unattended runs).
if [[ "${CONFIRM:-}" != "yes" ]]; then
  read -r -p "This stops the API for a few minutes while the data is copied. Continue? [y/N] " answer
  [[ "$answer" == [yY] ]] || { echo "Nothing changed."; exit 1; }
fi

echo "Stopping the API…"
remote "cd /opt/sealease && docker compose stop backend"
api_stopped=true

echo "Copying the data…"
remote "docker run --rm --env-file $work/source.env -v $work:/work postgres:18 pg_dump '$src' -Fc -f /work/sealease.dump"
remote "docker run --rm --env-file $work/target.env -v $work:/work:ro postgres:18 \
  pg_restore --no-owner --no-privileges --exit-on-error --single-transaction -d '$dst' /work/sealease.dump"

echo "Comparing every table's row count…"
counts="SELECT string_agg(table_name || '=' || (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', table_name), false, true, '')))[1]::text, ' ' ORDER BY table_name) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
before=$(psql_on source "$src" "$counts")
after=$(psql_on target "$dst" "$counts")
if [[ "$before" != "$after" ]]; then
  echo "The copy does not match the original; not switching." >&2
  diff <(tr ' ' '\n' <<<"$before") <(tr ' ' '\n' <<<"$after") >&2 || true
  exit 1
fi
echo "All $(wc -w <<<"$after") tables match."

echo "Switching the app to RDS…"
printf '# Set by migrate-to-rds.sh: the app uses RDS (rds.tf). Commit this file.\ndatabase = "rds"\n' > $here/database.auto.tfvars
terraform -chdir=$here apply -refresh-only -auto-approve -compact-warnings >/dev/null
$here/env.sh >/dev/null
grep -v '^#' .secrets/lightsail.env | send_to 'umask 077; cat > /opt/sealease/backend.env'
remote "cd /opt/sealease && docker compose up -d --wait backend"
switched=true

echo "Done: the app runs on RDS. Next:"
echo "  1. Check the site, then commit $here/database.auto.tfvars."
echo "  2. After a few days, delete the Lightsail database: remove database.tf's database and run terraform apply"
echo "     (a final snapshot, sealease-dev-db-final, is kept)."
