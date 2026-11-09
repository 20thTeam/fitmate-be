#!/usr/bin/env bash
# 로컬에서 jar를 빌드해 EC2로 올리고 컨테이너를 다시 띄운다.
# 사용: EC2_HOST=<고정 IP> EC2_KEY=<pem 경로> ./deploy/deploy.sh
# 서버의 ~/fitmate/.env는 처음 한 번 직접 만들어 둔다(.env.example 참고).
set -euo pipefail

: "${EC2_HOST:?EC2_HOST(서버 고정 IP)를 지정하세요}"
: "${EC2_KEY:?EC2_KEY(pem 키 경로)를 지정하세요}"
EC2_USER="${EC2_USER:-ubuntu}"
REMOTE_DIR="fitmate"

cd "$(dirname "$0")/.."

echo "1/3 테스트·빌드"
./gradlew clean bootJar

echo "2/3 서버로 파일 복사"
ssh -i "$EC2_KEY" "$EC2_USER@$EC2_HOST" "mkdir -p ~/$REMOTE_DIR"
scp -i "$EC2_KEY" build/libs/app.jar deploy/Dockerfile deploy/docker-compose.yml deploy/Caddyfile \
    "$EC2_USER@$EC2_HOST:~/$REMOTE_DIR/"

echo "3/3 컨테이너 재시작 (실시간 접속이 잠깐 끊긴다)"
ssh -i "$EC2_KEY" "$EC2_USER@$EC2_HOST" "cd ~/$REMOTE_DIR && docker compose up -d --build"

echo "완료. 헬스 체크: curl https://<DOMAIN>/actuator/health"
