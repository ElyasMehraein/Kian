import { create } from 'zustand';

interface UIState {
  isBootstrapping: boolean;
  isRelayConnected: boolean;
  errorMessage: string | null;
  accountMode: 'business' | 'merchant';
  profileVersion: number;
  setBootstrapping: (value: boolean) => void;
  setRelayConnected: (value: boolean) => void;
  setErrorMessage: (value: string | null) => void;
  setAccountMode: (value: 'business' | 'merchant') => void;
  bumpProfileVersion: () => void;
  reset: () => void;
}

const initialState = {
  isBootstrapping: false,
  isRelayConnected: false,
  errorMessage: null,
  accountMode: 'business' as const,
  profileVersion: 0,
} as const;

export const useUIStore = create<UIState>((set) => ({
  ...initialState,
  setBootstrapping: (value) => {
    set({ isBootstrapping: value });
  },
  setRelayConnected: (value) => {
    set({ isRelayConnected: value });
  },
  setErrorMessage: (value) => {
    set({ errorMessage: value });
  },
  setAccountMode: (value) => {
    set({ accountMode: value });
  },
  bumpProfileVersion: () => {
    set((state) => ({ profileVersion: state.profileVersion + 1 }));
  },
  reset: () => {
    set(initialState);
  },
}));
