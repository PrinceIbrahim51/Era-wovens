# Generic runtime image for any service. Build the jar first (mvn -DskipTests package), then:
#   docker build -f services/Dockerfile --build-arg SERVICE=catalog-service -t aurelia/catalog-service .
# Non-root, JRE-only, pinned by digest, no secrets in the image (all config comes from the environment).
FROM eclipse-temurin:21-jre-alpine@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474
ARG SERVICE
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
WORKDIR /app
COPY --chown=app:app services/${SERVICE}/target/${SERVICE}-*.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
