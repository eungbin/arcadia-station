import { AdaptiveDpr } from "@react-three/drei";
import { Canvas, useFrame, useThree } from "@react-three/fiber";
import { Physics } from "@react-three/rapier";
import {
  ACESFilmicToneMapping,
  Frustum,
  Matrix4,
  PointLight,
  Quaternion,
  SRGBColorSpace,
  Sphere,
  Vector3,
  type Object3D,
} from "three";
import {
  memo,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { InteractionController } from "./InteractionController";
import { PlayerController } from "./PlayerController";
import { StationWorld } from "./world/StationWorld";
import { useGameStore } from "../store/gameStore";
import { useSettingsStore } from "../store/settingsStore";

const NORMAL_POINT_LIGHT_SLOTS = 20;
const SCAN_POINT_LIGHT_SLOTS = 24;
const POINT_LIGHT_CULL_MARGIN = 0.1;
const LIGHT_SLOT_INDICES = Array.from(
  { length: SCAN_POINT_LIGHT_SLOTS },
  (_, index) => index,
);

export const ArcadiaScene = memo(function ArcadiaScene({
  onReady,
}: {
  onReady: () => void;
}) {
  const quality = useSettingsStore((state) => state.graphicsQuality);
  const [shadersReady, setShadersReady] = useState(false);
  const handleShadersReady = useCallback(() => {
    setShadersReady(true);
    onReady();
  }, [onReady]);

  return (
    <Canvas
      className="scene-canvas"
      frameloop="always"
      shadows={quality !== "LOW"}
      style={{ background: "#06090a" }}
      dpr={quality === "HIGH" ? [1, 1.75] : quality === "MEDIUM" ? [1, 1.35] : 1}
      camera={{ fov: 68, near: 0.08, far: 120, position: [0, 1.7, 6] }}
      gl={{
        alpha: false,
        antialias: true,
        powerPreference: "high-performance",
        stencil: false,
      }}
      onCreated={({ gl, scene }) => {
        gl.setClearColor("#06090a", 1);
        gl.toneMapping = ACESFilmicToneMapping;
        gl.toneMappingExposure = 1.16;
        gl.outputColorSpace = SRGBColorSpace;
        scene.matrixAutoUpdate = false;
      }}
    >
      <color attach="background" args={["#06090a"]} />
      <fog attach="fog" args={["#080c0d", 20, 58]} />

      <ambientLight color="#9db2ac" intensity={0.9} />
      <hemisphereLight args={["#b8d6ce", "#25201d", 1.05]} />
      <directionalLight
        position={[7, 14, 9]}
        color="#d8e2dc"
        intensity={2.6}
        castShadow
        shadow-mapSize={[2048, 2048]}
        shadow-camera-near={1}
        shadow-camera-far={45}
        shadow-camera-left={-24}
        shadow-camera-right={24}
        shadow-camera-top={24}
        shadow-camera-bottom={-30}
      />
      <pointLight position={[-7, 3.8, 5]} color="#b8eee1" intensity={48} distance={18} decay={2} />
      <pointLight position={[7, 3.4, 1]} color="#8fcabd" intensity={38} distance={16} decay={2} />
      <pointLight position={[0, 3.2, -8]} color="#dc7562" intensity={42} distance={18} decay={2} />
      <LightSlotPool />
      <Physics gravity={[0, -16, 0]} timeStep="vary">
        <StationWorld />
        {shadersReady && <PlayerController />}
      </Physics>
      {shadersReady && <InteractionController />}
      {shadersReady && <LightAndInteractionEffectsController />}
      <ShaderPrecompiler onReady={handleShadersReady} />
      {quality !== "HIGH" && <AdaptiveDpr pixelated />}

    </Canvas>
  );
});

function LightSlotPool() {
  return (
    <>
      {LIGHT_SLOT_INDICES.map((index) => (
        <pointLight
          key={index}
          intensity={0}
          distance={1}
          visible={false}
          userData={{ lightSlotDummy: true }}
        />
      ))}
    </>
  );
}

function ShaderPrecompiler({ onReady }: { onReady: () => void }) {
  const gl = useThree((state) => state.gl);
  const scene = useThree((state) => state.scene);
  const camera = useThree((state) => state.camera);

  useEffect(() => {
    let cancelled = false;
    const originalVisibility = new Map<Object3D, boolean>();

    const precompile = async () => {
      const pointLights: PointLight[] = [];
      const dummyLights: PointLight[] = [];
      const focusLights: PointLight[] = [];
      const scanEffects: Object3D[] = [];
      scene.traverse((object) => {
        if (object instanceof PointLight) {
          if (object.userData.lightSlotDummy === true) {
            dummyLights.push(object);
          } else {
            pointLights.push(object);
            if (typeof object.userData.interactionFocusLight === "string") {
              focusLights.push(object);
            }
          }
        }
        if (object.userData.interactionScanEffect === true) {
          scanEffects.push(object);
        }
      });

      [...pointLights, ...dummyLights, ...scanEffects].forEach((object) => {
        originalVisibility.set(object, object.visible);
      });

      const restoreVisibility = () => {
        originalVisibility.forEach((visible, object) => {
          object.visible = visible;
        });
      };
      const compileCurrentScene = async () => {
        await gl.compileAsync(scene, camera);
        gl.render(scene, camera);
        gl.getContext().finish();
      };
      const compileSlotCount = async (count: number) => {
        restoreVisibility();
        pointLights.forEach((light) => {
          light.visible = false;
        });
        scanEffects.forEach((effect) => {
          effect.visible = false;
        });
        dummyLights.forEach((light, index) => {
          light.visible = index < count;
        });
        await compileCurrentScene();
      };
      const compileFullVariant = async (focusVisible: boolean, scanVisible: boolean) => {
        restoreVisibility();
        dummyLights.forEach((light) => {
          light.visible = false;
        });
        focusLights.forEach((light, index) => {
          light.visible = focusVisible && index === 0;
        });
        scanEffects.forEach((effect) => {
          effect.visible = scanVisible;
        });
        await compileCurrentScene();
      };

      await compileSlotCount(NORMAL_POINT_LIGHT_SLOTS);
      await compileSlotCount(SCAN_POINT_LIGHT_SLOTS);
      await compileFullVariant(false, false);
      await compileFullVariant(true, false);
      await compileFullVariant(false, true);
      await compileFullVariant(true, true);
    };

    void precompile()
      .catch((error) => {
        if (import.meta.env.DEV) {
          console.error("Shader precompilation failed.", error);
        }
      })
      .finally(() => {
        originalVisibility.forEach((visible, object) => {
          object.visible = visible;
        });
        if (!cancelled) {
          onReady();
        }
      });

    return () => {
      cancelled = true;
      originalVisibility.forEach((visible, object) => {
        object.visible = visible;
      });
    };
  }, [camera, gl, onReady, scene]);

  return null;
}

type PointLightRecord = {
  focusId?: string;
  light: PointLight;
  scanOnly: boolean;
  sphere: Sphere;
};

function LightAndInteractionEffectsController() {
  const scene = useThree((state) => state.scene);
  const camera = useThree((state) => state.camera);
  const lightRecords = useRef<PointLightRecord[]>([]);
  const dummyLights = useRef<PointLight[]>([]);
  const scanEffects = useRef<Object3D[]>([]);
  const relevantLights = useRef<PointLightRecord[]>([]);
  const frustum = useRef(new Frustum());
  const projectionView = useRef(new Matrix4());
  const lastCameraPosition = useRef(new Vector3());
  const lastCameraQuaternion = useRef(new Quaternion());
  const lastProjectionMatrix = useRef(new Matrix4());
  const lastFocusedId = useRef<string | null>(null);
  const lastScanActive = useRef(false);
  const cullingInitialized = useRef(false);

  useLayoutEffect(() => {
    const worldPosition = new Vector3();
    scene.updateMatrixWorld(true);
    scene.traverse((object) => {
      if (object instanceof PointLight) {
        if (object.userData.lightSlotDummy === true) {
          dummyLights.current.push(object);
          return;
        }
        object.getWorldPosition(worldPosition);
        lightRecords.current.push({
          focusId: typeof object.userData.interactionFocusLight === "string"
            ? object.userData.interactionFocusLight
            : undefined,
          light: object,
          scanOnly: object.userData.interactionScanLight === true,
          sphere: new Sphere(
            worldPosition.clone(),
            object.distance > 0
              ? object.distance + POINT_LIGHT_CULL_MARGIN
              : Number.POSITIVE_INFINITY,
          ),
        });
      }
      if (object.userData.interactionScanEffect === true) {
        scanEffects.current.push(object);
      }
    });

    return () => {
      lightRecords.current = [];
      dummyLights.current = [];
      scanEffects.current = [];
      relevantLights.current = [];
    };
  }, [scene]);

  useFrame(() => {
    const { focusedId, scanUntil } = useGameStore.getState();
    const scanActive = scanUntil > performance.now();
    const scanChanged = scanActive !== lastScanActive.current;
    if (scanChanged) {
      scanEffects.current.forEach((effect) => {
        effect.visible = scanActive;
      });
      lastScanActive.current = scanActive;
    }

    const cameraChanged = !lastCameraPosition.current.equals(camera.position)
      || !lastCameraQuaternion.current.equals(camera.quaternion)
      || !lastProjectionMatrix.current.equals(camera.projectionMatrix);
    if (
      cullingInitialized.current
      && !cameraChanged
      && !scanChanged
      && focusedId === lastFocusedId.current
    ) {
      return;
    }
    cullingInitialized.current = true;
    lastCameraPosition.current.copy(camera.position);
    lastCameraQuaternion.current.copy(camera.quaternion);
    lastProjectionMatrix.current.copy(camera.projectionMatrix);
    lastFocusedId.current = focusedId;

    camera.updateMatrixWorld();
    projectionView.current.multiplyMatrices(
      camera.projectionMatrix,
      camera.matrixWorldInverse,
    );
    frustum.current.setFromProjectionMatrix(projectionView.current);

    const relevant = relevantLights.current;
    relevant.length = 0;
    lightRecords.current.forEach((record) => {
      const intended = record.focusId !== undefined
        ? record.focusId === focusedId
        : !record.scanOnly || scanActive;
      record.light.visible = false;
      if (intended && frustum.current.intersectsSphere(record.sphere)) {
        relevant.push(record);
      }
    });

    const slotCount = scanActive
      ? SCAN_POINT_LIGHT_SLOTS
      : NORMAL_POINT_LIGHT_SLOTS;
    if (relevant.length <= slotCount) {
      relevant.forEach((record) => {
        record.light.visible = true;
      });
      dummyLights.current.forEach((light, index) => {
        light.visible = index < slotCount - relevant.length;
      });
      return;
    }

    lightRecords.current.forEach((record) => {
      record.light.visible = record.focusId !== undefined
        ? record.focusId === focusedId
        : !record.scanOnly || scanActive;
    });
    dummyLights.current.forEach((light) => {
      light.visible = false;
    });
  });

  useEffect(() => {
    return () => {
      lightRecords.current.forEach((record) => {
        record.light.visible = record.focusId === undefined && !record.scanOnly;
      });
      dummyLights.current.forEach((light) => {
        light.visible = false;
      });
      scanEffects.current.forEach((effect) => {
        effect.visible = false;
      });
    };
  }, []);

  return null;
}
