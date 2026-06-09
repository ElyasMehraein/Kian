import { StatusBar } from 'expo-status-bar';
import { Platform, Text, View } from 'react-native';

import EditScreenInfo from '@/components/EditScreenInfo';

export default function ModalScreen() {
  return (
    <View className="flex-1 items-center justify-center px-6">
      <Text className="text-ink text-xl font-bold">Modal</Text>
      <View className="my-8 h-px w-4/5 bg-line" />
      <EditScreenInfo path="app/modal.tsx" />
      <StatusBar style={Platform.OS === 'ios' ? 'light' : 'auto'} />
    </View>
  );
}
