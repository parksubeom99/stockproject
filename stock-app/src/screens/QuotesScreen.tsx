import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TouchableWithoutFeedback,
  TextInput,
  ScrollView,
  StatusBar,
  Keyboard,
} from 'react-native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from '../../App';
import { useStockQuote } from '../hooks/useStockQuote';

type Props = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'Quotes'>;
};

// ─── 종목 메타데이터 ────────────────────────────────────────────────────────
type StockMeta = {
  symbol: string;
  name: string;
  nameKr: string;
  market: string;
  sector: string;
  flag: string;
};

const ALL_SYMBOLS: StockMeta[] = [
  // ── 미국 주식 ──
  { symbol: 'AAPL',    name: 'Apple Inc.',             nameKr: '애플',          market: 'NASDAQ', sector: '기술', flag: '🇺🇸' },
  { symbol: 'TSLA',    name: 'Tesla Inc.',              nameKr: '테슬라',        market: 'NASDAQ', sector: '전기차', flag: '🇺🇸' },
  { symbol: 'NVDA',    name: 'NVIDIA Corporation',      nameKr: '엔비디아',      market: 'NASDAQ', sector: '반도체', flag: '🇺🇸' },
  { symbol: 'MSFT',    name: 'Microsoft Corporation',   nameKr: '마이크로소프트', market: 'NASDAQ', sector: '기술', flag: '🇺🇸' },
  { symbol: 'AMZN',    name: 'Amazon.com Inc.',         nameKr: '아마존',        market: 'NASDAQ', sector: '이커머스', flag: '🇺🇸' },
  { symbol: 'GOOGL',   name: 'Alphabet Inc.',           nameKr: '구글',          market: 'NASDAQ', sector: '기술', flag: '🇺🇸' },
  { symbol: 'META',    name: 'Meta Platforms Inc.',     nameKr: '메타',          market: 'NASDAQ', sector: '소셜미디어', flag: '🇺🇸' },
  { symbol: 'NFLX',    name: 'Netflix Inc.',            nameKr: '넷플릭스',      market: 'NASDAQ', sector: '엔터테인먼트', flag: '🇺🇸' },
  { symbol: 'AMD',     name: 'Advanced Micro Devices',  nameKr: 'AMD',           market: 'NASDAQ', sector: '반도체', flag: '🇺🇸' },
  { symbol: 'INTC',    name: 'Intel Corporation',       nameKr: '인텔',          market: 'NASDAQ', sector: '반도체', flag: '🇺🇸' },
  // ── 국내 주식 ──
  { symbol: 'SAMSUNG', name: 'Samsung Electronics',     nameKr: '삼성전자',      market: 'KOSPI',  sector: '반도체', flag: '🇰🇷' },
  { symbol: 'SKHYNIX', name: 'SK Hynix',                nameKr: 'SK하이닉스',    market: 'KOSPI',  sector: '반도체', flag: '🇰🇷' },
  { symbol: 'KAKAO',   name: 'Kakao Corp.',             nameKr: '카카오',        market: 'KOSPI',  sector: '플랫폼', flag: '🇰🇷' },
  { symbol: 'NAVER',   name: 'NAVER Corporation',       nameKr: '네이버',        market: 'KOSPI',  sector: '플랫폼', flag: '🇰🇷' },
  { symbol: 'HYUNDAI', name: 'Hyundai Motor Company',   nameKr: '현대자동차',    market: 'KOSPI',  sector: '자동차', flag: '🇰🇷' },
  { symbol: 'LGELEC',  name: 'LG Electronics',          nameKr: 'LG전자',        market: 'KOSPI',  sector: '가전', flag: '🇰🇷' },
  { symbol: 'CELLTRION',name:'Celltrion Inc.',           nameKr: '셀트리온',      market: 'KOSPI',  sector: '바이오', flag: '🇰🇷' },
  { symbol: 'KRAFTON', name: 'Krafton Inc.',             nameKr: '크래프톤',      market: 'KOSPI',  sector: '게임', flag: '🇰🇷' },
  // ── Mock 테스트용 ──
  { symbol: 'TESELA',  name: 'Tesla (Mock Demo)',        nameKr: '테슬라(데모)',   market: 'MOCK',   sector: '테스트', flag: '🧪' },
];

const POPULAR_SYMBOLS = ['AAPL', 'TSLA', 'SAMSUNG', 'KAKAO', 'NAVER'];

