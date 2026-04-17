// Android 에뮬레이터: localhost → 10.0.2.2
// 실기기 사용 시: PC의 실제 IP 주소로 변경
const BASE_HOST = '10.0.2.2';

// WebSocket 핸드셰이크 인증 토큰 (mock 프로파일용 dev bypass)
// 실환경에서는 JWT 발급 endpoint를 통해 동적으로 획득해야 함
const WS_TOKEN = 'dev-token';

export const API = {
  MARKET_BASE: `http://${BASE_HOST}:8085`,
  DEBATE_BASE: `http://${BASE_HOST}:8083`,
  WS_QUOTES: (symbol: string) =>
    `ws://${BASE_HOST}:8085/ws/quotes/${symbol}?token=${WS_TOKEN}`,
};
