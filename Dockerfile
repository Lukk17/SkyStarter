# need to be run from project root !
ARG APP_NAME=sky-starter
ARG HOME=/home/app
ARG STOREPASS=changeit

# Stage 1: Build Stage
FROM eclipse-temurin:21-jdk AS builder

ARG APP_NAME
ARG HOME

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH

COPY ./app/src/main/ $HOME/$APP_NAME/app/src/main/
COPY ./app/build.gradle.kts $HOME/$APP_NAME/app/

COPY ./infrastructure/src/main/ $HOME/$APP_NAME/infrastructure/src/main/
COPY ./infrastructure/build.gradle.kts $HOME/$APP_NAME/infrastructure/

COPY ./service/src/main/ $HOME/$APP_NAME/service/src/main/
COPY ./service/build.gradle.kts $HOME/$APP_NAME/service/

COPY ./domain/src/main/ $HOME/$APP_NAME/domain/src/main/
COPY ./domain/build.gradle.kts $HOME/$APP_NAME/domain/

COPY ./gradle/ $HOME/$APP_NAME/gradle/
COPY --chmod=755 ./gradlew $HOME/$APP_NAME/
COPY ./build.gradle.kts $HOME/$APP_NAME/
COPY ./settings.gradle.kts $HOME/$APP_NAME/
COPY ./lombok.config $HOME/$APP_NAME/

WORKDIR $HOME/$APP_NAME

RUN ./gradlew app:bootJar --no-daemon --stacktrace

# Stage 2: Runtime Stage
FROM eclipse-temurin:21-jre-alpine AS runtime

ARG APP_NAME
ARG HOME
ARG STOREPASS

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH

COPY --from=builder $HOME/$APP_NAME/app/build/libs/$APP_NAME.jar ./app.jar

COPY ./certificates/localhost/localhostDomain.crt /tmp/localhostDomain.crt
# Delete existing entry in cacerts (if any)
# Import the certificate into the Java keystore
RUN keytool -importcert \
    -file /tmp/localhostDomain.crt \
    -alias keycloak-cert \
    -keystore "$JAVA_HOME"/lib/security/cacerts \
    -storepass "$STOREPASS" \
    -noprompt

# needed for healthcheck
RUN apk add --no-cache curl

EXPOSE 7979

ENTRYPOINT ["java", "-jar", "./app.jar"]

CMD ["--spring.profiles.active=docker"]
