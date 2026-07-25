import { AdaptiveDpr } from "@react-three/drei";
import { Canvas } from "@react-three/fiber";
import { Physics } from "@react-three/rapier";
import { ACESFilmicToneMapping, SRGBColorSpace } from "three";
import { memo } from "react";
import { InteractionController } from "./InteractionController";
import { PlayerController } from "./PlayerController";
import { StationWorld } from "./world/StationWorld";
import { useSettingsStore } from "../store/settingsStore";

export const ArcadiaScene = memo(function ArcadiaScene() {
  const quality = useSettingsStore((state) => state.graphicsQuality);

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
      }}
      onCreated={({ gl }) => {
        gl.setClearColor("#06090a", 1);
        gl.toneMapping = ACESFilmicToneMapping;
        gl.toneMappingExposure = 1.16;
        gl.outputColorSpace = SRGBColorSpace;
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
      <Physics gravity={[0, -16, 0]} timeStep="vary">
        <StationWorld />
        <PlayerController />
      </Physics>
      <InteractionController />
      {quality !== "HIGH" && <AdaptiveDpr pixelated />}

    </Canvas>
  );
});
