import { DEFAULT_RELAYS } from '@/types';
import type { ClientMessage, NostrEvent, NostrFilter } from '@/types';

export interface RelayConnection {
  url: string;
  socket: WebSocket;
  connected: boolean;
}
export type RelayStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'disconnected';

export interface RelayState {
  url: string;
  status: RelayStatus;
  connected: boolean;
  lastError?: string;
  reconnectAt?: number;
}

type Subscription = {
  filters: NostrFilter[];
  onEvent: (event: NostrEvent) => void;
  relayUrls: string[];
};
type StateListener = (states: RelayState[]) => void;

function createSubscriptionId(): string {
  return Math.random().toString(36).slice(2, 12);
}

function serializeMessage(message: ClientMessage): string {
  return JSON.stringify(message);
}

export class RelayPool {
  private relayUrls: string[];
  private readonly connections = new Map<string, RelayConnection>();
  private readonly states = new Map<string, RelayState>();
  private readonly subscriptions = new Map<string, Subscription>();
  private readonly pendingPublishes = new Map<string, string[]>();
  private readonly reconnectTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private readonly listeners = new Set<StateListener>();
  constructor(relayUrls: string[] = DEFAULT_RELAYS) {
    this.relayUrls = [...new Set(relayUrls)];

    for (const url of this.relayUrls) {
      this.states.set(url, { url, status: 'idle', connected: false });
    }
  }
  async connect(relayUrls: string[] = this.relayUrls): Promise<void> {
    this.ensureRelayUrls(relayUrls);
    await Promise.all(relayUrls.map(async (url) => this.connectRelay(url)));
  }
  getRelayStates(relayUrls: string[] = this.relayUrls): RelayState[] {
    this.ensureRelayUrls(relayUrls);
    return relayUrls.map(
      (url) => this.states.get(url) ?? { url, status: 'idle', connected: false },
    );
  }

  onStateChange(listener: StateListener): () => void {
    this.listeners.add(listener);
    listener(this.getRelayStates());
    return () => {
      this.listeners.delete(listener);
    };
  }
  publish(event: NostrEvent, relayUrls: string[] = this.relayUrls): void {
    this.ensureRelayUrls(relayUrls);
    const payload = serializeMessage(['EVENT', event]);

    for (const relayUrl of relayUrls) {
      const connection = this.connections.get(relayUrl);

      if (connection?.connected) {
        connection.socket.send(payload);
        continue;
      }

      const queue = this.pendingPublishes.get(relayUrl) ?? [];
      queue.push(payload);
      this.pendingPublishes.set(relayUrl, queue);
      void this.connect([relayUrl]);
    }
  }
  subscribe(
    filters: NostrFilter[],
    onEvent: (event: NostrEvent) => void,
    relayUrls: string[] = this.relayUrls,
  ): string {
    this.ensureRelayUrls(relayUrls);
    const subId = createSubscriptionId();
    this.subscriptions.set(subId, { filters, onEvent, relayUrls: [...relayUrls] });
    void this.connect(relayUrls);
    this.broadcast(['REQ', subId, ...filters], relayUrls);
    return subId;
  }
  unsubscribe(subId: string): void {
    const subscription = this.subscriptions.get(subId);
    this.subscriptions.delete(subId);
    this.broadcast(['CLOSE', subId], subscription?.relayUrls);
  }
  private async connectRelay(url: string): Promise<void> {
    if (this.connections.has(url)) {
      return;
    }
    this.updateState(url, { status: 'connecting', connected: false });
    const socket = new WebSocket(url);
    const connection: RelayConnection = { url, socket, connected: false };
    this.connections.set(url, connection);
    socket.onopen = () => {
      connection.connected = true;
      this.clearReconnect(url);
      this.updateState(url, {
        status: 'connected',
        connected: true,
        lastError: undefined,
        reconnectAt: undefined,
      });
      this.flushPendingPublishes(connection);
      this.resubscribe(connection);
    };

    socket.onmessage = (message) => {
      this.handleMessage(String(message.data));
    };
    socket.onerror = () => {
      connection.connected = false;
      this.updateState(url, {
        status: this.reconnectTimers.has(url) ? 'reconnecting' : 'disconnected',
        connected: false,
        lastError: 'WebSocket error',
      });
    };
    socket.onclose = () => {
      connection.connected = false;
      this.connections.delete(url);
      this.updateState(url, { status: 'disconnected', connected: false });
      this.scheduleReconnect(url);
    };
  }
  private handleMessage(payload: string): void {
    const message = JSON.parse(payload) as [string, ...unknown[]];
    if (message[0] !== 'EVENT') {
      return;
    }
    const [, subId, event] = message as ['EVENT', string, NostrEvent];
    const subscription = this.subscriptions.get(subId);
    subscription?.onEvent(event);
  }
  private resubscribe(connection: RelayConnection): void {
    for (const [subId, subscription] of this.subscriptions.entries()) {
      if (!subscription.relayUrls.includes(connection.url)) {
        continue;
      }

      connection.socket.send(
        serializeMessage(['REQ', subId, ...subscription.filters]),
      );
    }
  }
  private flushPendingPublishes(connection: RelayConnection): void {
    const queuedPayloads = this.pendingPublishes.get(connection.url);

    if (!queuedPayloads || queuedPayloads.length === 0) {
      return;
    }

    for (const payload of queuedPayloads) {
      connection.socket.send(payload);
    }

    this.pendingPublishes.delete(connection.url);
  }
  private broadcast(message: ClientMessage, relayUrls: string[] = this.relayUrls): void {
    const payload = serializeMessage(message);

    for (const relayUrl of relayUrls) {
      const connection = this.connections.get(relayUrl);

      if (connection?.connected) {
        connection.socket.send(payload);
      }
    }
  }
  private ensureRelayUrls(relayUrls: string[]): void {
    for (const relayUrl of relayUrls) {
      if (!this.states.has(relayUrl)) {
        this.states.set(relayUrl, { url: relayUrl, status: 'idle', connected: false });
      }

      if (!this.relayUrls.includes(relayUrl)) {
        this.relayUrls.push(relayUrl);
      }
    }
  }
  private scheduleReconnect(url: string): void {
    if (this.reconnectTimers.has(url)) {
      return;
    }
    const reconnectAt = Date.now() + 3_000;
    this.updateState(url, {
      status: 'reconnecting',
      connected: false,
      reconnectAt,
    });
    const timer = setTimeout(() => {
      this.reconnectTimers.delete(url);
      void this.connectRelay(url);
    }, 3_000);
    this.reconnectTimers.set(url, timer);
  }
  private clearReconnect(url: string): void {
    const timer = this.reconnectTimers.get(url);
    if (timer) {
      clearTimeout(timer);
      this.reconnectTimers.delete(url);
    }
  }
  private updateState(url: string, patch: Partial<RelayState>): void {
    const current = this.states.get(url) ?? {
      url,
      status: 'idle' as RelayStatus,
      connected: false,
    };
    this.states.set(url, { ...current, ...patch, url });
    const snapshot = this.getRelayStates();
    for (const listener of this.listeners) {
      listener(snapshot);
    }
  }
}
