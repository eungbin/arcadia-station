import { create } from "zustand";
import { persist } from "zustand/middleware";

export type GraphicsQuality = "LOW" | "MEDIUM" | "HIGH";

type SettingsState = {
  open: boolean;
  /** 플레이 안내 오버레이 표시 여부. 저장하지 않는다. */
  guideOpen: boolean;
  /** 안내를 한 번이라도 닫았는지. 첫 플레이에만 자동으로 띄우기 위해 저장한다. */
  guideSeen: boolean;
  graphicsQuality: GraphicsQuality;
  mouseSensitivity: number;
  masterVolume: number;
  audioEnabled: boolean;
  reducedMotion: boolean;
  subtitles: boolean;
  setOpen: (open: boolean) => void;
  setGuideOpen: (open: boolean) => void;
  closeGuide: () => void;
  setGraphicsQuality: (quality: GraphicsQuality) => void;
  setMouseSensitivity: (value: number) => void;
  setMasterVolume: (value: number) => void;
  setAudioEnabled: (enabled: boolean) => void;
  setReducedMotion: (enabled: boolean) => void;
  setSubtitles: (enabled: boolean) => void;
};

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      open: false,
      guideOpen: false,
      guideSeen: false,
      graphicsQuality: "HIGH",
      mouseSensitivity: 1,
      masterVolume: 0.42,
      audioEnabled: true,
      reducedMotion: false,
      subtitles: true,
      setOpen: (open) => set({ open }),
      setGuideOpen: (guideOpen) => set({ guideOpen }),
      closeGuide: () => set({ guideOpen: false, guideSeen: true }),
      setGraphicsQuality: (graphicsQuality) => set({ graphicsQuality }),
      setMouseSensitivity: (mouseSensitivity) => set({ mouseSensitivity }),
      setMasterVolume: (masterVolume) => set({ masterVolume }),
      setAudioEnabled: (audioEnabled) => set({ audioEnabled }),
      setReducedMotion: (reducedMotion) => set({ reducedMotion }),
      setSubtitles: (subtitles) => set({ subtitles }),
    }),
    {
      name: "arcadia-station-settings-v1",
      partialize: ({ open: _open, guideOpen: _guideOpen, ...settings }) => settings,
    },
  ),
);
