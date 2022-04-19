FROM adoptopenjdk:8-jdk-hotspot AS build
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dkotlin.incremental=false"
WORKDIR /app

COPY gradlew settings.gradle ./
COPY gradle ./gradle
RUN ./gradlew --version

COPY build.gradle ./
COPY src ./src
RUN ./gradlew build


FROM golang:1.18-alpine AS shell
RUN apk add --no-cache shellcheck
ENV GO111MODULE=on
RUN go install mvdan.cc/sh/v3/cmd/shfmt@latest
WORKDIR /overlay
COPY root/ ./
COPY .editorconfig /
RUN find . -type f | xargs shellcheck -e SC1008
RUN shfmt -d .


FROM oznu/s6-alpine:3.11
LABEL maintainer="Jake Wharton <docker@jakewharton.com>"

RUN apk add --no-cache \
      curl \
      openjdk8-jre \
 && rm -rf /var/cache/* \
 && mkdir /var/cache/apk

ENV \
    # Fail if cont-init scripts exit with non-zero code.
    S6_BEHAVIOUR_IF_STAGE2_FAILS=2 \
    CRON="" \
    HEALTHCHECK_ID="" \
    HEALTHCHECK_HOST="https://hc-ping.com" \
    PUID="" \
    PGID="" \
    NOTIFY_IFTTT="" \
    DEPENDENCY_WATCH_ARGS=""
COPY root/ /
WORKDIR /app
COPY --from=build /app/build/install/dependency-watch ./
