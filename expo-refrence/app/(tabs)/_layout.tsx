import { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import { Tabs, usePathname } from 'expo-router';

import { AppMenu } from '@/components/app-menu';
import { initDatabase } from '@/db';
import { conversationsRepo } from '@/db/repos';
import { messageEvents } from '@/services/message-events';

const styles = StyleSheet.create({
  iconWrap: {
    width: 24,
    height: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dot: {
    position: 'absolute',
    top: 1,
    right: 1,
    width: 8,
    height: 8,
    borderRadius: 999,
    backgroundColor: '#2563eb',
  },
});

async function getUnreadTotal(): Promise<number> {
  await initDatabase();
  const conversations = await conversationsRepo.list();
  return conversations.reduce((sum, conversation) => sum + conversation.unread_count, 0);
}

export default function TabsLayout() {
  const pathname = usePathname();
  const [showChatsHint, setShowChatsHint] = useState(false);
  const [lastUnreadTotal, setLastUnreadTotal] = useState(0);
  const isChatsTabSelected = useMemo(() => pathname === '/chats', [pathname]);

  const refreshUnreadState = useCallback(async (): Promise<void> => {
    const nextUnreadTotal = await getUnreadTotal();

    setLastUnreadTotal((currentUnreadTotal) => {
      if (!isChatsTabSelected && nextUnreadTotal > currentUnreadTotal) {
        setShowChatsHint(true);
      }

      return nextUnreadTotal;
    });
  }, [isChatsTabSelected]);

  useEffect(() => {
    void refreshUnreadState();
    const unsubscribe = messageEvents.subscribe(() => {
      void refreshUnreadState();
    });

    return unsubscribe;
  }, [refreshUnreadState]);

  useEffect(() => {
    if (!isChatsTabSelected) {
      return;
    }

    setShowChatsHint(false);
    void getUnreadTotal().then(setLastUnreadTotal);
  }, [isChatsTabSelected]);

  return (
    <View className="flex-1">
      <Tabs screenOptions={{ headerShown: false }}>
        <Tabs.Screen
          name="home"
          options={{
            title: 'Home',
            tabBarIcon: ({ color, size }) => <MaterialIcons color={color} name="home" size={size} />,
          }}
        />
        <Tabs.Screen
          name="wallet"
          options={{
            title: 'Wallet',
            tabBarIcon: ({ color, size }) => (
              <MaterialIcons color={color} name="account-balance-wallet" size={size} />
            ),
          }}
        />
        <Tabs.Screen
          name="products"
          options={{
            title: 'Products',
            tabBarIcon: ({ color, size }) => (
              <MaterialIcons color={color} name="storefront" size={size} />
            ),
          }}
        />
        <Tabs.Screen
          name="chats"
          options={{
            title: 'Chats',
            tabBarIcon: ({ color, size }) => (
              <View style={styles.iconWrap}>
                <MaterialIcons color={color} name="chat-bubble-outline" size={size} />
                {showChatsHint ? <View style={styles.dot} /> : null}
              </View>
            ),
          }}
        />
      </Tabs>
      <AppMenu />
    </View>
  );
}
