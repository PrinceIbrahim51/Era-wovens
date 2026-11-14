#!/bin/bash
# Creates one database and one least-privilege login per service. Passwords come from the environment.
# No service can connect to another service's database.
set -euo pipefail
for svc in catalog inventory cart checkout orders cod payments notifications; do
  upper=$(echo "$svc" | tr '[:lower:]' '[:upper:]')
  pass_var="${upper}_DB_PASSWORD"
  pass="${!pass_var:?missing $pass_var}"
  user="${svc}_svc"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       -v user="$user" -v pass="$pass" -v db="$svc" <<'SQL'
CREATE ROLE :"user" LOGIN PASSWORD :'pass';
CREATE DATABASE :"db" OWNER :"user";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
SQL
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$svc" \
       -c "REVOKE CREATE ON SCHEMA public FROM PUBLIC;" \
       -c "ALTER SCHEMA public OWNER TO ${user};"
done
