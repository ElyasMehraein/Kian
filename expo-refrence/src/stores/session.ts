import { create } from 'zustand';

interface SessionState {
  pubkey: string | null;
  isReady: boolean;
  setPubkey: (pubkey: string | null) => void;
  setReady: (isReady: boolean) => void;
  reset: () => void;
}

const initialState = {
  pubkey: null,
  isReady: false,
} as const;

export const useSessionStore = create<SessionState>((set) => ({
  ...initialState,
  setPubkey: (pubkey) => {
    set({ pubkey });
  },
  setReady: (isReady) => {
    set({ isReady });
  },
  reset: () => {
    set(initialState);
  },
}));