// 심볼로 메타 조회 (없으면 기본값)
const getMeta = (symbol: string): StockMeta =>
  ALL_SYMBOLS.find((s) => s.symbol === symbol) ?? {
    symbol,
    name: symbol,
    nameKr: symbol,
    market: 'UNKNOWN',
    sector: '-',
    flag: '🌐',
  };

export default function QuotesScreen({ navigation }: Props) {
  const [inputSymbol, setInputSymbol] = useState('AAPL');
  const [activeSymbol, setActiveSymbol] = useState('AAPL');
  const [showDropdown, setShowDropdown] = useState(false);
  const { quote, status } = useStockQuote(activeSymbol);

  const meta = getMeta(activeSymbol);

  const dropdownItems = inputSymbol.trim().length > 0
    ? ALL_SYMBOLS.filter((s) => {
        const q = inputSymbol.trim().toUpperCase();
        return (
          s.symbol.includes(q) ||
          s.name.toUpperCase().includes(q) ||
          s.nameKr.includes(inputSymbol.trim())
        );
      })
    : [];

  const handleInputChange = (text: string) => {
    setInputSymbol(text.toUpperCase());
    setShowDropdown(text.trim().length > 0);
  };

  const handleSelectSymbol = (symbol: string) => {
    setInputSymbol(symbol);
    setActiveSymbol(symbol);
    setShowDropdown(false);
    Keyboard.dismiss();
  };

  const handleSearch = () => {
    if (inputSymbol.trim()) {
      setActiveSymbol(inputSymbol.trim().toUpperCase());
      setShowDropdown(false);
      Keyboard.dismiss();
    }
  };

  const isPositive = quote ? quote.change >= 0 : true;
  const changeColor = isPositive ? '#00C896' : '#FF4757';
  const statusColor =
    status === 'CONNECTED' ? '#00C896' : status === 'CONNECTING' ? '#FFA502' : '#FF4757';

  return (
    // ✅ 섹션 I 패턴: TouchableWithoutFeedback으로 외부 터치 시 드롭다운 닫기
    <TouchableWithoutFeedback onPress={() => { setShowDropdown(false); Keyboard.dismiss(); }}>
      <View style={styles.container}>
        <StatusBar barStyle="light-content" />

        {/* 헤더 */}
        <View style={styles.header}>
          <Text style={styles.headerTitle}>📈 AI 투자 분석</Text>
          <View style={[styles.statusBadge, { backgroundColor: statusColor + '22' }]}>
            <View style={[styles.statusDot, { backgroundColor: statusColor }]} />
            <Text style={[styles.statusText, { color: statusColor }]}>{status}</Text>
          </View>
        </View>

        {/* 검색 영역 — ScrollView 밖, absolute 드롭다운 */}
        <View style={styles.searchWrapper}>
          <View style={styles.searchRow}>
            <TextInput
              style={styles.input}
              value={inputSymbol}
              onChangeText={handleInputChange}
              placeholder="종목명 또는 심볼 (예: 삼성, TSLA)"
              placeholderTextColor="#666"
              autoCapitalize="characters"
              onSubmitEditing={handleSearch}
              onFocus={() => setShowDropdown(inputSymbol.trim().length > 0)}
              // ✅ onBlur 완전 제거 — Android 터치 이벤트 순서 문제
            />
            <TouchableOpacity style={styles.searchBtn} onPress={handleSearch}>
              <Text style={styles.searchBtnText}>조회</Text>
            </TouchableOpacity>
          </View>

          {/* ✅ elevation: 20 — Android에서 ScrollView 위로 뜨기 위해 필수 */}
          {showDropdown && (
            <View style={styles.dropdown}>
              {dropdownItems.length > 0 ? (
                dropdownItems.slice(0, 6).map((item, idx) => (
                  <TouchableOpacity
                    key={item.symbol}
                    style={[
                      styles.dropdownItem,
                      idx === Math.min(dropdownItems.length, 6) - 1 && styles.dropdownItemLast,
                    ]}
                    onPress={() => handleSelectSymbol(item.symbol)}
                  >
                    <Text style={styles.dropdownFlag}>{item.flag}</Text>
                    <View style={styles.dropdownTextCol}>
                      <Text style={styles.dropdownSymbol}>{item.symbol}</Text>
                      <Text style={styles.dropdownName}>{item.nameKr}</Text>
                    </View>
                    <Text style={styles.dropdownMarket}>{item.market}</Text>
                  </TouchableOpacity>
                ))
              ) : (
                <View style={[styles.dropdownItem, styles.dropdownItemLast]}>
                  <Text style={styles.dropdownNoResult}>
                    "{inputSymbol}" — 조회 버튼으로 직접 검색
                  </Text>
                </View>
              )}
            </View>
          )}
        </View>

        {/* ✅ keyboardShouldPersistTaps="always" — 드롭다운 터치 이벤트 허용 */}
        <ScrollView
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="always"
        >
          {/* 인기 종목 칩 */}
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsRow}>
            {POPULAR_SYMBOLS.map((s) => {
              const m = getMeta(s);
              return (
                <TouchableOpacity
                  key={s}
                  style={[styles.chip, activeSymbol === s && styles.chipActive]}
                  onPress={() => handleSelectSymbol(s)}
                >
                  <Text style={styles.chipFlag}>{m.flag}</Text>
                  <Text style={[styles.chipText, activeSymbol === s && styles.chipTextActive]}>
                    {s}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>

          {/* 시세 카드 */}
          <View style={styles.quoteCard}>
            {/* 종목 헤더 */}
            <View style={styles.symbolRow}>
              <Text style={styles.symbolFlag}>{meta.flag}</Text>
              <View>
                <Text style={styles.symbolKr}>{meta.nameKr}</Text>
                <Text style={styles.symbol}>{activeSymbol}</Text>
              </View>
              <View style={styles.marketBadge}>
                <Text style={styles.marketBadgeText}>{meta.market}</Text>
              </View>
            </View>

            {/* 섹터 태그 */}
            <View style={styles.sectorRow}>
              <View style={styles.sectorTag}>
                <Text style={styles.sectorTagText}># {meta.sector}</Text>
              </View>
            </View>

            {/* 가격 정보 */}
            {quote ? (
              <>
                <Text style={styles.price}>
                  {meta.market === 'KOSPI' || meta.market === 'KOSDAQ' ? '₩' : '$'}
                  {quote.price.toFixed(2)}
                </Text>
                <View style={styles.changeRow}>
                  <Text style={[styles.change, { color: changeColor }]}>
                    {isPositive ? '+' : ''}{quote.change.toFixed(2)}
                  </Text>
                  <Text style={[styles.changePercent, { color: changeColor }]}>
                    ({isPositive ? '+' : ''}{quote.changePercent.toFixed(2)}%)
                  </Text>
                </View>
                <View style={styles.metaRow}>
                  <View style={styles.metaItem}>
                    <Text style={styles.metaLabel}>거래량</Text>
                    <Text style={styles.metaValue}>{(quote.volume / 1_000_000).toFixed(1)}M</Text>
                  </View>
                  <View style={styles.metaDivider} />
                  <View style={styles.metaItem}>
                    <Text style={styles.metaLabel}>업데이트</Text>
                    <Text style={styles.metaValue}>
                      {new Date(quote.timestamp).toLocaleTimeString('ko-KR')}
                    </Text>
                  </View>
                  <View style={styles.metaDivider} />
                  <View style={styles.metaItem}>
                    <Text style={styles.metaLabel}>거래소</Text>
                    <Text style={styles.metaValue}>{meta.market}</Text>
                  </View>
                </View>
              </>
            ) : (
              <View style={styles.loadingBox}>
                <Text style={styles.loadingText}>
                  {status === 'CONNECTING' ? '연결 중...' : '데이터 대기 중'}
                </Text>
              </View>
            )}
          </View>

          {/* AI 토론 요청 버튼 */}
          <TouchableOpacity
            style={styles.debateBtn}
            onPress={() =>
              navigation.navigate('DebateRequest', { symbol: activeSymbol, price: quote?.price })
            }
          >
            <Text style={styles.debateBtnIcon}>🤖</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.debateBtnTitle}>AI 토론 분석 요청</Text>
              <Text style={styles.debateBtnSub}>
                {meta.nameKr}({activeSymbol}) 투자 가설을 5인 AI가 검토합니다
              </Text>
            </View>
            <Text style={styles.debateBtnArrow}>›</Text>
          </TouchableOpacity>

          <View style={{ height: 40 }} />
        </ScrollView>
      </View>
    </TouchableWithoutFeedback>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0A0E1A' },

  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 56,
    paddingBottom: 16,
  },
  headerTitle: { fontSize: 22, fontWeight: '700', color: '#FFFFFF' },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 20,
    gap: 6,
  },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  statusText: { fontSize: 11, fontWeight: '600' },

  searchWrapper: { marginHorizontal: 20, marginBottom: 12, zIndex: 999 },
  searchRow: { flexDirection: 'row', gap: 8 },
  input: {
    flex: 1,
    backgroundColor: '#151B2E',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: '#FFFFFF',
    fontSize: 15,
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  searchBtn: {
    backgroundColor: '#3D7BFF',
    borderRadius: 12,
    paddingHorizontal: 20,
    justifyContent: 'center',
  },
  searchBtnText: { color: '#FFF', fontWeight: '700', fontSize: 14 },

  dropdown: {
    position: 'absolute',
    top: 52,
    left: 0,
    right: 72,
    backgroundColor: '#1C2340',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#3D7BFF88',
    zIndex: 1000,
    elevation: 20,          // ✅ Android 필수
    overflow: 'hidden',
  },
  dropdownItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 11,
    borderBottomWidth: 1,
    borderBottomColor: '#2A3250',
    gap: 10,
  },
  dropdownItemLast: { borderBottomWidth: 0 },
  dropdownFlag: { fontSize: 18 },
  dropdownTextCol: { flex: 1 },
  dropdownSymbol: { fontSize: 13, fontWeight: '700', color: '#FFFFFF' },
  dropdownName: { fontSize: 11, color: '#8899BB', marginTop: 1 },
  dropdownMarket: {
    fontSize: 10,
    color: '#3D7BFF',
    fontWeight: '600',
    backgroundColor: '#3D7BFF18',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  dropdownNoResult: { fontSize: 12, color: '#8899BB' },

  chipsRow: { paddingLeft: 20, marginBottom: 20 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 8,
    backgroundColor: '#151B2E',
    marginRight: 8,
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  chipActive: { backgroundColor: '#3D7BFF', borderColor: '#3D7BFF' },
  chipFlag: { fontSize: 12 },
  chipText: { color: '#8899BB', fontSize: 13, fontWeight: '600' },
  chipTextActive: { color: '#FFFFFF' },

  quoteCard: {
    marginHorizontal: 20,
    backgroundColor: '#151B2E',
    borderRadius: 20,
    padding: 24,
    borderWidth: 1,
    borderColor: '#2A3250',
    marginBottom: 16,
  },
  symbolRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 8,
  },
  symbolFlag: { fontSize: 28 },
  symbolKr: { fontSize: 16, fontWeight: '700', color: '#FFFFFF' },
  symbol: { fontSize: 12, color: '#8899BB', marginTop: 2 },
  marketBadge: {
    marginLeft: 'auto',
    backgroundColor: '#3D7BFF18',
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderWidth: 1,
    borderColor: '#3D7BFF44',
  },
  marketBadgeText: { fontSize: 11, color: '#3D7BFF', fontWeight: '700' },
  sectorRow: { marginBottom: 16 },
  sectorTag: {
    alignSelf: 'flex-start',
    backgroundColor: '#2A3250',
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  sectorTagText: { fontSize: 11, color: '#8899BB' },

  price: { fontSize: 44, fontWeight: '700', color: '#FFFFFF', marginBottom: 8 },
  changeRow: { flexDirection: 'row', gap: 8, alignItems: 'center', marginBottom: 20 },
  change: { fontSize: 18, fontWeight: '600' },
  changePercent: { fontSize: 16, fontWeight: '500' },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: 16 },
  metaItem: { gap: 4 },
  metaDivider: { width: 1, height: 28, backgroundColor: '#2A3250' },
  metaLabel: { fontSize: 11, color: '#8899BB', fontWeight: '500' },
  metaValue: { fontSize: 13, color: '#FFFFFF', fontWeight: '600' },
  loadingBox: { height: 80, justifyContent: 'center', alignItems: 'center' },
  loadingText: { color: '#8899BB', fontSize: 14 },

  debateBtn: {
    marginHorizontal: 20,
    marginBottom: 40,
    backgroundColor: '#1A1F35',
    borderRadius: 16,
    padding: 20,
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#3D7BFF44',
    gap: 14,
  },
  debateBtnIcon: { fontSize: 28 },
  debateBtnTitle: { fontSize: 15, fontWeight: '700', color: '#FFFFFF', marginBottom: 2 },
  debateBtnSub: { fontSize: 12, color: '#8899BB' },
  debateBtnArrow: { fontSize: 24, color: '#3D7BFF' },
});
