import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = __ENV.BASE_URL;
const PRODUCT_LIST_PATH = __ENV.PRODUCT_LIST_PATH || '/api/v1/products';

if (!BASE_URL) {
  throw new Error('Missing BASE_URL. Run with: k6 run -e BASE_URL=<API_GATEWAY_URL> stress-test.js');
}

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '1m', target: 100 },
    { duration: '1m', target: 200 },
    { duration: '1m', target: 300 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<3000'],
  },
};

function buildUrl(path) {
  const normalizedBase = BASE_URL.replace(/\/+$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${normalizedBase}${normalizedPath}`;
}

export default function () {
  // Dùng endpoint public ổn định nhất để đẩy tải qua gateway.
  const response = http.get(buildUrl(PRODUCT_LIST_PATH), {
    headers: {
      Accept: 'application/json',
    },
    tags: {
      test_type: 'stress',
      endpoint: PRODUCT_LIST_PATH,
    },
  });

  check(response, {
    'stress status 200 hoac 429': (res) => res.status === 200 || res.status === 429,
  });

  sleep(0.2);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'results/stress-test-summary.json': JSON.stringify(data, null, 2),
  };
}
