// Android 에뮬레이터: localhost → 10.0.2.2
// 실기기 사용 시: PC의 실제 IP 주소로 변경
const BASE_HOST = '10.0.2.2';

export const API = {
  MARKET_BASE: `http://${BASE_HOST}:8085`,
  DEBATE_BASE: `http://${BASE_HOST}:8083`,
  WS_QUOTES: (symbol: string) => `ws://${BASE_HOST}:8085/ws/quotes/${symbol}`,
};
