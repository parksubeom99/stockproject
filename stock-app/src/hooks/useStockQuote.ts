import { useEffect, useRef, useState } from 'react';
import { API } from '../constants/api';

export interface StockQuote {
  symbol: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  timestamp: string;
}

export type WsStatus = 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR';

export function useStockQuote(symbol: string) {
  const [quote, setQuote] = useState<StockQuote | null>(null);
  const [status, setStatus] = useState<WsStatus>('DISCONNECTED');
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!symbol) return;

    const connect = () => {
      setStatus('CONNECTING');
      const ws = new WebSocket(API.WS_QUOTES(symbol));
      wsRef.current = ws;

      ws.onopen = () => setStatus('CONNECTED');

      ws.onmessage = (e) => {
        try {
          const data: StockQuote = JSON.parse(e.data);
          setQuote(data);
        } catch {
          // JSON 파싱 실패 무시
        }
      };

      ws.onerror = () => setStatus('ERROR');

      ws.onclose = () => {
        setStatus('DISCONNECTED');
        // 3초 후 재연결
        setTimeout(connect, 3000);
      };
    };

    connect();

    return () => {
      wsRef.current?.close();
    };
  }, [symbol]);

  return { quote, status };
}
