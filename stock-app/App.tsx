import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import QuotesScreen from './src/screens/QuotesScreen';
import DebateRequestScreen from './src/screens/DebateRequestScreen';
import DebateResultScreen from './src/screens/DebateResultScreen';

export type RootStackParamList = {
  Quotes: undefined;
  DebateRequest: { symbol: string; price?: number };
  DebateResult: { requestId: string; symbol: string; thesis: string };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Quotes" component={QuotesScreen} />
        <Stack.Screen name="DebateRequest" component={DebateRequestScreen} />
        <Stack.Screen name="DebateResult" component={DebateResultScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
