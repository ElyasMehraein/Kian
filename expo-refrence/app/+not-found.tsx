import { Link, Stack } from 'expo-router';
import { Text, View } from 'react-native';

export default function NotFoundScreen() {
  return (
    <>
      <Stack.Screen options={{ title: 'Oops!' }} />
      <View className="flex-1 items-center justify-center px-5">
        <Text className="text-ink text-xl font-bold">This screen doesn't exist.</Text>
        <Link className="mt-4 py-4" href="/">
          <Text className="text-sm text-sky-600">Go to home screen!</Text>
        </Link>
      </View>
    </>
  );
}
