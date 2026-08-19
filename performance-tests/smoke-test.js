import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = __ENV.BASE_URL;
const HEALTH_PATH = __ENV.HEALTH_PATH || '/health';
const PRODUCT_LIST_PATH = __ENV.PRODUCT_LIST_PATH || '/api/v1/products';

if (!BASE_URL) {
  throw new Error('Missing BASE_URL. Run with: k6 run -e BASE_URL=<API_GATEWAY_URL> smoke-test.js');
}

export const options = {
  vus: 1,
  duration: '10s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

function buildUrl(path) {
  const normalizedBase = BASE_URL.replace(/\/+$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${normalizedBase}${normalizedPath}`;
}

function buildHeaders() {
  return {
    Accept: 'application/json',
  };
}

export default function () {
  // Ưu tiên health check, fallback sang danh sách sản phẩm nếu cần.
  const targetPath = HEALTH_PATH || PRODUCT_LIST_PATH;
  const response = http.get(buildUrl(targetPath), {
    headers: buildHeaders(),
    tags: {
      test_type: 'smoke',
      endpoint: targetPath,
    },
  });

  check(response, {
    'smoke status hop le': (res) => res.status >= 200 && res.status < 400,
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'results/smoke-test-summary.json': JSON.stringify(data, null, 2),
  };
}
