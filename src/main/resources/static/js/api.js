/**
 * fetch 공통 래퍼.
 * - 성공: JSON 바디 반환
 * - 실패: 서버의 ErrorResponse({code, message})를 담은 Error를 throw
 * - 401(세션 만료): 로그인 페이지로 보내고, 로그인 후 지금 페이지로 돌아오게 한다
 */
async function api(url, { method = 'GET', body } = {}) {
  const isForm = body instanceof FormData;
  const res = await fetch(url, {
    method,
    headers: isForm || body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: isForm ? body : body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await res.json().catch(() => null);

  if (res.status === 401) {
    location.href = '/?next=' + encodeURIComponent(location.pathname + location.search);
    throw new Error('로그인이 필요합니다.');
  }
  if (!res.ok) {
    const error = new Error(data?.message || '요청에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    error.code = data?.code;
    error.status = res.status;
    throw error;
  }
  return data;
}

/** 사용자 입력이 들어가는 텍스트는 innerHTML 대신 이 함수(textContent)로 넣는다 → XSS 방지 */
function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
}

function won(amount) {
  return amount.toLocaleString('ko-KR') + '원';
}
