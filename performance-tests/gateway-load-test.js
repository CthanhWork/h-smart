import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = __ENV.BASE_URL;
const TOKEN = __ENV.TOKEN || '';
const USERNAME = __ENV.USERNAME || '';
const PASSWORD = __ENV.PASSWORD || '';
const PRODUCT_LIST_PATH = __ENV.PRODUCT_LIST_PATH || '/api/v1/products';
const PRODUCT_DETAIL_PATH = __ENV.PRODUCT_DETAIL_PATH || '/api/v1/products/{id}';
const CATEGORY_PATH = '/api/v1/products/categories';
const SEARCH_PATH = '/api/v1/search/products';
const REVIEW_PATH_TEMPLATE = '/api/v1/reviews/sellers/{sellerId}';
const WISHLIST_PATH = '/api/v1/products/wishlist';
const LOGIN_PATH = '/api/v1/auth/login';

if (!BASE_URL) {
  throw new Error('Missing BASE_URL. Run with: k6 run -e BASE_URL=<API_GATEWAY_URL> gateway-load-test.js');
}

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '1m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.95'],
  },
};

function buildUrl(path) {
  const normalizedBase = BASE_URL.replace(/\/+$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${normalizedBase}${normalizedPath}`;
}

function buildHeaders(token) {
  const headers = {
    Accept: 'application/json',
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return headers;
}

function parseJson(response) {
  try {
    return response.json();
  } catch (error) {
    return null;
  }
}

function unwrapData(payload) {
  if (!payload || typeof payload !== 'object') {
    return payload;
  }

  if (payload.data !== undefined) {
    return payload.data;
  }

  return payload;
}

function toCollection(payload) {
  const data = unwrapData(payload);

  if (Array.isArray(data)) {
    return data;
  }

  if (!data || typeof data !== 'object') {
    return [];
  }

  if (Array.isArray(data.content)) {
    return data.content;
  }

  if (Array.isArray(data.items)) {
    return data.items;
  }

  if (Array.isArray(data.data)) {
    return data.data;
  }

  return [];
}

function toRecord(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function pickFirstProduct(payload) {
  const items = toCollection(payload);
  return items.length > 0 ? toRecord(items[0]) : {};
}

function pickId(candidate) {
  if (!candidate) {
    return '';
  }

  if (Array.isArray(candidate)) {
    for (const item of candidate) {
      const found = pickId(item);
      if (found) {
        return found;
      }
    }
    return '';
  }

  if (typeof candidate !== 'object') {
    return '';
  }

  if (candidate.id !== undefined && candidate.id !== null && `${candidate.id}`.trim()) {
    return `${candidate.id}`.trim();
  }

  if (candidate._id !== undefined && candidate._id !== null && `${candidate._id}`.trim()) {
    return `${candidate._id}`.trim();
  }

  const data = unwrapData(candidate);
  if (data !== candidate) {
    const nestedId = pickId(data);
    if (nestedId) {
      return nestedId;
    }
  }

  if (candidate.items) {
    const nestedId = pickId(candidate.items);
    if (nestedId) {
      return nestedId;
    }
  }

  if (candidate.content) {
    return pickId(candidate.content);
  }

  return '';
}

function buildPathWithId(template, id) {
  if (!id) {
    return '';
  }

  if (template.includes('{id}')) {
    return template.replace('{id}', encodeURIComponent(id));
  }

  if (template.includes(':id')) {
    return template.replace(':id', encodeURIComponent(id));
  }

  return `${template.replace(/\/+$/, '')}/${encodeURIComponent(id)}`;
}

function buildSellerReviewPath(sellerId) {
  return REVIEW_PATH_TEMPLATE.replace('{sellerId}', encodeURIComponent(sellerId));
}

function extractSearchKeyword(product) {
  const title = typeof product.title === 'string' ? product.title.trim() : '';
  if (!title) {
    return '';
  }

  const parts = title.split(/\s+/).filter((item) => item.length >= 2);
  return parts.length > 0 ? parts[0] : title;
}

function resolveToken() {
  if (TOKEN) {
    return TOKEN;
  }

  if (!USERNAME || !PASSWORD) {
    return '';
  }

  // Tự login nếu có tài khoản test.
  const response = http.post(
    buildUrl(LOGIN_PATH),
    JSON.stringify({
      usernameOrEmail: USERNAME,
      password: PASSWORD,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      tags: {
        test_type: 'gateway-load-setup',
        endpoint: LOGIN_PATH,
      },
    }
  );

  if (response.status < 200 || response.status >= 300) {
    throw new Error(`Unable to login with USERNAME/PASSWORD. Received status ${response.status} from ${LOGIN_PATH}.`);
  }

  const payload = parseJson(response);
  const data = unwrapData(payload);
  const accessToken = data && typeof data === 'object' ? data.accessToken : '';

  if (!accessToken) {
    throw new Error(`Login response from ${LOGIN_PATH} does not contain data.accessToken.`);
  }

  return accessToken;
}

export function setup() {
  return {
    token: resolveToken(),
  };
}

export default function (data) {
  const token = data && data.token ? data.token : '';
  const authHeaders = buildHeaders(token);

  // Tải danh mục để kiểm tra route public qua gateway.
  const categoryResponse = http.get(buildUrl(CATEGORY_PATH), {
    headers: authHeaders,
    tags: {
      test_type: 'gateway-load',
      endpoint: CATEGORY_PATH,
    },
  });

  check(categoryResponse, {
    'categories status 200': (res) => res.status === 200,
  });

  // Tải danh sách sản phẩm để làm dữ liệu nền cho các request tiếp theo.
  const listResponse = http.get(buildUrl(PRODUCT_LIST_PATH), {
    headers: authHeaders,
    tags: {
      test_type: 'gateway-load',
      endpoint: PRODUCT_LIST_PATH,
    },
  });

  check(listResponse, {
    'product list status 200': (res) => res.status === 200,
  });

  const listPayload = parseJson(listResponse);
  const firstProduct = pickFirstProduct(listPayload);
  const productId = pickId(firstProduct);
  const sellerId = firstProduct.sellerId ? `${firstProduct.sellerId}` : '';
  const searchKeyword = extractSearchKeyword(firstProduct);

  const searchSuffix = searchKeyword ? `?q=${encodeURIComponent(searchKeyword)}` : '';
  const searchPath = `${SEARCH_PATH}${searchSuffix}`;
  const searchResponse = http.get(buildUrl(searchPath), {
    headers: authHeaders,
    tags: {
      test_type: 'gateway-load',
      endpoint: SEARCH_PATH,
    },
  });

  check(searchResponse, {
    'search status 200': (res) => res.status === 200,
  });

  if (productId) {
    const detailPath = buildPathWithId(PRODUCT_DETAIL_PATH, productId);
    const detailResponse = http.get(buildUrl(detailPath), {
      headers: authHeaders,
      tags: {
        test_type: 'gateway-load',
        endpoint: PRODUCT_DETAIL_PATH,
      },
    });

    check(detailResponse, {
      'product detail status 200': (res) => res.status === 200,
    });
  }

  if (sellerId) {
    const reviewPath = buildSellerReviewPath(sellerId);
    const reviewResponse = http.get(buildUrl(reviewPath), {
      headers: authHeaders,
      tags: {
        test_type: 'gateway-load',
        endpoint: REVIEW_PATH_TEMPLATE,
      },
    });

    check(reviewResponse, {
      'seller review status 200': (res) => res.status === 200,
    });
  }

  if (token) {
    // Chỉ gọi endpoint đọc dữ liệu để tránh làm bẩn dữ liệu thật.
    const wishlistResponse = http.get(buildUrl(WISHLIST_PATH), {
      headers: authHeaders,
      tags: {
        test_type: 'gateway-load',
        endpoint: WISHLIST_PATH,
      },
    });

    check(wishlistResponse, {
      'wishlist status 200': (res) => res.status === 200,
    });
  }

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'results/gateway-load-test-summary.json': JSON.stringify(data, null, 2),
  };
}
