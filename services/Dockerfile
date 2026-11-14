# Generic runtime image for any service. Build the jar first (mvn -DskipTests package), then:
#   docker build -f services/Dockerfile --build-arg SERVICE=catalog-service -t aurelia/catalog-service .
# Non-root, JRE-only, pinned by digest, no secrets in the image (all config comes from the environment).
FROM eclipse-temurin:25-jre-alpine@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918
ARG SERVICE
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
WORKDIR /app
COPY --chown=app:app services/${SERVICE}/target/${SERVICE}-*.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
