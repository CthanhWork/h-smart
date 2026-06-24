import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = __ENV.BASE_URL;
const TOKEN = __ENV.TOKEN || '';
const USERNAME = __ENV.USERNAME || '';
const PASSWORD = __ENV.PASSWORD || '';
const PRODUCT_LIST_PATH = __ENV.PRODUCT_LIST_PATH || '/api/v1/products';
const PRODUCT_DETAIL_PATH = __ENV.PRODUCT_DETAIL_PATH || '/api/v1/products/{id}';
const SEARCH_PATH = '/api/v1/search/products';
const REVIEW_PATH_TEMPLATE = '/api/v1/reviews/sellers/{sellerId}';
const WISHLIST_PATH = '/api/v1/products/wishlist';
const CONVERSATIONS_PATH = '/api/v1/interactions/conversations';
const NOTIFICATIONS_PATH = '/api/v1/interactions/notifications';
const LOGIN_PATH = '/api/v1/auth/login';

if (!BASE_URL) {
  throw new Error('Missing BASE_URL. Run with: k6 run -e BASE_URL=<API_GATEWAY_URL> workflow-test.js');
}

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '1m', target: 20 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1500'],
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

function getFlexibleCollection(payload) {
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

function getFlexibleId(candidate) {
  if (!candidate) {
    return '';
  }

  if (Array.isArray(candidate)) {
    for (const item of candidate) {
      const found = getFlexibleId(item);
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

  if (candidate.data !== undefined) {
    const nested = getFlexibleId(candidate.data);
    if (nested) {
      return nested;
    }
  }

  if (candidate.items !== undefined) {
    const nested = getFlexibleId(candidate.items);
    if (nested) {
      return nested;
    }
  }

  if (candidate.content !== undefined) {
    const nested = getFlexibleId(candidate.content);
    if (nested) {
      return nested;
    }
  }

  return '';
}

function toRecord(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function getFirstItem(payload) {
  const items = getFlexibleCollection(payload);
  return items.length > 0 ? toRecord(items[0]) : {};
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

function extractKeyword(product) {
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
        test_type: 'workflow-setup',
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
  const headers = buildHeaders(token);

  // Bước 1: xem danh sách sản phẩm.
  const productListResponse = http.get(buildUrl(PRODUCT_LIST_PATH), {
    headers,
    tags: {
      test_type: 'workflow',
      endpoint: PRODUCT_LIST_PATH,
      step: 'list-products',
    },
  });

  check(productListResponse, {
    'workflow list status 200': (res) => res.status === 200,
  });

  const listPayload = parseJson(productListResponse);
  const firstProduct = getFirstItem(listPayload);
  const productId = getFlexibleId(firstProduct);

  let sellerId = firstProduct.sellerId ? `${firstProduct.sellerId}` : '';
  let keyword = extractKeyword(firstProduct);

  // Bước 2: xem chi tiết sản phẩm đầu tiên nếu có ID.
  if (productId) {
    const detailPath = buildPathWithId(PRODUCT_DETAIL_PATH, productId);
    const productDetailResponse = http.get(buildUrl(detailPath), {
      headers,
      tags: {
        test_type: 'workflow',
        endpoint: PRODUCT_DETAIL_PATH,
        step: 'product-detail',
      },
    });

    check(productDetailResponse, {
      'workflow detail status 200': (res) => res.status === 200,
    });

    const detailPayload = parseJson(productDetailResponse);
    const detailData = toRecord(unwrapData(detailPayload));
    if (!sellerId && detailData.sellerId) {
      sellerId = `${detailData.sellerId}`;
    }
    if (!keyword) {
      keyword = extractKeyword(detailData);
    }
  }

  // Bước 3: gọi review theo seller nếu lấy được sellerId.
  if (sellerId) {
    const reviewPath = buildSellerReviewPath(sellerId);
    const sellerReviewResponse = http.get(buildUrl(reviewPath), {
      headers,
      tags: {
        test_type: 'workflow',
        endpoint: REVIEW_PATH_TEMPLATE,
        step: 'seller-reviews',
      },
    });

    check(sellerReviewResponse, {
      'workflow reviews status 200': (res) => res.status === 200,
    });
  }

  // Bước 4: tìm kiếm lại theo từ khóa lấy từ sản phẩm.
  const searchSuffix = keyword ? `?q=${encodeURIComponent(keyword)}` : '';
  const searchResponse = http.get(buildUrl(`${SEARCH_PATH}${searchSuffix}`), {
    headers,
    tags: {
      test_type: 'workflow',
      endpoint: SEARCH_PATH,
      step: 'search-products',
    },
  });

  check(searchResponse, {
    'workflow search status 200': (res) => res.status === 200,
  });

  // Bước 5: nếu có JWT thì mở thêm các endpoint đọc dữ liệu cá nhân.
  if (token) {
    const wishlistResponse = http.get(buildUrl(WISHLIST_PATH), {
      headers,
      tags: {
        test_type: 'workflow',
        endpoint: WISHLIST_PATH,
        step: 'wishlist',
      },
    });

    check(wishlistResponse, {
      'workflow wishlist status 200': (res) => res.status === 200,
    });

    const conversationsResponse = http.get(buildUrl(CONVERSATIONS_PATH), {
      headers,
      tags: {
        test_type: 'workflow',
        endpoint: CONVERSATIONS_PATH,
        step: 'conversations',
      },
    });

    check(conversationsResponse, {
      'workflow conversations status 200': (res) => res.status === 200,
    });

    const notificationsResponse = http.get(buildUrl(NOTIFICATIONS_PATH), {
      headers,
      tags: {
        test_type: 'workflow',
        endpoint: NOTIFICATIONS_PATH,
        step: 'notifications',
      },
    });

    check(notificationsResponse, {
      'workflow notifications status 200': (res) => res.status === 200,
    });
  }

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'results/workflow-test-summary.json': JSON.stringify(data, null, 2),
  };
}
