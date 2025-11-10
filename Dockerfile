FROM azul/zulu-openjdk:25-latest AS build
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dkotlin.incremental=false"
WORKDIR /app

COPY gradlew settings.gradle .editorconfig gradle.propertie[s] ./
COPY gradle ./gradle
RUN ./gradlew --version

COPY build.gradle ./
COPY src ./src
RUN ./gradlew build


FROM koalaman/shellcheck-alpine:stable AS shellcheck
WORKDIR /overlay
COPY root/ ./
RUN find . -type f | xargs shellcheck -e SC1008


FROM mvdan/shfmt:v3-alpine AS shfmt
WORKDIR /overlay
COPY root/ ./
COPY .editorconfig /
RUN shfmt -d .


FROM crazymax/alpine-s6:3.22
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
    NOTIFY_IFTTT="" \
    NOTIFY_SLACK="" \
    NOTIFY_TEAMS="" \
    DEPENDENCY_WATCH_ARGS=""
COPY root/ /
WORKDIR /app
COPY --from=build /app/build/install/dependency-watch ./
