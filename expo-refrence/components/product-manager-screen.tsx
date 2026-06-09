import { useCallback, useEffect, useMemo, useState } from 'react';
import { FlatList, Pressable, Text, TextInput, View } from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import { router, useFocusEffect } from 'expo-router';

import { buildDeleteEvent, buildProduct } from '@/builders';
import { signEvent } from '@/crypto';
import { initDatabase } from '@/db';
import { keysRepo, productCategoriesRepo, productsRepo } from '@/db/repos';
import { RelayPool } from '@/nostr';
import { productEvents } from '@/services';
import type { Product, ProductCategory } from '@/types';

type Draft = {
  id: string | null;
  name: string;
  description: string;
  imageUrls: string;
  categories: string[];
};

const emptyDraft: Draft = {
  id: null,
  name: '',
  description: '',
  imageUrls: '',
  categories: [],
};
const relayPool = new RelayPool();
let isConnected = false;

function createProductId(): string {
  return `product-${Date.now()}`;
}

async function ensureConnected(): Promise<void> {
  if (!isConnected) {
    await relayPool.connect();
    isConnected = true;
  }
}

function parseImageUrls(value: string): string[] {
  return value.split('\n').map((item) => item.trim()).filter(Boolean);
}

function getChildren(categories: ProductCategory[], parentId?: string): ProductCategory[] {
  return categories
    .filter((category) => (category.parent_id ?? undefined) === parentId)
    .sort((left, right) => left.name.localeCompare(right.name));
}

function getCategoryPath(categories: ProductCategory[], leafId: string): ProductCategory[] {
  const byId = new Map(categories.map((category) => [category.id, category]));
  const path: ProductCategory[] = [];
  let current = byId.get(leafId);

  while (current) {
    path.unshift(current);
    current = current.parent_id ? byId.get(current.parent_id) : undefined;
  }

  return path;
}

function getBranchIds(categories: ProductCategory[], rootId: string): string[] {
  const ids = [rootId];

  for (const category of categories.filter((entry) => entry.parent_id === rootId)) {
    ids.push(...getBranchIds(categories, category.id));
  }

  return ids;
}

function ProductRow({
  categoryName,
  item,
  onDelete,
  onEdit,
}: {
  categoryName: string | null;
  item: Product;
  onDelete: (product: Product) => void;
  onEdit: (product: Product) => void;
}) {
  return (
    <View className="border-line bg-panel rounded-xl border p-3.5">
      <Pressable onPress={() => onEdit(item)}>
        <Text className="text-ink text-base font-semibold">{item.name}</Text>
        <Text className="mt-1.5 text-sm text-slate-600">{item.description || 'No description'}</Text>
        {categoryName ? <Text className="mt-2 text-xs font-semibold uppercase tracking-[0.14em] text-sky-700">{categoryName}</Text> : null}
        <Text className="text-accent mt-2 text-[13px] font-semibold">Tap to edit</Text>
      </Pressable>
      <Pressable className="mt-3 self-start rounded-xl bg-rose-100 px-3.5 py-2.5" onPress={() => onDelete(item)}>
        <Text className="font-semibold text-rose-700">Delete product</Text>
      </Pressable>
    </View>
  );
}

function CategoryFilterBar({
  categories,
  path,
  onChange,
}: {
  categories: ProductCategory[];
  path: ProductCategory[];
  onChange: (next: ProductCategory[]) => void;
}) {
  const levels = Array.from({ length: 5 }, (_, index) => {
    const parentId = index === 0 ? undefined : path[index - 1]?.id;
    return getChildren(categories, parentId);
  }).filter((level, index) => index === 0 || path[index - 1]);

  return (
    <View className="mb-4 gap-3">
      <View className="flex-row flex-wrap gap-2">
        <Pressable className={path.length === 0 ? 'rounded-full bg-ink px-3 py-2' : 'rounded-full bg-white px-3 py-2'} onPress={() => onChange([])}>
          <Text className={path.length === 0 ? 'font-semibold text-white' : 'font-semibold text-slate-700'}>All</Text>
        </Pressable>
        {path.map((category, index) => (
          <Pressable
            className="rounded-full bg-sky-100 px-3 py-2"
            key={category.id}
            onPress={() => onChange(path.slice(0, index + 1))}
          >
            <Text className="font-semibold text-sky-800">{category.name}</Text>
          </Pressable>
        ))}
      </View>
      {levels.map((options, index) => (
        <FlatList
          contentContainerClassName="gap-2"
          data={options}
          horizontal
          key={`level-${index + 1}`}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => {
            const active = path[index]?.id === item.id;
            return (
              <Pressable
                className={active ? 'rounded-full bg-emerald-100 px-3 py-2' : 'rounded-full bg-white px-3 py-2'}
                onPress={() => onChange([...path.slice(0, index), item])}
              >
                <Text className={active ? 'font-semibold text-emerald-800' : 'font-semibold text-slate-700'}>{item.name}</Text>
              </Pressable>
            );
          }}
          showsHorizontalScrollIndicator={false}
        />
      ))}
    </View>
  );
}

