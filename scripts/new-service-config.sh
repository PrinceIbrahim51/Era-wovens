#!/usr/bin/env bash
# Internal scaffolding helper: writes base application.yml for a service. Extra YAML on stdin is appended.
set -euo pipefail
name="$1"; port="$2"; db="$3"; DB_UPPER="$(echo "$db" | tr a-z A-Z)"
dir="services/$name/src/main/resources"; mkdir -p "$dir/db/migration"
cat > "$dir/application.yml" <<YML
# All secrets come from the environment / secret manager. Missing values fail startup (fail closed).
server:
  port: \${${DB_UPPER}_SERVICE_PORT:$port}
  error:
    include-stacktrace: never
    include-message: never
    whitelabel.enabled: false
  shutdown: graceful
spring:
  application.name: $name
  main.banner-mode: off
  threads.virtual.enabled: true
  datasource:
    url: jdbc:postgresql://\${DATABASE_HOST}:\${DATABASE_PORT}/$db
    username: \${${DB_UPPER}_DB_USER}
    password: \${${DB_UPPER}_DB_PASSWORD}
    hikari.maximum-pool-size: 10
  flyway:
    enabled: true
    locations: classpath:db/migration
  servlet.multipart.enabled: false
  jackson.default-property-inclusion: non_null
  mvc.problemdetails.enabled: false
platform:
  security:
    gateway-token: \${GATEWAY_INTERNAL_TOKEN}
    service-token: \${SERVICE_INTERNAL_TOKEN}
management:
  endpoints.web.exposure.include: health
  endpoint.health:
    show-details: never
    probes.enabled: true
logging.pattern.level: "%5p [%X{requestId:-}]"
YML
extra="$(cat || true)"; [ -n "$extra" ] && printf '%s\n' "$extra" >> "$dir/application.yml"
true
