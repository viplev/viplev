FROM gradle:8-jdk21-alpine AS build

ARG APP_VERSION=0.0.0

WORKDIR /app

COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon

COPY src src
RUN gradle bootJar -PappVersion=${APP_VERSION} --no-daemon

FROM amazoncorretto:21-alpine-jdk AS runtime

RUN apk --no-cache add curl

WORKDIR /app

COPY --from=build /app/build/libs/viplev-api.jar /app/viplev-api.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 CMD curl --fail http://localhost:8080/actuator/health || exit 1

CMD ["java", "-jar", "/app/viplev-api.jar"]
