import { lazy, Suspense, useCallback, useEffect, useState } from "react";
import { GameUI } from "./ui/GameUI";
import { useGameStore } from "./store/gameStore";
import { AudioDirector } from "./game/AudioDirector";
import { SettingsPanel } from "./ui/SettingsPanel";
import { MobileControls } from "./ui/MobileControls";
import { useSettingsStore } from "./store/settingsStore";
import { INVESTIGATION_OBJECTS } from "./data/investigation";

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
  const [sceneReady, setSceneReady] = useState(false);
  const handleSceneReady = useCallback(() => setSceneReady(true), []);

  useEffect(() => {
    if (layer === "opening") setSceneReady(false);
  }, [layer]);

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
        // 물리 계층: 조사한 3D 오브젝트. 진행 게이트가 이걸 본다.
        const inspected = [
          "CO_BODY",
          "CO_DOOR_LOG",
          "CO_ENV_PANEL",
          "CO_TERMINAL",
          "EN_LIFE_SUPPORT",
          "CM_SECURITY_ARCHIVE",
          "CG_AIRLOCK_LOG",
          "MD_MEDICAL_TERMINAL",
        ];
        // 지식 계층: 서버가 해금해 준 단서. 수첩·이론·재판이 이걸 본다.
        const evidence = inspected.map((objectId, index) => ({
          clueId: `MOCK-${objectId}`,
          title: INVESTIGATION_OBJECTS[objectId]?.evidenceLabel ?? objectId,
          clueType: (["PHYSICAL", "DIGITAL", "OPPORTUNITY", "MOTIVE"] as const)[index % 4],
          playerText: INVESTIGATION_OBJECTS[objectId]?.detail ?? "",
          sourceObjectId: objectId,
        }));
        const theory = {
          suspectId: "JUNHO",
          setup: "MOCK-CO_ENV_PANEL",
          trigger: "MOCK-EN_LIFE_SUPPORT",
          opportunity: "MOCK-CO_DOOR_LOG",
          motive: "MOCK-CO_TERMINAL",
          exclusions: {
            MAYA: "MOCK-CO_BODY",
            SOPHIA: "MOCK-MD_MEDICAL_TERMINAL",
            KASIM: "MOCK-CM_SECURITY_ARCHIVE",
            YUNA: "MOCK-CG_AIRLOCK_LOG",
          },
        };

        if (screen === "dayReview") {
          useGameStore.setState({ layer: "dayReview", discoveredIds: inspected, evidence });
        } else if (screen === "interrogation") {
          useGameStore.setState({
            layer: "interrogation",
            selectedId: "NPC_MAYA",
            // 심문 채널 조회는 세션이 있어야 시작된다. 저장된 상태에 의존하지 않도록 직접 세운다.
            sessionId: useGameStore.getState().sessionId ?? "LOCAL-QA",
            discoveredIds: inspected, evidence,
          });
        } else if (screen === "theory") {
          useGameStore.setState({
            layer: "notebook",
            notebookTab: "theory",
            phase: "DAY2",
            discoveredIds: inspected, evidence,
            theory,
          });
        } else if (screen === "trial") {
          useGameStore.setState({ layer: "trial", phase: "DAY2", discoveredIds: inspected, evidence, theory });
        } else {
          useGameStore.setState({
            layer: "result",
            phase: "DAY2",
            discoveredIds: inspected, evidence,
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
          <ArcadiaScene onReady={handleSceneReady} />
          {!sceneReady && <SceneBootScreen />}
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
