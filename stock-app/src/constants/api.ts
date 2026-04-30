// Android 에뮬레이터: localhost → 10.0.2.2
// 실기기 사용 시: PC의 실제 IP 주소로 변경
const BASE_HOST = '10.0.2.2';

// WebSocket 핸드셰이크 인증 토큰 (mock 프로파일용 dev bypass)
//
// ⚠️ 보안 주의: 쿼리파라미터(?token=)는 URL 로깅·Referrer·프록시 캐시·
// 브라우저 히스토리에 토큰이 그대로 노출되는 취약점이 있다.
// React Native 표준 WebSocket API가 커스텀 헤더를 지원하지 않아
// 데모 환경에서만 사용한다.
//
// 프로덕션 전환 시 다음 중 하나 채택 권장:
//   1. Sec-WebSocket-Protocol 헤더에 토큰 전달 (SockJS/STOMP)
//   2. 핸드셰이크 직전 1회용 단기 토큰 발급 endpoint (nonce)
//   3. 쿠키 기반 세션 인증
const WS_TOKEN = 'dev-token';

export const API = {
  MARKET_BASE: `http://${BASE_HOST}:8081`,
  DEBATE_BASE: `http://${BASE_HOST}:8083`,
  WS_QUOTES: (symbol: string) =>
    `ws://${BASE_HOST}:8081/ws/quotes/${symbol}?token=${WS_TOKEN}`,
};
