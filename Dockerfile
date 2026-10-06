# ---------- 1단계: 빌드 (JDK + Gradle) ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 의존성 정의 파일만 먼저 복사 → 소스만 바뀌었을 땐 의존성 다운로드 레이어를 캐시로 재사용
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---------- 2단계: 실행 (JRE만, 빌드 도구 없음 → 이미지 작고 안전) ----------
FROM eclipse-temurin:17-jre
ENV TZ=Asia/Seoul
WORKDIR /app

# root가 아닌 전용 계정으로 실행 (앱이 털려도 컨테이너 안 권한이 최소)
RUN useradd --system --uid 1001 fitbet \
    && mkdir -p /data/uploads \
    && chown -R fitbet /data

COPY --from=build /app/build/libs/fitbet-*.jar app.jar
USER fitbet
EXPOSE 8080

# MaxRAMPercentage: 컨테이너 메모리 한도의 60%까지만 힙으로 (작은 서버에서 MariaDB와 나눠 쓰기)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
