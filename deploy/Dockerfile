# FITMATE 백엔드 실행 이미지.
# 서버(EC2)는 메모리가 작아 Gradle 빌드를 하지 않는다. 로컬에서 만든 app.jar를 복사해 실행만 한다.
FROM eclipse-temurin:21-jre

# 서버 기본 시간대는 UTC다. 공개 파티 오픈 시각·운동 날짜가 한국 시간 기준이 되도록 고정한다.
ENV TZ=Asia/Seoul

# 루트가 아닌 사용자로 실행한다
RUN useradd --system --create-home fitmate
USER fitmate
WORKDIR /home/fitmate

COPY --chown=fitmate:fitmate app.jar app.jar

EXPOSE 8080

# 컨테이너 메모리 한도의 75%까지만 힙으로 쓴다
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
