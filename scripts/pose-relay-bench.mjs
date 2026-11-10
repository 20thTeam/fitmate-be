// 동작 정보(POSE) 중계 지연 측정 스크립트 (이슈 #4)
//
// 한 운동방에 참가자 여러 명을 붙이고, 모두가 같은 빈도로 POSE를 보낼 때
// "보낸 시각 → 다른 참가자가 PARTICIPANT_POSE를 받은 시각"의 차이(중계 지연)를 잰다.
// 모든 클라이언트가 한 프로세스에 있어 같은 시계(performance.now)를 쓰므로 시계 차이 보정이 필요 없다.
//
// 서버는 좌표를 해석하지 않고 관절 배열을 그대로 넘기므로, 첫 관절의 x 자리에 보낸 시각을 넣어 잰다.
//
// 실행 (Node 22 이상, 추가 설치 없음):
//   node scripts/pose-relay-bench.mjs --base http://localhost:8080 --clients 5 --hz 15 --seconds 30 --joints 13
//
// 주의: 로컬 한 대에서 재면 네트워크 왕복이 거의 없다. 서버 처리 지연의 하한으로만 본다.
//       실제 합격 수치는 배포 서버와 여러 PC로 다시 잰다.

import { performance } from 'node:perf_hooks';

// ===== 옵션 =====
const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, value, index, all) => {
    if (value.startsWith('--')) pairs.push([value.slice(2), all[index + 1]]);
    return pairs;
  }, []),
);
const BASE = args.base ?? 'http://localhost:8080';
const CLIENTS = Number(args.clients ?? 5);
const HZ = Number(args.hz ?? 15);
const SECONDS = Number(args.seconds ?? 30);
const JOINTS = Number(args.joints ?? 13);
const WARMUP_SECONDS = 2; // 처음 2초는 JIT·연결 안정화 구간이라 통계에서 뺀다
const WS_URL = BASE.replace(/^http/, 'ws') + '/ws';

// ===== REST: 참가자 생성과 운동방 입장 =====
async function rest(path, { method = 'GET', body, token } = {}) {
  const response = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'X-Participant-Token': token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await response.json();
  if (!response.ok) throw new Error(`${method} ${path} 실패: ${response.status} ${JSON.stringify(json)}`);
  return json.data;
}

// ===== POSE 메시지: 관절 JOINTS개, 소수 3자리 (docs/동작정보_형식.md D안·C안 모양) =====
function poseMessage(sentAt) {
  const landmarks = [];
  for (let i = 0; i < JOINTS; i++) {
    const v = () => Number((Math.random() * 2 - 1).toFixed(3));
    landmarks.push([v(), v(), v(), Number(Math.random().toFixed(2))]);
  }
  landmarks[0][0] = Number(sentAt.toFixed(3)); // 첫 관절 x 자리에 보낸 시각(ms)
  return JSON.stringify({ type: 'POSE', payload: { landmarks } });
}

// ===== 클라이언트 하나 =====
function connectClient(token, roomId, stats) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(WS_URL);
    const timer = setTimeout(() => reject(new Error('JOIN 응답 없음')), 10_000);
    ws.addEventListener('open', () => {
      ws.send(JSON.stringify({ type: 'JOIN', payload: { token, roomId } }));
    });
    ws.addEventListener('message', (event) => {
      const message = JSON.parse(event.data);
      if (message.type === 'ROOM_SNAPSHOT') {
        clearTimeout(timer);
        resolve(ws);
      } else if (message.type === 'PARTICIPANT_POSE') {
        const latency = performance.now() - message.payload.landmarks[0][0];
        stats.received++;
        if (stats.measuring) stats.latencies.push(latency);
      } else if (message.type === 'ERROR') {
        stats.errors.push(message.payload.code);
      }
    });
    ws.addEventListener('error', (error) => reject(error));
    ws.addEventListener('close', (event) => {
      if (event.code !== 1000) stats.closes.push(`${event.code} ${event.reason}`);
    });
  });
}

function percentile(sorted, p) {
  if (sorted.length === 0) return NaN;
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}

// ===== 실행 =====
const rooms = await rest('/api/rooms');
const roomId = rooms[0].roomId;
const stats = { received: 0, latencies: [], errors: [], closes: [], measuring: false };

const sockets = [];
for (let i = 0; i < CLIENTS; i++) {
  const participant = await rest('/api/participants', {
    method: 'POST',
    body: { nickname: `측정${i + 1}`, characterCode: 'CHARACTER_01' },
  });
  await rest(`/api/rooms/${roomId}/enter`, { method: 'POST', token: participant.clientToken });
  sockets.push(await connectClient(participant.clientToken, roomId, stats));
}

const sampleSize = Buffer.byteLength(poseMessage(performance.now()));
console.log(`방 ${roomId}, 참가자 ${CLIENTS}명, ${HZ}Hz, ${SECONDS}초, 관절 ${JOINTS}개 (POSE 약 ${sampleSize}B)`);

let sent = 0;
const intervalMs = 1000 / HZ;
const pings = setInterval(() => sockets.forEach((ws) => ws.send('{"type":"PING"}')), 10_000);
const senders = sockets.map((ws, index) =>
  // 참가자마다 시작 시점을 조금씩 어긋나게 해 실제처럼 섞이게 한다
  new Promise((resolve) => setTimeout(() => {
    const timer = setInterval(() => {
      ws.send(poseMessage(performance.now()));
      sent++;
    }, intervalMs);
    resolve(timer);
  }, (intervalMs / CLIENTS) * index)),
);
const timers = await Promise.all(senders);

await new Promise((resolve) => setTimeout(resolve, WARMUP_SECONDS * 1000));
stats.measuring = true;
const receivedAtStart = stats.received;
const sentAtStart = sent;
await new Promise((resolve) => setTimeout(resolve, (SECONDS - WARMUP_SECONDS) * 1000));
stats.measuring = false;
const sentInWindow = sent - sentAtStart;
const receivedInWindow = stats.received - receivedAtStart;

timers.forEach(clearInterval);
clearInterval(pings);
await new Promise((resolve) => setTimeout(resolve, 500));
sockets.forEach((ws) => ws.send('{"type":"LEAVE"}'));

const sorted = [...stats.latencies].sort((a, b) => a - b);
const expected = sentInWindow * (CLIENTS - 1);
const fmt = (n) => n.toFixed(1);
console.log(`측정 구간 ${SECONDS - WARMUP_SECONDS}초: 보냄 ${sentInWindow}개, 받아야 할 수 ${expected}개, 받음 ${receivedInWindow}개 (${fmt((receivedInWindow / expected) * 100)}%)`);
console.log(`중계 지연(ms): p50 ${fmt(percentile(sorted, 50))}, p95 ${fmt(percentile(sorted, 95))}, p99 ${fmt(percentile(sorted, 99))}, 최대 ${fmt(sorted.at(-1))}`);
console.log(`오류 ${stats.errors.length}개${stats.errors.length ? ' ' + [...new Set(stats.errors)].join(',') : ''}, 비정상 종료 ${stats.closes.length}개`);
setTimeout(() => process.exit(0), 500);
