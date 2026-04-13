import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TextInput,
  ScrollView,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RouteProp } from '@react-navigation/native';
import { RootStackParamList } from '../../App';
import { API } from '../constants/api';

type Props = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'DebateRequest'>;
  route: RouteProp<RootStackParamList, 'DebateRequest'>;
};

const THESIS_TEMPLATES = [
  '이 종목은 AI 반도체 수요 급증으로 향후 6개월간 30% 이상 상승할 것이다.',
  '현재 고평가 상태로 단기 조정이 예상되므로 매도 포지션이 유리하다.',
  '실적 개선과 시장 점유율 확대로 장기 보유 전략이 최적이다.',
];

export default function DebateRequestScreen({ navigation, route }: Props) {
  const { symbol, price } = route.params;
  const [thesis, setThesis] = useState('');
  const [userId] = useState('user-001');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    if (!thesis.trim()) {
      Alert.alert('입력 오류', '투자 가설을 입력해주세요.');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch(`${API.MARKET_BASE}/debate/request`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          symbol,
          thesis: thesis.trim(),
          userId,
        }),
      });

      if (!res.ok) {
        throw new Error(`서버 오류: ${res.status}`);
      }

      const data = await res.json();
      navigation.navigate('DebateResult', {
        requestId: data.requestId,
        symbol,
        thesis: thesis.trim(),
      });
    } catch (e: any) {
      Alert.alert('요청 실패', e.message || '서버에 연결할 수 없습니다.\n백엔드 서비스가 실행 중인지 확인하세요.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()}>
          <Text style={styles.backArrow}>‹</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>AI 토론 요청</Text>
        <View style={styles.headerRight} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false} style={styles.scroll}>
        {/* 종목 정보 */}
        <View style={styles.symbolCard}>
          <View>
            <Text style={styles.symbolLabel}>분석 대상</Text>
            <Text style={styles.symbolText}>{symbol}</Text>
          </View>
          {price !== undefined && (
            <View style={styles.priceBox}>
              <Text style={styles.priceLabel}>현재가</Text>
              <Text style={styles.priceText}>${price.toFixed(2)}</Text>
            </View>
          )}
        </View>

        {/* AI 페르소나 소개 */}
        <View style={styles.personaSection}>
          <Text style={styles.sectionTitle}>🤖 5인 AI 위원회가 검토합니다</Text>
          <View style={styles.personaRow}>
            {[
              { name: 'MUSK', role: '반론 전문', emoji: '⚡' },
              { name: 'ALTMAN', role: '전략 분석', emoji: '🧠' },
              { name: 'AMODEI', role: '리스크 설계', emoji: '🔬' },
              { name: 'KARPATH', role: 'QA 검증', emoji: '✅' },
              { name: 'EL', role: '합의 도출', emoji: '📊' },
            ].map((p) => (
              <View key={p.name} style={styles.personaCard}>
                <Text style={styles.personaEmoji}>{p.emoji}</Text>
                <Text style={styles.personaName}>{p.name}</Text>
                <Text style={styles.personaRole}>{p.role}</Text>
              </View>
            ))}
          </View>
        </View>

        {/* 가설 입력 */}
        <View style={styles.inputSection}>
          <Text style={styles.sectionTitle}>💡 투자 가설 입력</Text>
          <TextInput
            style={styles.thesisInput}
            value={thesis}
            onChangeText={setThesis}
            placeholder="예) AAPL은 AI 수요 증가로 향후 상승할 것이다."
            placeholderTextColor="#4A5568"
            multiline
            numberOfLines={4}
            textAlignVertical="top"
            autoCapitalize="none"      // ✅ 한글 입력 픽스
            autoCorrect={false}         // ✅ 자동 교정 비활성화
            keyboardType="default"      // ✅ 기본 키보드 (한/영 전환 가능)
          />
          <Text style={styles.charCount}>{thesis.length} / 200</Text>
        </View>

        {/* 템플릿 */}
        <View style={styles.templateSection}>
          <Text style={styles.templateTitle}>빠른 선택</Text>
          {THESIS_TEMPLATES.map((t, i) => (
            <TouchableOpacity
              key={i}
              style={styles.templateItem}
              onPress={() => setThesis(t.replace('이 종목', symbol))}
            >
              <Text style={styles.templateText} numberOfLines={2}>
                {t.replace('이 종목', symbol)}
              </Text>
              <Text style={styles.templateArrow}>›</Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* 제출 */}
        <TouchableOpacity
          style={[styles.submitBtn, (!thesis.trim() || loading) && styles.submitBtnDisabled]}
          onPress={handleSubmit}
          disabled={!thesis.trim() || loading}
        >
          {loading ? (
            <ActivityIndicator color="#FFF" />
          ) : (
            <>
              <Text style={styles.submitBtnText}>AI 토론 시작</Text>
              <Text style={styles.submitBtnSub}>5인 위원회 분석 · 약 2분 소요</Text>
            </>
          )}
        </TouchableOpacity>

        <View style={styles.bottomSpace} />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0A0E1A' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 56,
    paddingBottom: 16,
  },
  backBtn: { width: 40, height: 40, justifyContent: 'center' },
  backArrow: { fontSize: 32, color: '#FFFFFF', lineHeight: 36 },
  headerTitle: { flex: 1, textAlign: 'center', fontSize: 17, fontWeight: '700', color: '#FFFFFF' },
  headerRight: { width: 40 },
  scroll: { flex: 1 },
  symbolCard: {
    marginHorizontal: 20,
    marginBottom: 20,
    backgroundColor: '#151B2E',
    borderRadius: 16,
    padding: 20,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  symbolLabel: { fontSize: 11, color: '#8899BB', marginBottom: 4 },
  symbolText: { fontSize: 28, fontWeight: '700', color: '#FFFFFF' },
  priceBox: { alignItems: 'flex-end' },
  priceLabel: { fontSize: 11, color: '#8899BB', marginBottom: 4 },
  priceText: { fontSize: 20, fontWeight: '600', color: '#00C896' },
  personaSection: { marginHorizontal: 20, marginBottom: 20 },
  sectionTitle: { fontSize: 14, fontWeight: '700', color: '#8899BB', marginBottom: 12 },
  personaRow: { flexDirection: 'row', gap: 8 },
  personaCard: {
    flex: 1,
    backgroundColor: '#151B2E',
    borderRadius: 12,
    padding: 10,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  personaEmoji: { fontSize: 18, marginBottom: 4 },
  personaName: { fontSize: 10, fontWeight: '700', color: '#3D7BFF', marginBottom: 2 },
  personaRole: { fontSize: 9, color: '#8899BB', textAlign: 'center' },
  inputSection: { marginHorizontal: 20, marginBottom: 16 },
  thesisInput: {
    backgroundColor: '#151B2E',
    borderRadius: 16,
    padding: 16,
    color: '#FFFFFF',
    fontSize: 14,
    lineHeight: 22,
    borderWidth: 1,
    borderColor: '#2A3250',
    minHeight: 100,
    marginBottom: 6,
  },
  charCount: { textAlign: 'right', fontSize: 11, color: '#4A5568' },
  templateSection: { marginHorizontal: 20, marginBottom: 24 },
  templateTitle: { fontSize: 12, color: '#8899BB', fontWeight: '600', marginBottom: 10 },
  templateItem: {
    backgroundColor: '#151B2E',
    borderRadius: 12,
    padding: 14,
    marginBottom: 8,
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  templateText: { flex: 1, fontSize: 12, color: '#AABBCC', lineHeight: 18 },
  templateArrow: { fontSize: 20, color: '#3D7BFF', marginLeft: 8 },
  submitBtn: {
    marginHorizontal: 20,
    backgroundColor: '#3D7BFF',
    borderRadius: 16,
    paddingVertical: 18,
    alignItems: 'center',
  },
  submitBtnDisabled: { backgroundColor: '#1E2844', opacity: 0.6 },
  submitBtnText: { fontSize: 16, fontWeight: '700', color: '#FFFFFF', marginBottom: 2 },
  submitBtnSub: { fontSize: 11, color: '#AACBFF' },
  bottomSpace: { height: 40 },
});
