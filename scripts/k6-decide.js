// FlowLink 决策接口压测脚本
// 用法：k6 run -e BASE=http://localhost:8090 -e API_KEY=<替换为你的租户 Key> scripts/k6-decide.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8090';
const API_KEY = __ENV.API_KEY || 'demo-tenant-key';

export const options = {
  scenarios: {
    steady: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '1m', target: 200 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<120', 'p(99)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const subjectId = `user-${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    ruleSetKey: 'order_risk',
    subjectId,
    eventType: 'ORDER_CREATE',
    features: {
      amount: Math.floor(Math.random() * 20000),
      deviceAgeDays: Math.floor(Math.random() * 400),
      ordersLastHour: Math.floor(Math.random() * 12),
      ipRiskScore: Math.floor(Math.random() * 100),
    },
    attributes: {
      channel: 'app',
      region: Math.random() > 0.7 ? 'oversea' : 'cn',
    },
    explain: false,
  });

  const res = http.post(`${BASE}/api/v1/decisions:evaluate`, payload, {
    headers: { 'Content-Type': 'application/json', 'X-API-Key': API_KEY },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'decision returned': (r) => r.body && r.body.includes('decision'),
  });
  sleep(0.01);
}
