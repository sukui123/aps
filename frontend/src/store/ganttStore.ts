import { create } from 'zustand';

export type ZoomLevel = 'day' | 'week' | 'month';

/** 甘特高频交互 UI 态骨架（技术方案 §1.2/§6.4），本轮不实现虚拟滚动。 */
interface GanttState {
  zoom: ZoomLevel;
  selectedTaskIds: number[];
  lockedTaskIds: number[];
  setZoom: (zoom: ZoomLevel) => void;
  selectTask: (id: number) => void;
  toggleLock: (id: number) => void;
  clearSelection: () => void;
}

export const useGanttStore = create<GanttState>((set) => ({
  zoom: 'day',
  selectedTaskIds: [],
  lockedTaskIds: [],
  setZoom: (zoom) => set({ zoom }),
  selectTask: (id) =>
    set((state) => ({
      selectedTaskIds: state.selectedTaskIds.includes(id)
        ? state.selectedTaskIds
        : [...state.selectedTaskIds, id],
    })),
  toggleLock: (id) =>
    set((state) => ({
      lockedTaskIds: state.lockedTaskIds.includes(id)
        ? state.lockedTaskIds.filter((x) => x !== id)
        : [...state.lockedTaskIds, id],
    })),
  clearSelection: () => set({ selectedTaskIds: [] }),
}));
