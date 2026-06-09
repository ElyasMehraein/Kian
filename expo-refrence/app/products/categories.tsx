import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import { router } from 'expo-router';

import { initDatabase } from '@/db';
import { keysRepo, productCategoriesRepo, productsRepo } from '@/db/repos';
import type { ProductCategory } from '@/types';

const MAX_LEVEL = 5;

function createCategoryId(): string {
  return `category-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function getChildren(categories: ProductCategory[], parentId?: string): ProductCategory[] {
  return categories
    .filter((category) => (category.parent_id ?? undefined) === parentId)
    .sort((left, right) => left.name.localeCompare(right.name));
}

function getBranchIds(categories: ProductCategory[], rootId: string): string[] {
  const ids = [rootId];

  for (const category of categories.filter((entry) => entry.parent_id === rootId)) {
    ids.push(...getBranchIds(categories, category.id));
  }

  return ids;
}

function CategoryNode({
  categories,
  item,
  onAddChild,
  onDelete,
}: {
  categories: ProductCategory[];
  item: ProductCategory;
  onAddChild: (parent: ProductCategory) => void;
  onDelete: (category: ProductCategory) => void;
}) {
  const children = getChildren(categories, item.id);

  return (
    <View className="gap-2">
      <View className="border-line rounded-3xl border bg-white p-4">
        <View className="flex-row items-center gap-3">
          <View className="h-8 w-8 items-center justify-center rounded-lg bg-sky-100">
            <Text className="font-bold text-sky-800">{item.level}</Text>
          </View>
          <View className="flex-1">
            <Text className="text-ink text-base font-semibold">{item.name}</Text>
            <Text className="mt-1 text-xs text-slate-500">{children.length > 0 ? `${children.length} subcategories` : 'No subcategories yet'}</Text>
          </View>
          {item.level < MAX_LEVEL ? (
            <Pressable className="h-10 w-10 items-center justify-center rounded-full bg-emerald-100" onPress={() => onAddChild(item)}>
              <MaterialIcons color="#047857" name="add" size={20} />
            </Pressable>
          ) : null}
          <Pressable className="h-10 w-10 items-center justify-center rounded-full bg-rose-100" onPress={() => onDelete(item)}>
            <MaterialIcons color="#be123c" name="delete-outline" size={20} />
          </Pressable>
        </View>
      </View>
      {children.length > 0 ? (
        <View className="ml-5 gap-2 border-l border-slate-200 pl-3">
          {children.map((child) => (
            <CategoryNode categories={categories} item={child} key={child.id} onAddChild={onAddChild} onDelete={onDelete} />
          ))}
        </View>
      ) : null}
    </View>
  );
}

export default function ProductCategoriesScreen() {
  const [pubkey, setPubkey] = useState<string | null>(null);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [rootName, setRootName] = useState('');
  const [draftParent, setDraftParent] = useState<ProductCategory | null>(null);
  const [childName, setChildName] = useState('');

  const load = useCallback(async (): Promise<void> => {
    await initDatabase();
    const ownerPubkey = await keysRepo.getPublicKey();
    setPubkey(ownerPubkey);
    setCategories(ownerPubkey ? await productCategoriesRepo.listByPubkey(ownerPubkey) : []);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const roots = useMemo(() => getChildren(categories), [categories]);

  async function addCategory(name: string, parent?: ProductCategory): Promise<void> {
    if (!pubkey) {
      return;
    }

    const trimmed = name.trim();

    if (!trimmed) {
      return;
    }

    const level = parent ? parent.level + 1 : 1;

    if (level > MAX_LEVEL) {
      Alert.alert('Maximum depth reached', 'Categories can only go 5 levels deep.');
      return;
    }

    await productCategoriesRepo.upsert({
      id: createCategoryId(),
      pubkey,
      name: trimmed,
      parent_id: parent?.id,
      level,
      created_at: Math.floor(Date.now() / 1000),
    });
    setRootName('');
    setChildName('');
    setDraftParent(null);
    await load();
  }

  async function handleDelete(category: ProductCategory): Promise<void> {
    if (!pubkey) {
      return;
    }

    const branchIds = getBranchIds(categories, category.id);
    const products = await productsRepo.getByProducer(pubkey);
    const assignedProducts = products.filter((product) => product.categories.some((categoryId) => branchIds.includes(categoryId)));

    if (assignedProducts.length > 0) {
      Alert.alert('Category in use', 'Remove this category from your products before deleting this branch.');
      return;
    }

    await productCategoriesRepo.deleteBranch(pubkey, branchIds);
    if (draftParent && branchIds.includes(draftParent.id)) {
      setDraftParent(null);
      setChildName('');
    }
    await load();
  }

  return (
    <ScrollView className="bg-canvas flex-1" contentContainerClassName="px-5 pb-10 pt-14">
      <View className="mb-6 flex-row items-center justify-between gap-3">
        <View className="flex-1">
          <Text className="text-ink text-[28px] font-bold">Category management</Text>
          <Text className="mt-1.5 text-sm leading-6 text-slate-500">Build your category tree up to 5 levels deep, then use it on the products page.</Text>
        </View>
        <Pressable className="h-11 w-11 items-center justify-center rounded-full bg-white" onPress={() => router.back()}>
          <MaterialIcons color="#0f172a" name="close" size={22} />
        </Pressable>
      </View>

      <View className="mb-4 rounded-3xl bg-white p-4">
        <Text className="text-ink text-base font-semibold">Add root category</Text>
        <View className="mt-3 flex-row gap-2">
          <TextInput className="border-line text-ink flex-1 rounded-2xl border px-4 py-3" onChangeText={setRootName} placeholder="New root category name" placeholderTextColor="#94a3b8" value={rootName} />
          <Pressable className="bg-ink items-center justify-center rounded-2xl px-4" onPress={() => void addCategory(rootName)}>
            <Text className="font-semibold text-white">Add</Text>
          </Pressable>
        </View>
      </View>

      {draftParent ? (
        <View className="mb-4 rounded-3xl bg-sky-50 p-4">
          <Text className="text-sm font-semibold text-sky-900">Add subcategory under {draftParent.name}</Text>
          <View className="mt-3 flex-row gap-2">
            <TextInput className="border-line text-ink flex-1 rounded-2xl border bg-white px-4 py-3" onChangeText={setChildName} placeholder="Subcategory name" placeholderTextColor="#94a3b8" value={childName} />
            <Pressable className="bg-ink items-center justify-center rounded-2xl px-4" onPress={() => void addCategory(childName, draftParent)}>
              <Text className="font-semibold text-white">Save</Text>
            </Pressable>
          </View>
        </View>
      ) : null}

      <View className="gap-3">
        {roots.length === 0 ? (
          <View className="rounded-3xl bg-white px-4 py-6">
            <Text className="text-ink text-base font-semibold">No categories yet</Text>
            <Text className="mt-2 text-sm leading-6 text-slate-500">Start by adding a root category, then grow the tree with subcategories.</Text>
          </View>
        ) : (
          roots.map((item) => (
            <CategoryNode categories={categories} item={item} key={item.id} onAddChild={setDraftParent} onDelete={(category) => void handleDelete(category)} />
          ))
        )}
      </View>
    </ScrollView>
  );
}
