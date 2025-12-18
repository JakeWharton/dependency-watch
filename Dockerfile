FROM alpine:3.23.2 AS build
ENV GRADLE_OPTS="-Dkotlin.incremental=false -Dorg.gradle.daemon=false -Dorg.gradle.vfs.watch=false -Dorg.gradle.logging.stacktrace=full"

RUN apk add --no-cache \
      openjdk21 \
 && rm -rf /var/cache/* \
 && mkdir /var/cache/apk

WORKDIR /app

# Get the Gradle wrapper and cache the Gradle distribution first.
COPY gradlew settings.gradle ./
COPY gradle/wrapper ./gradle/wrapper
RUN ./gradlew --version

COPY gradle/libs.versions.toml ./gradle/libs.versions.toml
COPY build.gradle ./
COPY src/main ./src/main
RUN ./gradlew installDist

FROM alpine:3.23.2
LABEL maintainer="Jake Wharton <docker@jakewharton.com>"

RUN apk add --no-cache \
      openjdk8-jre-base \
      tini \
 && rm -rf /var/cache/* \
 && mkdir /var/cache/apk

ENV \
    DEPENDENCY_WATCH_CONFIG="/config" \
    DEPENDENCY_WATCH_DATA="/data"

WORKDIR /app
COPY --from=build /app/build/install/dependency-watch ./

ENTRYPOINT ["/sbin/tini", "--", "/app/bin/dependency-watch"]
