import { create } from "zustand";
import { persist } from "zustand/middleware";

export type GraphicsQuality = "LOW" | "MEDIUM" | "HIGH";

/**
 * 처음 실행할 때의 그래픽 품질.
 *
 * 정거장은 물리·그림자·실시간 조명을 함께 돌려서, 어떤 기기인지 모르는 채로 높은 품질에서
 * 시작하면 첫 화면부터 프레임이 무너질 수 있다. 잘못 낮추면 설정에서 바로 올릴 수 있지만
 * 잘못 높이면 첫인상을 잃으므로, 모두 성능 우선에서 시작한다.
 */
export const DEFAULT_QUALITY: GraphicsQuality = "LOW";

/**
 * 이 기기가 감당할 수 있는 그래픽 품질.
 *
 * 기본값을 정하는 데는 쓰지 않는다. 성능에 여유가 있는 기기에 "더 올려도 된다"고 알려 주는
 * 용도다. 판단 근거는 브라우저가 알려주는 것만 쓴다. `deviceMemory`는 크로미움 계열에만 있고
 * `hardwareConcurrency`도 값이 부정확할 수 있어, 하나라도 낮게 나오면 낮은 쪽을 택한다.
 */
export function recommendedQuality(): GraphicsQuality {
  if (typeof navigator === "undefined") return "MEDIUM";

  const cores = navigator.hardwareConcurrency ?? 0;
  const memory = (navigator as Navigator & { deviceMemory?: number }).deviceMemory ?? 0;
  const isTouchOnly =
    typeof matchMedia === "function" && matchMedia("(hover: none) and (pointer: coarse)").matches;

  // 모바일·태블릿은 코어 수가 많아도 지속 성능과 발열 한계가 달라 항상 성능 우선으로 시작한다.
  if (isTouchOnly) return "LOW";
  if ((cores > 0 && cores <= 4) || (memory > 0 && memory <= 4)) return "LOW";
  if ((cores > 0 && cores <= 8) || (memory > 0 && memory <= 8)) return "MEDIUM";
  return "HIGH";
}

type SettingsState = {
  open: boolean;
  /** 플레이 안내 오버레이 표시 여부. 저장하지 않는다. */
  guideOpen: boolean;
  /** 안내를 한 번이라도 닫았는지. 첫 플레이에만 자동으로 띄우기 위해 저장한다. */
  guideSeen: boolean;
  /** 수첩 탭 안내 표시 여부. 저장하지 않는다. */
  notebookGuideOpen: boolean;
  /** 수첩 안내를 한 번이라도 닫았는지. 수첩을 처음 열 때만 자동으로 띄운다. */
  notebookGuideSeen: boolean;
  graphicsQuality: GraphicsQuality;
  /** 사람이 직접 품질을 고른 적이 있는지. 없으면 진입 화면에서 권장값을 제안한다. */
  graphicsQualityChosen: boolean;
  mouseSensitivity: number;
  masterVolume: number;
  audioEnabled: boolean;
  reducedMotion: boolean;
  subtitles: boolean;
  setOpen: (open: boolean) => void;
  setGuideOpen: (open: boolean) => void;
  closeGuide: () => void;
  setNotebookGuideOpen: (open: boolean) => void;
  closeNotebookGuide: () => void;
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
      notebookGuideOpen: false,
      notebookGuideSeen: false,
      graphicsQuality: DEFAULT_QUALITY,
      graphicsQualityChosen: false,
      mouseSensitivity: 1,
      masterVolume: 0.42,
      audioEnabled: true,
      reducedMotion: false,
      subtitles: true,
      setOpen: (open) => set({ open }),
      setGuideOpen: (guideOpen) => set({ guideOpen }),
      closeGuide: () => set({ guideOpen: false, guideSeen: true }),
      setNotebookGuideOpen: (notebookGuideOpen) => set({ notebookGuideOpen }),
      closeNotebookGuide: () => set({ notebookGuideOpen: false, notebookGuideSeen: true }),
      setGraphicsQuality: (graphicsQuality) =>
        set({ graphicsQuality, graphicsQualityChosen: true }),
      setMouseSensitivity: (mouseSensitivity) => set({ mouseSensitivity }),
      setMasterVolume: (masterVolume) => set({ masterVolume }),
      setAudioEnabled: (audioEnabled) => set({ audioEnabled }),
      setReducedMotion: (reducedMotion) => set({ reducedMotion }),
      setSubtitles: (subtitles) => set({ subtitles }),
    }),
    {
      name: "arcadia-station-settings-v1",
      partialize: ({
        open: _open,
        guideOpen: _guideOpen,
        notebookGuideOpen: _notebookGuideOpen,
        ...settings
      }) => settings,
      version: 1,
      migrate: (persistedState, fromVersion) => {
        const settings = persistedState as Partial<SettingsState>;
        // v0은 기기 성능을 추정해서 기본값을 정했다. 직접 고른 적이 없다면 성능 우선으로 되돌린다.
        return (
          fromVersion < 1 && !settings.graphicsQualityChosen
            ? { ...settings, graphicsQuality: DEFAULT_QUALITY }
            : settings
        ) as SettingsState;
      },
    },
  ),
);
