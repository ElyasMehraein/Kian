import '../global.css';
import { Stack } from 'expo-router';

export default function RootLayout() {
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="index" />
      <Stack.Screen name="onboarding" />
      <Stack.Screen name="(tabs)" />
      <Stack.Screen name="chat/[pubkey]" />
      <Stack.Screen name="chat/send-product/[pubkey]" />
      <Stack.Screen name="tokens/send" />
      <Stack.Screen name="user/[pubkey]" />
      <Stack.Screen name="review/[pubkey]" />
      <Stack.Screen name="cart" />
      <Stack.Screen name="relay-status" />
      <Stack.Screen name="pending-events" />
      <Stack.Screen name="private-key" />
      <Stack.Screen name="products/manage" />
      <Stack.Screen name="products/categories" />
      <Stack.Screen name="profile/manage" />
    </Stack>
  );
}
