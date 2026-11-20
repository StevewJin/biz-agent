import { create } from 'zustand';
import * as api from '../services/api';
import type { Team, LlmProvider, AgentItem } from '../types/admin';

interface AdminState {
  teams: Team[];
  providers: LlmProvider[];
  agents: AgentItem[];
  agentsLoading: boolean;
  error: string | null;

  loadTeams: () => Promise<void>;
  createTeam: (name: string, description?: string) => Promise<void>;
  deleteTeam: (id: number) => Promise<void>;

  loadProviders: () => Promise<void>;
  createProvider: (data: Record<string, unknown>) => Promise<void>;
  deleteProvider: (id: number) => Promise<void>;

  loadAgents: (teamId?: number) => Promise<void>;
  createAgent: (data: Record<string, unknown>) => Promise<void>;
  deleteAgent: (id: number) => Promise<void>;

  clearError: () => void;
}

export const useAdminStore = create<AdminState>((set, get) => ({
  teams: [],
  providers: [],
  agents: [],
  agentsLoading: false,
  error: null,

  loadTeams: async () => {
    try {
      const res = await api.getTeams();
      set({ teams: res.data });
    } catch (e: unknown) {
      set({ error: (e as Error).message });
    }
  },

  createTeam: async (name, description) => {
    await api.createTeam(name, description);
    await get().loadTeams();
  },

  deleteTeam: async (id) => {
    await api.deleteTeam(id);
    set((s) => ({ teams: s.teams.filter((t) => t.id !== id) }));
  },

  loadProviders: async () => {
    try {
      const res = await api.getProviders();
      set({ providers: res.data });
    } catch (e: unknown) {
      set({ error: (e as Error).message });
    }
  },

  createProvider: async (data) => {
    await api.createProvider(data);
    await get().loadProviders();
  },

  deleteProvider: async (id) => {
    await api.deleteProvider(id);
    set((s) => ({ providers: s.providers.filter((p) => p.id !== id) }));
  },

  loadAgents: async (teamId) => {
    set({ agentsLoading: true });
    try {
      const res = await api.getAgents(teamId);
      set({ agents: res.data, agentsLoading: false });
    } catch (e: unknown) {
      set({ error: (e as Error).message, agentsLoading: false });
    }
  },

  createAgent: async (data) => {
    await api.createAgent(data);
    await get().loadAgents();
  },

  deleteAgent: async (id) => {
    await api.deleteAgent(id);
    set((s) => ({ agents: s.agents.filter((a) => a.id !== id) }));
  },

  clearError: () => set({ error: null }),
}));
