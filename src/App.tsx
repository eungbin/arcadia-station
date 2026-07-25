import { lazy, Suspense, useEffect } from "react";
import { GameUI } from "./ui/GameUI";
import { useGameStore } from "./store/gameStore";
import { AudioDirector } from "./game/AudioDirector";
import { SettingsPanel } from "./ui/SettingsPanel";
import { MobileControls } from "./ui/MobileControls";
import { useSettingsStore } from "./store/settingsStore";

const ArcadiaScene = lazy(() =>
  import("./game/ArcadiaScene").then((module) => ({ default: module.ArcadiaScene })),
);

declare global {
  interface Window {
    __ARCADIA_QA__?: {
      toggleNotebook: () => string;
      setSettingsOpen: (open: boolean) => void;
      showScreen: (
        screen: "dayReview" | "interrogation" | "theory" | "trial" | "result"
      ) => void;
    };
  }
}

export default function App() {
  const layer = useGameStore((state) => state.layer);
  const graphicsQuality = useSettingsStore((state) => state.graphicsQuality);
  const reducedMotion = useSettingsStore((state) => state.reducedMotion);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const state = useGameStore.getState();
      const settings = useSettingsStore.getState();

      if (event.code === "Escape" && settings.open) {
        settings.setOpen(false);
        return;
      }

      if (event.code === "Tab" && state.layer !== "opening") {
        event.preventDefault();
        state.toggleNotebook();
      }

      if (
        event.code === "Escape" &&
        (state.layer === "inspection" ||
          state.layer === "interrogation" ||
          state.layer === "notebook" ||
          state.layer === "dayReview")
      ) {
        state.closeOverlay();
        return;
      }

      if (event.code === "Escape" && state.layer === "playing") {
        document.exitPointerLock?.();
        settings.setOpen(true);
        return;
      }

      if (state.layer !== "playing") return;

      if (event.code === "KeyE" && state.focusedId) {
        state.openInspection(state.focusedId);
      }

      if (event.code === "KeyQ" && !event.repeat) {
        state.activateScan();
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  useEffect(() => {
    if (!import.meta.env.DEV) return;
    window.__ARCADIA_QA__ = {
      toggleNotebook: () => {
        useGameStore.getState().toggleNotebook();
        return useGameStore.getState().layer;
      },
      setSettingsOpen: (open) => useSettingsStore.getState().setOpen(open),
      showScreen: (screen) => {
        const evidence = [
          "CO_BODY",
          "CO_DOOR_LOG",
          "CO_ENV_PANEL",
          "CO_TERMINAL",
          "EN_LIFE_SUPPORT",
          "CM_SECURITY_ARCHIVE",
          "CG_AIRLOCK_LOG",
          "MD_MEDICAL_TERMINAL",
        ];
        const theory = {
          suspectId: "JUNHO",
          method: "CO_ENV_PANEL",
          motive: "CO_TERMINAL",
          trace: "EN_LIFE_SUPPORT",
          exclusions: {
            MAYA: "CO_BODY",
            SOPHIA: "MD_MEDICAL_TERMINAL",
            KASIM: "CM_SECURITY_ARCHIVE",
            YUNA: "CG_AIRLOCK_LOG",
          },
        };

        if (screen === "dayReview") {
          useGameStore.setState({ layer: "dayReview", discoveredIds: evidence });
        } else if (screen === "interrogation") {
          useGameStore.setState({
            layer: "interrogation",
            selectedId: "NPC_MAYA",
            discoveredIds: evidence,
          });
        } else if (screen === "theory") {
          useGameStore.setState({
            layer: "notebook",
            notebookTab: "theory",
            phase: "DAY2",
            discoveredIds: evidence,
            theory,
          });
        } else if (screen === "trial") {
          useGameStore.setState({ layer: "trial", phase: "DAY2", discoveredIds: evidence, theory });
        } else {
          useGameStore.setState({
            layer: "result",
            phase: "DAY2",
            discoveredIds: evidence,
            theory,
            trialResult: {
              accusedId: "JUNHO",
              votesFor: 5,
              ending: "CULPRIT_EXPELLED",
              correctAccusation: true,
            },
          });
        }
      },
    };
    return () => {
      delete window.__ARCADIA_QA__;
    };
  }, []);

  return (
    <main className="game-shell">
      {layer !== "opening" && (
        <Suspense fallback={<SceneBootScreen />}>
          <ArcadiaScene />
        </Suspense>
      )}
      {layer !== "opening" && (
        <div
          className={`scene-grade scene-grade--${graphicsQuality.toLowerCase()} ${
            reducedMotion ? "is-reduced" : ""
          }`}
          aria-hidden="true"
        />
      )}
      <GameUI />
      <MobileControls />
      <AudioDirector />
      <SettingsPanel />
    </main>
  );
}

function SceneBootScreen() {
  return (
    <div className="scene-boot" role="status">
      <i />
      <span>STATION GEOMETRY</span>
      <strong>아르카디아 공간 데이터 복원 중</strong>
    </div>
  );
}
