import { create } from 'zustand';
import * as api from '../services/api';
import type { Skill } from '../types';

interface SkillState {
  skills: Skill[];
  activeSkills: Skill[];
  isLoading: boolean;
  error: string | null;

  loadSkills: (conversationId?: number) => Promise<void>;
  toggleSkill: (skillId: number, conversationId: number) => Promise<void>;
  clearError: () => void;
}

export const useSkillStore = create<SkillState>((set, get) => ({
  skills: [],
  activeSkills: [],
  isLoading: false,
  error: null,

  loadSkills: async (conversationId) => {
    set({ isLoading: true });
    try {
      const res = await api.getSkills(conversationId);
      const skills = res.data;
      set({
        skills,
        activeSkills: skills.filter((s) => s.active),
        isLoading: false
      });
    } catch (error: unknown) {
      const msg = error instanceof Error ? error.message : '加载技能失败';
      set({ error: msg, isLoading: false });
    }
  },

  toggleSkill: async (skillId, conversationId) => {
    const { skills } = get();
    const skill = skills.find((s) => s.id === skillId);
    if (!skill) return;

    const newActive = !skill.active;

    // 乐观更新
    set({
      skills: skills.map((s) =>
        s.id === skillId ? { ...s, active: newActive } : s
      )
    });

    try {
      if (newActive) {
        await api.activateSkill(skillId, conversationId);
      } else {
        await api.deactivateSkill(skillId, conversationId);
      }
      // 重新加载以确保状态同步
      await get().loadSkills(conversationId);
    } catch (error: unknown) {
      // 回滚
      set({
        skills: skills.map((s) =>
          s.id === skillId ? { ...s, active: !newActive } : s
        ),
        error: error instanceof Error ? error.message : '操作失败'
      });
    }
  },

  clearError: () => set({ error: null })
}));
