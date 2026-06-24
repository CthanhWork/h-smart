import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const AI_BASE_URL = __ENV.AI_BASE_URL;
const AI_DETECT_PATH = __ENV.AI_DETECT_PATH || '/api/v1/predict';
const AI_FILE_FIELD = __ENV.AI_FILE_FIELD || 'file';
const TOKEN = __ENV.TOKEN || '';

if (!AI_BASE_URL) {
  throw new Error('Missing AI_BASE_URL. Run with: k6 run -e AI_BASE_URL=<AI_SERVICE_URL> ai-service-test.js');
}

let imageFile;
try {
  // K6 đọc ảnh mẫu ở thư mục hiện tại.
  imageFile = open('./sample.jpg', 'b');
} catch (error) {
  throw new Error('Missing sample.jpg in performance-tests/. Place a test image at performance-tests/sample.jpg before running ai-service-test.js.');
}

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '1m', target: 10 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000'],
    checks: ['rate>0.90'],
  },
};

function buildUrl(path) {
  const normalizedBase = AI_BASE_URL.replace(/\/+$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${normalizedBase}${normalizedPath}`;
}

function parseJson(response) {
  try {
    return response.json();
  } catch (error) {
    return null;
  }
}

export default function () {
  const payload = {
    [AI_FILE_FIELD]: http.file(imageFile, 'sample.jpg', 'image/jpeg'),
  };
  const headers = {
    Accept: 'application/json',
  };

  if (TOKEN) {
    headers.Authorization = `Bearer ${TOKEN}`;
  }

  // Gửi multipart/form-data tới AI-service.
  const response = http.post(buildUrl(AI_DETECT_PATH), payload, {
    headers,
    tags: {
      test_type: 'ai-service',
      endpoint: AI_DETECT_PATH,
      file_field: AI_FILE_FIELD,
    },
  });

  const json = parseJson(response);

  check(response, {
    'ai status 200': (res) => res.status === 200,
    'ai tra ve label hoac detections': () =>
      !!(json && (typeof json.label === 'string' || Array.isArray(json.detections) || typeof json.num_detections === 'number')),
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'results/ai-service-test-summary.json': JSON.stringify(data, null, 2),
  };
}
