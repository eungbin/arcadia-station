import { create } from "zustand";
import { persist } from "zustand/middleware";

export type GraphicsQuality = "LOW" | "MEDIUM" | "HIGH";

type SettingsState = {
  open: boolean;
  graphicsQuality: GraphicsQuality;
  mouseSensitivity: number;
  masterVolume: number;
  audioEnabled: boolean;
  reducedMotion: boolean;
  subtitles: boolean;
  setOpen: (open: boolean) => void;
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
      graphicsQuality: "HIGH",
      mouseSensitivity: 1,
      masterVolume: 0.42,
      audioEnabled: true,
      reducedMotion: false,
      subtitles: true,
      setOpen: (open) => set({ open }),
      setGraphicsQuality: (graphicsQuality) => set({ graphicsQuality }),
      setMouseSensitivity: (mouseSensitivity) => set({ mouseSensitivity }),
      setMasterVolume: (masterVolume) => set({ masterVolume }),
      setAudioEnabled: (audioEnabled) => set({ audioEnabled }),
      setReducedMotion: (reducedMotion) => set({ reducedMotion }),
      setSubtitles: (subtitles) => set({ subtitles }),
    }),
    {
      name: "arcadia-station-settings-v1",
      partialize: ({ open: _open, ...settings }) => settings,
    },
  ),
);
