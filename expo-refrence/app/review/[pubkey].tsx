import { useEffect, useMemo, useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';

import { buildReviewPage } from '@/builders';
import { signEvent } from '@/crypto';
import { initDatabase } from '@/db';
import { keysRepo, reviewsRepo } from '@/db/repos';
import { RelayPool } from '@/nostr';
import type { ReviewEntry } from '@/types';

const relayPool = new RelayPool();
let isConnected = false;

async function ensureConnected(): Promise<void> {
  if (!isConnected) {
    await relayPool.connect();
    isConnected = true;
  }
}

function createReviewEntry(authorPubkey: string, targetPubkey: string, rating: number, comment: string): ReviewEntry {
  return { pubkey: authorPubkey, target_pubkey: targetPubkey, rating, comment, created_at: Math.floor(Date.now() / 1000) };
}

export default function ReviewScreen() {
  const params = useLocalSearchParams<{ pubkey?: string | string[] }>();
  const targetPubkey = useMemo(() => {
    const value = params.pubkey;
    return Array.isArray(value) ? value[0] ?? '' : value ?? '';
  }, [params.pubkey]);
  const [rating, setRating] = useState('5');
  const [comment, setComment] = useState('');
  const [authorPubkey, setAuthorPubkey] = useState<string | null>(null);

  useEffect(() => {
    if (!targetPubkey) {
      return;
    }

    async function loadReview(): Promise<void> {
      await initDatabase();
      const pubkey = await keysRepo.getPublicKey();
      setAuthorPubkey(pubkey);
      if (!pubkey) {
        return;
      }
      const review = await reviewsRepo.getByAuthorAndTarget(pubkey, targetPubkey);
      if (review) {
        setRating(String(review.rating));
        setComment(review.comment);
      }
    }

    void loadReview();
  }, [targetPubkey]);

  async function handleSave(): Promise<void> {
    const nextRating = Number(rating);
    const nextComment = comment.trim();
    if (!authorPubkey || !targetPubkey || nextRating < 1 || nextRating > 5) {
      return;
    }
    const privkey = await keysRepo.getPrivateKey();
    if (!privkey) {
      return;
    }

    const review = createReviewEntry(authorPubkey, targetPubkey, nextRating, nextComment);
    const signed = signEvent({ ...buildReviewPage([review], 0), pubkey: authorPubkey, created_at: review.created_at }, privkey);
    await ensureConnected();
    relayPool.publish(signed);
    await reviewsRepo.upsert(review);
  }

  return (
    <View className="bg-canvas flex-1 px-5 pt-14">
      <Text className="text-ink text-[28px] font-bold">Review merchant</Text>
      <Text className="mt-1.5 mb-4 text-slate-500">{targetPubkey || 'Missing merchant pubkey'}</Text>
      <TextInput
        className="border-line mb-2.5 rounded-xl border px-3 py-2.5"
        keyboardType="numeric"
        onChangeText={setRating}
        placeholder="Rating 1-5"
        value={rating}
      />
      <TextInput
        className="border-line mb-2.5 min-h-[120px] rounded-xl border px-3 py-2.5"
        multiline
        onChangeText={setComment}
        placeholder="Write your review"
        style={{ textAlignVertical: 'top' }}
        value={comment}
      />
      <Pressable className="bg-ink items-center rounded-xl py-3" onPress={() => void handleSave()}>
        <Text className="font-semibold text-white">Save review</Text>
      </Pressable>
    </View>
  );
}
