import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RouteProp } from '@react-navigation/native';
import { RootStackParamList } from '../../App';
import { API } from '../constants/api';

type Props = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'DebateResult'>;
  route: RouteProp<RootStackParamList, 'DebateResult'>;
};

interface DebateReport {
  consensus: string;
  successProbability: number;
  disputes: string[];
  actions: string[];
}

type PollingStatus = 'POLLING' | 'COMPLETED' | 'ERROR';

const POLLING_INTERVAL_MS = 5000;   // 5초 간격 (API 응답 대기 여유)
const POLLING_MAX_ATTEMPTS = 36;    // 36 × 5초 = 최대 3분 (5인 × 30초 여유)

export default function DebateResultScreen({ navigation, route }: Props) {
  const { requestId, symbol, thesis } = route.params;
  const [report, setReport] = useState<DebateReport | null>(null);
  const [pollingStatus, setPollingStatus] = useState<PollingStatus>('POLLING');
  const [attempts, setAttempts] = useState(0);
  const [errorMsg, setErrorMsg] = useState('');
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    // debate-svc에서 requestId로 결과 조회
    // market-svc가 Kafka로 발행 → debate-svc가 소비 후 InMemory 저장
    const poll = async () => {
      try {
        const res = await fetch(`${API.DEBATE_BASE}/debate/${requestId}/report`);

        if (res.ok) {
          const data: DebateReport = await res.json();
          setReport(data);
          setPollingStatus('COMPLETED');
          if (timerRef.current) clearInterval(timerRef.current);
          return;
        }

        // 404/500 = 아직 처리 중
        setAttempts((prev) => {
          const next = prev + 1;
          if (next >= POLLING_MAX_ATTEMPTS) {
            setPollingStatus('ERROR');
            setErrorMsg('분석 시간이 초과되었습니다. 다시 시도해주세요.');
            if (timerRef.current) clearInterval(timerRef.current);
          }
          return next;
        });
      } catch {
        setAttempts((prev) => {
          const next = prev + 1;
          if (next >= POLLING_MAX_ATTEMPTS) {
            setPollingStatus('ERROR');
            setErrorMsg('서버 연결 오류가 발생했습니다.');
            if (timerRef.current) clearInterval(timerRef.current);
          }
          return next;
        });
      }
    };

    // 즉시 1회 실행 후 폴링
    poll();
    timerRef.current = setInterval(poll, POLLING_INTERVAL_MS);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [requestId]);

  const probColor = report
    ? report.successProbability >= 70
      ? '#00C896'
      : report.successProbability >= 40
      ? '#FFA502'
      : '#FF4757'
    : '#3D7BFF';

  return (
    <View style={styles.container}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity style={styles.backBtn} onPress={() => navigation.popToTop()}>
          <Text style={styles.backArrow}>‹</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>AI 분석 결과</Text>
        <View style={styles.headerRight} />
      </View>

      <ScrollView showsVerticalScrollIndicator={false}>
        {/* 요청 정보 */}
        <View style={styles.requestCard}>
          <View style={styles.requestRow}>
            <Text style={styles.requestLabel}>종목</Text>
            <Text style={styles.requestSymbol}>{symbol}</Text>
          </View>
          <Text style={styles.requestThesis} numberOfLines={3}>{thesis}</Text>
          <Text style={styles.requestId}>ID: {requestId.slice(0, 8)}...</Text>
        </View>

        {/* 폴링 중 */}
        {pollingStatus === 'POLLING' && (
          <View style={styles.pollingCard}>
            <ActivityIndicator size="large" color="#3D7BFF" style={styles.spinner} />
            <Text style={styles.pollingTitle}>5인 AI 위원회 분석 중</Text>
            <View style={styles.personaProgress}>
              {['AMODEI', 'ALTMAN', 'MUSK', 'KARPATH', 'EL'].map((name, i) => {
                const completedCount = Math.floor((attempts / POLLING_MAX_ATTEMPTS) * 5);
                return (
                  <View key={name} style={styles.progressItem}>
                    <View
                      style={[
                        styles.progressDot,
                        { backgroundColor: i < completedCount ? '#00C896' : '#2A3250' },
                      ]}
                    />
                    <Text style={styles.progressName}>{name}</Text>
                  </View>
                );
              })}
            </View>
            <Text style={styles.pollingNote}>
              분석 중 · 최대 {Math.round((POLLING_MAX_ATTEMPTS * POLLING_INTERVAL_MS) / 60000)}분 소요
            </Text>
          </View>
        )}

        {/* 오류 */}
        {pollingStatus === 'ERROR' && (
          <View style={styles.errorCard}>
            <Text style={styles.errorEmoji}>⚠️</Text>
            <Text style={styles.errorTitle}>분석 실패</Text>
            <Text style={styles.errorMsg}>{errorMsg}</Text>
            <TouchableOpacity
              style={styles.retryBtn}
              onPress={() => navigation.goBack()}
            >
              <Text style={styles.retryBtnText}>다시 요청하기</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* 결과 */}
        {pollingStatus === 'COMPLETED' && report && (
          <>
            {/* 성공 확률 */}
            <View style={styles.probCard}>
              <Text style={styles.probLabel}>AI 위원회 투자 확률</Text>
              <Text style={[styles.probValue, { color: probColor }]}>
                {report.successProbability}%
              </Text>
              <View style={styles.probBar}>
                <View
                  style={[
                    styles.probFill,
                    { width: `${report.successProbability}%` as any, backgroundColor: probColor },
                  ]}
                />
              </View>
            </View>

            {/* 합의 사항 */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>🤝 위원회 합의</Text>
              <View style={styles.consensusCard}>
                <Text style={styles.consensusText}>{report.consensus}</Text>
              </View>
            </View>

            {/* 다음 액션 */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>📋 권장 액션 3가지</Text>
              {report.actions.map((action, i) => (
                <View key={i} style={styles.actionItem}>
                  <View style={styles.actionNumber}>
                    <Text style={styles.actionNumberText}>{i + 1}</Text>
                  </View>
                  <Text style={styles.actionText}>{action}</Text>
                </View>
              ))}
            </View>

            {/* 쟁점 사항 */}
            {report.disputes.length > 0 && (
              <View style={styles.section}>
                <Text style={styles.sectionTitle}>⚡ 위원회 쟁점</Text>
                {report.disputes.map((d, i) => (
                  <View key={i} style={styles.disputeItem}>
                    <Text style={styles.disputeBullet}>·</Text>
                    <Text style={styles.disputeText}>{d}</Text>
                  </View>
                ))}
              </View>
            )}

            {/* 다시 분석 */}
            <TouchableOpacity
              style={styles.reAnalyzeBtn}
              onPress={() => navigation.popToTop()}
            >
              <Text style={styles.reAnalyzeBtnText}>다른 종목 분석하기</Text>
            </TouchableOpacity>
          </>
        )}

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
  requestCard: {
    marginHorizontal: 20,
    marginBottom: 20,
    backgroundColor: '#151B2E',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  requestRow: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 8 },
  requestLabel: { fontSize: 11, color: '#8899BB' },
  requestSymbol: { fontSize: 18, fontWeight: '700', color: '#FFFFFF' },
  requestThesis: { fontSize: 13, color: '#AABBCC', lineHeight: 20, marginBottom: 8 },
  requestId: { fontSize: 10, color: '#4A5568' },
  pollingCard: {
    marginHorizontal: 20,
    backgroundColor: '#151B2E',
    borderRadius: 20,
    padding: 32,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  spinner: { marginBottom: 20 },
  pollingTitle: { fontSize: 16, fontWeight: '700', color: '#FFFFFF', marginBottom: 24 },
  personaProgress: { flexDirection: 'row', gap: 16, marginBottom: 20 },
  progressItem: { alignItems: 'center', gap: 6 },
  progressDot: { width: 10, height: 10, borderRadius: 5 },
  progressName: { fontSize: 9, color: '#8899BB', fontWeight: '600' },
  pollingNote: { fontSize: 11, color: '#4A5568' },
  errorCard: {
    marginHorizontal: 20,
    backgroundColor: '#1A0E1A',
    borderRadius: 20,
    padding: 32,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#FF475744',
  },
  errorEmoji: { fontSize: 40, marginBottom: 12 },
  errorTitle: { fontSize: 18, fontWeight: '700', color: '#FF4757', marginBottom: 8 },
  errorMsg: { fontSize: 13, color: '#AABBCC', textAlign: 'center', lineHeight: 20, marginBottom: 20 },
  retryBtn: {
    backgroundColor: '#FF4757',
    borderRadius: 12,
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  retryBtnText: { color: '#FFF', fontWeight: '700', fontSize: 14 },
  probCard: {
    marginHorizontal: 20,
    marginBottom: 20,
    backgroundColor: '#151B2E',
    borderRadius: 20,
    padding: 24,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  probLabel: { fontSize: 12, color: '#8899BB', marginBottom: 8 },
  probValue: { fontSize: 56, fontWeight: '700', marginBottom: 16 },
  probBar: {
    width: '100%',
    height: 6,
    backgroundColor: '#2A3250',
    borderRadius: 3,
    overflow: 'hidden',
  },
  probFill: { height: '100%', borderRadius: 3 },
  section: { marginHorizontal: 20, marginBottom: 20 },
  sectionTitle: { fontSize: 13, fontWeight: '700', color: '#8899BB', marginBottom: 12 },
  consensusCard: {
    backgroundColor: '#151B2E',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  consensusText: { fontSize: 14, color: '#FFFFFF', lineHeight: 22 },
  actionItem: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    backgroundColor: '#151B2E',
    borderRadius: 12,
    padding: 14,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#2A3250',
  },
  actionNumber: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#3D7BFF',
    justifyContent: 'center',
    alignItems: 'center',
  },
  actionNumberText: { fontSize: 12, fontWeight: '700', color: '#FFF' },
  actionText: { flex: 1, fontSize: 13, color: '#AABBCC', lineHeight: 20 },
  disputeItem: { flexDirection: 'row', gap: 8, marginBottom: 8 },
  disputeBullet: { fontSize: 18, color: '#FFA502', lineHeight: 22 },
  disputeText: { flex: 1, fontSize: 13, color: '#AABBCC', lineHeight: 20 },
  reAnalyzeBtn: {
    marginHorizontal: 20,
    backgroundColor: '#1A1F35',
    borderRadius: 16,
    padding: 18,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#3D7BFF44',
  },
  reAnalyzeBtnText: { fontSize: 15, fontWeight: '700', color: '#3D7BFF' },
  bottomSpace: { height: 40 },
});
