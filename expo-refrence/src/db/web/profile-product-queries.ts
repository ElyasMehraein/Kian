import { persistState, type WebDbState, type WebQueryResult } from './state';

export function handleProfileAndProductQueries(
  normalized: string,
  state: WebDbState,
  params: unknown[],
): WebQueryResult | null {
  if (normalized.includes('insert into profiles')) {
    const [pubkey, unusedName, displayName, about, picture, nip05, geohash, rawJson, createdAt] = params as [
      string,
      string | null,
      string | null,
      string | null,
      string | null,
      string | null,
      string | null,
      string,
      number,
    ];
    void unusedName;
    const updatedAt = Math.floor(Date.now() / 1000);
    const existingIndex = state.profiles.findIndex((profile) => profile.pubkey === pubkey);
    const nextProfile = {
      pubkey,
      name: null,
      display_name: displayName,
      about,
      picture,
      nip05,
      geohash,
      raw_json: rawJson,
      created_at: createdAt,
      updated_at: updatedAt,
    };

    if (existingIndex >= 0) {
      if (createdAt < state.profiles[existingIndex].created_at) {
        return { rows: [] };
      }
      state.profiles[existingIndex] = nextProfile;
    } else {
      state.profiles.push(nextProfile);
    }

    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('select * from profiles where pubkey = ? limit 1')) {
    const [pubkey] = params as [string];
    const profile = state.profiles.find((entry) => entry.pubkey === pubkey);
    return { rows: profile ? [profile] : [] };
  }

  if (normalized.includes('select * from profiles where pubkey in (')) {
    const pubkeys = params as string[];
    return { rows: state.profiles.filter((entry) => pubkeys.includes(entry.pubkey)) };
  }

  if (normalized.includes("select * from profiles where json_extract(raw_json, '$.is_trader') = 1")) {
    const rows = [...state.profiles]
      .filter((entry) => {
        try {
          return (JSON.parse(entry.raw_json) as { is_trader?: boolean }).is_trader === true;
        } catch {
          return false;
        }
      })
      .sort((left, right) => right.updated_at - left.updated_at || left.pubkey.localeCompare(right.pubkey));

    return { rows };
  }

  if (normalized.includes('select * from profiles where display_name like ?')) {
    const [pattern] = params as [string];
    const needle = String(pattern).replace(/%/g, '').toLowerCase();
    const rows = [...state.profiles]
      .filter((entry) => (entry.display_name ?? '').toLowerCase().includes(needle))
      .sort((left, right) => right.updated_at - left.updated_at);

    return { rows };
  }

  if (normalized.includes('insert into products')) {
    const [id, pubkey, name, description, images, categories, geohash, eventId, createdAt] = params as [
      string,
      string,
      string,
      string,
      string,
      string,
      string | null,
      string,
      number,
    ];
    const existingIndex = state.products.findIndex((product) => product.id === id && product.pubkey === pubkey);
    const nextProduct = { id, pubkey, name, description, images, categories, geohash, event_id: eventId, created_at: createdAt };

    if (existingIndex >= 0) {
      state.products[existingIndex] = nextProduct;
    } else {
      state.products.push(nextProduct);
    }

    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('insert into product_categories')) {
    const [id, pubkey, name, parentId, level, createdAt] = params as [
      string,
      string,
      string,
      string | null,
      number,
      number,
    ];
    const existingIndex = state.productCategories.findIndex(
      (category) => category.id === id && category.pubkey === pubkey,
    );
    const nextCategory = {
      id,
      pubkey,
      name,
      parent_id: parentId,
      level,
      created_at: createdAt,
    };

    if (existingIndex >= 0) {
      state.productCategories[existingIndex] = nextCategory;
    } else {
      state.productCategories.push(nextCategory);
    }

    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('select * from product_categories where pubkey = ? order by level asc, created_at asc, name collate nocase asc')) {
    const [pubkey] = params as [string];
    const rows = [...state.productCategories]
      .filter((category) => category.pubkey === pubkey)
      .sort((left, right) => left.level - right.level || left.created_at - right.created_at || left.name.localeCompare(right.name));

    return { rows };
  }

  if (normalized.includes('delete from product_categories where pubkey = ? and id in (')) {
    const [pubkey, ...ids] = params as [string, ...string[]];
    state.productCategories = state.productCategories.filter(
      (category) => category.pubkey !== pubkey || !ids.includes(category.id),
    );

    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('select * from products where pubkey = ?')) {
    const [pubkey] = params as [string];
    const rows = [...state.products]
      .filter((product) => product.pubkey === pubkey)
      .sort((left, right) => right.created_at - left.created_at);

    return { rows };
  }

  if (normalized.includes('select distinct pubkey from products')) {
    const rows = [...new Set(state.products.map((product) => product.pubkey))]
      .sort((left, right) => left.localeCompare(right))
      .map((pubkey) => ({ pubkey }));

    return { rows };
  }

  if (normalized.includes('select * from products where id = ? and pubkey = ? limit 1')) {
    const [id, pubkey] = params as [string, string];
    const product = state.products.find((entry) => entry.id === id && entry.pubkey === pubkey);
    return { rows: product ? [product] : [] };
  }

  if (normalized.includes('delete from products where pubkey = ? and event_id in (')) {
    const [pubkey, ...eventIds] = params as [string, ...string[]];
    state.products = state.products.filter(
      (product) => product.pubkey !== pubkey || !eventIds.includes(product.event_id),
    );

    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('insert into token_definitions')) {
    const [assetId, pubkey, productId, name, description, images, categories, unit, eventId, createdAt] = params as [
      string,
      string,
      string,
      string,
      string,
      string,
      string,
      string,
      string,
      number,
    ];
    const existingIndex = state.tokenDefinitions.findIndex(
      (definition) => definition.asset_id === assetId && definition.pubkey === pubkey,
    );
    const nextDefinition = {
      asset_id: assetId,
      pubkey,
      product_id: productId,
      name,
      description,
      images,
      categories,
      unit,
      event_id: eventId,
      created_at: createdAt,
    };

    if (existingIndex >= 0) {
      state.tokenDefinitions[existingIndex] = nextDefinition;
    } else {
      state.tokenDefinitions.push(nextDefinition);
    }

    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('select * from token_definitions where asset_id = ? and pubkey = ? limit 1')) {
    const [assetId, pubkey] = params as [string, string];
    const definition = state.tokenDefinitions.find(
      (entry) => entry.asset_id === assetId && entry.pubkey === pubkey,
    );
    return { rows: definition ? [definition] : [] };
  }

  if (normalized.includes('select * from token_definitions where pubkey = ? order by created_at desc')) {
    const [pubkey] = params as [string];
    const rows = [...state.tokenDefinitions]
      .filter((definition) => definition.pubkey === pubkey)
      .sort((left, right) => right.created_at - left.created_at);

    return { rows };
  }

  if (normalized.includes('insert or replace into token_utxos')) {
    const [utxoId, assetRef, producer, owner, amount, prevUtxoId, createdAt, spent] = params as [
      string,
      string,
      string,
      string,
      number,
      string | null,
      number,
      number,
    ];
    const existingIndex = state.tokenUtxos.findIndex((utxo) => utxo.utxo_id === utxoId);
    const nextUtxo = {
      utxo_id: utxoId,
      asset_ref: assetRef,
      producer,
      owner,
      amount,
      prev_utxo_id: prevUtxoId,
      created_at: createdAt,
      spent,
    };

    if (existingIndex >= 0) {
      state.tokenUtxos[existingIndex] = nextUtxo;
    } else {
      state.tokenUtxos.push(nextUtxo);
    }

    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('update token_utxos set spent = 1 where utxo_id = ?')) {
    const [utxoId] = params as [string];
    const existingIndex = state.tokenUtxos.findIndex((utxo) => utxo.utxo_id === utxoId);

    if (existingIndex >= 0) {
      state.tokenUtxos[existingIndex] = { ...state.tokenUtxos[existingIndex], spent: 1 };
      persistState(state);
    }

    return { rows: [] };
  }

  if (normalized.includes('select * from token_utxos where owner = ? and spent = 0 order by created_at desc')) {
    const [owner] = params as [string];
    const rows = [...state.tokenUtxos]
      .filter((utxo) => utxo.owner === owner && utxo.spent === 0)
      .sort((left, right) => right.created_at - left.created_at);

    return { rows };
  }

  if (normalized.includes('select * from token_utxos where owner = ? order by created_at desc')) {
    const [owner] = params as [string];
    const rows = [...state.tokenUtxos]
      .filter((utxo) => utxo.owner === owner)
      .sort((left, right) => right.created_at - left.created_at);

    return { rows };
  }

  if (normalized.includes('select * from token_utxos where utxo_id = ? limit 1')) {
    const [utxoId] = params as [string];
    const utxo = state.tokenUtxos.find((entry) => entry.utxo_id === utxoId);
    return { rows: utxo ? [utxo] : [] };
  }

  return null;
}