function DraftCategoryPicker({
  categories,
  selectedIds,
  onChange,
}: {
  categories: ProductCategory[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
}) {
  const selectedPath = useMemo(
    () => (selectedIds.length > 0 ? getCategoryPath(categories, selectedIds[selectedIds.length - 1]) : []),
    [categories, selectedIds],
  );

  const levels = Array.from({ length: 5 }, (_, index) => {
    const parentId = index === 0 ? undefined : selectedPath[index - 1]?.id;
    return getChildren(categories, parentId);
  }).filter((level, index) => index === 0 || selectedPath[index - 1]);

  return (
    <View className="mb-2.5">
      <View className="mb-2 flex-row items-center justify-between">
        <Text className="text-ink text-sm font-semibold">Category</Text>
        <Pressable className="rounded-full bg-sky-100 px-3 py-1.5" onPress={() => router.push('/products/categories')}>
          <Text className="font-semibold text-sky-800">Manage categories</Text>
        </Pressable>
      </View>
      {categories.length === 0 ? (
        <View className="rounded-2xl bg-slate-100 px-3 py-3">
          <Text className="text-sm leading-5 text-slate-600">Create categories first, then assign a product into that tree.</Text>
        </View>
      ) : (
        <View className="gap-2">
          <View className="flex-row flex-wrap gap-2">
            <Pressable className={selectedPath.length === 0 ? 'rounded-full bg-ink px-3 py-2' : 'rounded-full bg-slate-100 px-3 py-2'} onPress={() => onChange([])}>
              <Text className={selectedPath.length === 0 ? 'font-semibold text-white' : 'font-semibold text-slate-700'}>No category</Text>
            </Pressable>
            {selectedPath.map((category, index) => (
              <Pressable key={category.id} className="rounded-full bg-sky-100 px-3 py-2" onPress={() => onChange(selectedPath.slice(0, index + 1).map((item) => item.id))}>
                <Text className="font-semibold text-sky-800">{category.name}</Text>
              </Pressable>
            ))}
          </View>
          {levels.map((options, index) => (
            <FlatList
              contentContainerClassName="gap-2"
              data={options}
              horizontal
              key={`draft-level-${index + 1}`}
              keyExtractor={(item) => item.id}
              renderItem={({ item }) => {
                const active = selectedPath[index]?.id === item.id;
                return (
                  <Pressable
                    className={active ? 'rounded-full bg-emerald-100 px-3 py-2' : 'rounded-full bg-slate-100 px-3 py-2'}
                    onPress={() => onChange([...selectedPath.slice(0, index), item].map((entry) => entry.id))}
                  >
                    <Text className={active ? 'font-semibold text-emerald-800' : 'font-semibold text-slate-700'}>{item.name}</Text>
                  </Pressable>
                );
              }}
              showsHorizontalScrollIndicator={false}
            />
          ))}
        </View>
      )}
    </View>
  );
}

export function ProductManagerScreen() {
  const [draft, setDraft] = useState<Draft>(emptyDraft);
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [pubkey, setPubkey] = useState<string | null>(null);
  const [isComposerOpen, setIsComposerOpen] = useState(false);
  const [selectedFilterPath, setSelectedFilterPath] = useState<ProductCategory[]>([]);

  const loadProducts = useCallback(async (): Promise<void> => {
    await initDatabase();
    const ownerPubkey = await keysRepo.getPublicKey();
    setPubkey(ownerPubkey);

    if (!ownerPubkey) {
      setProducts([]);
      setCategories([]);
      return;
    }

    const [nextProducts, nextCategories] = await Promise.all([
      productsRepo.getByProducer(ownerPubkey),
      productCategoriesRepo.listByPubkey(ownerPubkey),
    ]);
    setProducts(nextProducts);
    setCategories(nextCategories);
  }, []);

  useEffect(() => {
    void loadProducts();
  }, [loadProducts]);

  useFocusEffect(
    useCallback(() => {
      void loadProducts();
    }, [loadProducts]),
  );

  function updateDraft(patch: Partial<Draft>): void {
    setDraft((current) => ({ ...current, ...patch }));
  }

  function handleEdit(product: Product): void {
    setDraft({
      id: product.id,
      name: product.name,
      description: product.description,
      imageUrls: product.images.join('\n'),
      categories: product.categories,
    });
    setIsComposerOpen(true);
  }

  function handleCreate(): void {
    setDraft(emptyDraft);
    setIsComposerOpen(true);
  }

  async function handleSave(): Promise<void> {
    const name = draft.name.trim();

    if (!pubkey || !name) {
      return;
    }

    const privkey = await keysRepo.getPrivateKey();

    if (!privkey) {
      return;
    }

    const product: Product = {
      id: draft.id ?? createProductId(),
      pubkey,
      name,
      description: draft.description.trim(),
      images: parseImageUrls(draft.imageUrls),
      categories: draft.categories,
      created_at: Math.floor(Date.now() / 1000),
      event_id: '',
    };
    const signed = signEvent(buildProduct(product), privkey);

    await productsRepo.upsert({ ...product, event_id: signed.id });
    productEvents.emit();
    await ensureConnected();
    relayPool.publish(signed);
    setDraft(emptyDraft);
    setIsComposerOpen(false);
    await loadProducts();
  }

  async function handleDelete(product: Product): Promise<void> {
    if (!pubkey) {
      return;
    }

    const privkey = await keysRepo.getPrivateKey();

    if (!privkey) {
      return;
    }

    await productsRepo.deleteByEventIds(pubkey, [product.event_id]);
    productEvents.emit();
    await ensureConnected();
    relayPool.publish(signEvent(buildDeleteEvent([product.event_id], pubkey), privkey));
    setDraft((current) => (current.id === product.id ? emptyDraft : current));
    if (draft.id === product.id) {
      setIsComposerOpen(false);
    }
    await loadProducts();
  }

  const categoryNameById = useMemo(
    () => new Map(categories.map((category) => [category.id, category.name])),
    [categories],
  );
  const filteredProducts = useMemo(() => {
    const selectedLeaf = selectedFilterPath[selectedFilterPath.length - 1];

    if (!selectedLeaf) {
      return products;
    }

    const allowed = new Set(getBranchIds(categories, selectedLeaf.id));
    return products.filter((product) => product.categories.some((categoryId) => allowed.has(categoryId)));
  }, [categories, products, selectedFilterPath]);

  return (
    <View className="bg-canvas flex-1 px-5 pt-14">
      <Text className="text-ink text-[28px] font-bold">Manage products</Text>
      <Text className="mb-4 mt-1.5 text-slate-500">{pubkey ? 'Tap the + button to create a product, edit one from the list, or filter by category.' : 'Create keys first.'}</Text>
      <CategoryFilterBar categories={categories} path={selectedFilterPath} onChange={setSelectedFilterPath} />
      <FlatList
        contentContainerClassName="gap-2.5 pb-24"
        data={filteredProducts}
        keyExtractor={(item) => `${item.pubkey}-${item.id}`}
        ListEmptyComponent={<Text className="text-[15px] text-slate-500">{products.length === 0 ? 'No products yet.' : 'No products found in this category.'}</Text>}
        renderItem={({ item }) => (
          <ProductRow
            categoryName={item.categories[0] ? categoryNameById.get(item.categories[item.categories.length - 1]) ?? null : null}
            item={item}
            onDelete={(product) => void handleDelete(product)}
            onEdit={handleEdit}
          />
        )}
      />
      {isComposerOpen ? (
        <View className="border-line absolute inset-x-4 bottom-6 rounded-[28px] border bg-white p-4 shadow-sm">
          <View className="mb-3 flex-row items-center justify-between">
            <View className="flex-1 pr-3">
              <Text className="text-ink text-lg font-semibold">{draft.id ? 'Edit product' : 'Create product'}</Text>
              <Text className="mt-1 text-sm text-slate-500">
                {draft.id ? 'Update the details for this product.' : 'Add a new product to your list.'}
              </Text>
            </View>
            <Pressable className="bg-slate-100 h-10 w-10 items-center justify-center rounded-full" onPress={() => { setDraft(emptyDraft); setIsComposerOpen(false); }}>
              <MaterialIcons color="#334155" name="close" size={20} />
            </Pressable>
          </View>
          <DraftCategoryPicker categories={categories} selectedIds={draft.categories} onChange={(next) => updateDraft({ categories: next })} />
          <TextInput className="border-line mb-2.5 rounded-xl border px-3 py-2.5" onChangeText={(name) => updateDraft({ name })} placeholder="Product name" value={draft.name} />
          <TextInput className="border-line mb-2.5 min-h-[88px] rounded-xl border px-3 py-2.5" multiline onChangeText={(description) => updateDraft({ description })} placeholder="Description" style={{ textAlignVertical: 'top' }} value={draft.description} />
          <TextInput autoCapitalize="none" autoCorrect={false} className="border-line mb-2.5 min-h-[88px] rounded-xl border px-3 py-2.5" multiline onChangeText={(imageUrls) => updateDraft({ imageUrls })} placeholder="Hosted image URLs, one per line" style={{ textAlignVertical: 'top' }} value={draft.imageUrls} />
          <View className="flex-row gap-2">
            <Pressable className="flex-1 items-center rounded-xl bg-sky-100 py-3" onPress={() => router.push('/products/categories')}>
              <Text className="font-semibold text-sky-800">Category management</Text>
            </Pressable>
            <Pressable className="bg-ink flex-1 items-center rounded-xl py-3" onPress={() => void handleSave()}>
              <Text className="font-semibold text-white">{draft.id ? 'Update product' : 'Create product'}</Text>
            </Pressable>
          </View>
        </View>
      ) : null}
      {pubkey ? (
        <Pressable className="bg-ink absolute bottom-6 right-5 h-14 w-14 items-center justify-center rounded-full" onPress={handleCreate}>
          <MaterialIcons color="#ffffff" name="add" size={28} />
        </Pressable>
      ) : null}
    </View>
  );
}
