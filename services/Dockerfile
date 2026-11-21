# Generic runtime image for any service. Build the jar first (mvn -DskipTests package), then:
#   docker build -f services/Dockerfile --build-arg SERVICE=catalog-service -t aurelia/catalog-service .
# Non-root, JRE-only, pinned by digest, no secrets in the image (all config comes from the environment).
FROM eclipse-temurin:24-jre-alpine@sha256:4044b6c87cb088885bcd0220f7dc7a8a4aab76577605fa471945d2e98270741f
ARG SERVICE
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
WORKDIR /app
COPY --chown=app:app services/${SERVICE}/target/${SERVICE}-*.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
