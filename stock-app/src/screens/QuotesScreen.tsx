import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TextInput,
  ScrollView,
  StatusBar,
} from 'react-native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from '../../App';
import { useStockQuote } from '../hooks/useStockQuote';

type Props = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'Quotes'>;
};

const POPULAR_SYMBOLS = ['AAPL', 'TSLA', 'NVDA', 'MSFT', 'AMZN'];

export default function QuotesScreen({ navigation }: Props) {
  const [inputSymbol, setInputSymbol] = useState('AAPL');
  const [activeSymbol, setActiveSymbol] = useState('AAPL');
  const { quote, status } = useStockQuote(activeSymbol);

  const handleSearch = () => {
    if (inputSymbol.trim()) {
      setActiveSymbol(inputSymbol.trim().toUpperCase());
    }
  };

  const isPositive = quote ? quote.change >= 0 : true;
  const changeColor = isPositive ? '#00C896' : '#FF4757';
  const statusColor =
    status === 'CONNECTED' ? '#00C896' : status === 'CONNECTING' ? '#FFA502' : '#FF4757';

  return (
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

      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 검색 */}
        <View style={styles.searchRow}>
          <TextInput
            style={styles.input}
            value={inputSymbol}
            onChangeText={(t) => setInputSymbol(t.toUpperCase())}
            placeholder="종목 심볼 입력"
            placeholderTextColor="#666"
            autoCapitalize="characters"
            onSubmitEditing={handleSearch}
          />
          <TouchableOpacity style={styles.searchBtn} onPress={handleSearch}>
            <Text style={styles.searchBtnText}>조회</Text>
          </TouchableOpacity>
        </View>

        {/* 인기 종목 */}
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsRow}>
          {POPULAR_SYMBOLS.map((s) => (
            <TouchableOpacity
              key={s}
              style={[styles.chip, activeSymbol === s && styles.chipActive]}
              onPress={() => {
                setActiveSymbol(s);
                setInputSymbol(s);
              }}
            >
              <Text style={[styles.chipText, activeSymbol === s && styles.chipTextActive]}>
                {s}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {/* 시세 카드 */}
        <View style={styles.quoteCard}>
          <Text style={styles.symbol}>{activeSymbol}</Text>

          {quote ? (
            <>
              <Text style={styles.price}>${quote.price.toFixed(2)}</Text>
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
                  <Text style={styles.metaValue}>{(quote.volume / 1000000).toFixed(1)}M</Text>
                </View>
                <View style={styles.metaItem}>
                  <Text style={styles.metaLabel}>업데이트</Text>
                  <Text style={styles.metaValue}>
                    {new Date(quote.timestamp).toLocaleTimeString('ko-KR')}
                  </Text>
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
          onPress={() => navigation.navigate('DebateRequest', { symbol: activeSymbol, price: quote?.price })}
        >
          <Text style={styles.debateBtnIcon}>🤖</Text>
          <View>
            <Text style={styles.debateBtnTitle}>AI 토론 분석 요청</Text>
            <Text style={styles.debateBtnSub}>{activeSymbol} 투자 가설을 5인 AI가 검토합니다</Text>
          </View>
          <Text style={styles.debateBtnArrow}>›</Text>
        </TouchableOpacity>
      </ScrollView>
    </View>
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
  searchRow: {
    flexDirection: 'row',
    marginHorizontal: 20,
    marginBottom: 12,
    gap: 8,
  },
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
  chipsRow: { paddingLeft: 20, marginBottom: 20 },
  chip: {
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: '#151B2E',
    marginRight: 8,
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  chipActive: { backgroundColor: '#3D7BFF', borderColor: '#3D7BFF' },
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
  symbol: { fontSize: 14, color: '#8899BB', fontWeight: '600', marginBottom: 8 },
  price: { fontSize: 48, fontWeight: '700', color: '#FFFFFF', marginBottom: 8 },
  changeRow: { flexDirection: 'row', gap: 8, alignItems: 'center', marginBottom: 20 },
  change: { fontSize: 18, fontWeight: '600' },
  changePercent: { fontSize: 16, fontWeight: '500' },
  metaRow: { flexDirection: 'row', gap: 24 },
  metaItem: { gap: 4 },
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
  debateBtnArrow: { marginLeft: 'auto', fontSize: 24, color: '#3D7BFF' },
});
