ARG FACE_API_VERSION=1.7.15
ARG WASM_VERSION=4.22.0

FROM --platform=$BUILDPLATFORM node:22-alpine AS face-assets
ARG FACE_API_VERSION
ARG WASM_VERSION
WORKDIR /assets
RUN npm pack "@vladmandic/face-api@${FACE_API_VERSION}" --silent \
    && npm pack "@tensorflow/tfjs-backend-wasm@${WASM_VERSION}" --silent \
    && mkdir face-api wasm \
    && tar -xzf vladmandic-face-api-"${FACE_API_VERSION}".tgz -C face-api \
    && tar -xzf tensorflow-tfjs-backend-wasm-"${WASM_VERSION}".tgz -C wasm \
    && mkdir -p out/model out/wasm \
    && cp face-api/package/dist/face-api.js out/ \
    && cp face-api/package/model/tiny_face_detector_model* out/model/ \
    && cp face-api/package/model/face_landmark_68_tiny_model* out/model/ \
    && cp face-api/package/model/face_recognition_model* out/model/ \
    && cp wasm/package/dist/tf-backend-wasm.js out/wasm/ \
    && cp wasm/package/dist/*.wasm out/wasm/ \
    && cp face-api/package/LICENSE out/LICENSE-face-api.txt \
    && (cp wasm/package/LICENSE* out/LICENSE-tfjs-backend-wasm.txt 2>/dev/null || echo "Apache-2.0 - https://github.com/tensorflow/tfjs" > out/LICENSE-tfjs-backend-wasm.txt)

FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
COPY docs/referentiel-des-lignes.json docs/referentiel-des-lignes.json
COPY docs/response_info-traffic.json docs/response_info-traffic.json
COPY --from=face-assets /assets/out/ /build/src/main/resources/static/vendor/face-api/
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:25-jre
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.revision=$GIT_COMMIT
ENV GIT_COMMIT=$GIT_COMMIT \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xmx384m -Xss512k"
RUN useradd --system --uid 10001 --no-create-home assistant
WORKDIR /app
COPY --from=build /build/target/app.jar /app/app.jar
USER assistant
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
