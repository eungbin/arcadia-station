import { createInstances, RoundedBox } from "@react-three/drei";
import { useFrame } from "@react-three/fiber";
import { CuboidCollider, RigidBody } from "@react-three/rapier";
import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  type PropsWithChildren,
} from "react";
import {
  AdditiveBlending,
  CanvasTexture,
  Color,
  DoubleSide,
  Group,
  LinearFilter,
  SRGBColorSpace,
  type InstancedMesh,
  type Object3D,
} from "three";
import { useGameStore } from "../../store/gameStore";

type Vector3Tuple = [number, number, number];

const palette = {
  structure: "#161d20",
  structureLight: "#263034",
  floor: "#111719",
  ivory: "#d6d1c4",
  mint: "#72cbb9",
  amber: "#e19b54",
  violet: "#9188e8",
  magenta: "#da7aa4",
  alert: "#d65a43",
  screen: "#79d7c3",
};

let wallPanelTexture: CanvasTexture | null = null;
let floorPanelTexture: CanvasTexture | null = null;
const [CeilingHousingInstances, CeilingHousingInstance] = createInstances();
const [CeilingBracketInstances, CeilingBracketInstance] = createInstances();
const [DoorStructureInstances, DoorStructureInstance] = createInstances();
const [DoorBadgeInstances, DoorBadgeInstance] = createInstances();
const [FloorBaseInstances, FloorBaseInstance] = createInstances();
const [FloorPanelInstances, FloorPanelInstance] = createInstances();
const [ScreenFrameInstances, ScreenFrameInstance] = createInstances();
const [ScreenLineInstances, ScreenLineInstance] = createInstances();
const [ScreenIndicatorInstances, ScreenIndicatorInstance] = createInstances();
const [WallInstances, WallInstance] = createInstances();
const [WallPanelInstances, WallPanelInstance] = createInstances();

function getWallPanelTexture() {
  if (wallPanelTexture) return wallPanelTexture;

  const canvas = document.createElement("canvas");
  canvas.width = 256;
  canvas.height = 256;
  const context = canvas.getContext("2d");

  if (context) {
    context.fillStyle = "#182124";
    context.fillRect(0, 0, 256, 256);
    context.strokeStyle = "#3a474a";
    context.lineWidth = 2;
    context.strokeRect(8, 8, 240, 240);
    context.strokeStyle = "#303c3f";
    context.lineWidth = 1;
    [64, 128, 192].forEach((x) => {
      context.beginPath();
      context.moveTo(x, 10);
      context.lineTo(x, 246);
      context.stroke();
    });
    [78, 178].forEach((y) => {
      context.beginPath();
      context.moveTo(10, y);
      context.lineTo(246, y);
      context.stroke();
    });
    context.fillStyle = "#647073";
    [18, 238].forEach((x) => {
      [18, 238].forEach((y) => {
        context.beginPath();
        context.arc(x, y, 3, 0, Math.PI * 2);
        context.fill();
      });
    });
    context.fillStyle = "#222d30";
    context.fillRect(18, 220, 54, 8);
    context.fillStyle = "#66998f";
    context.fillRect(20, 222, 28, 4);
  }

  wallPanelTexture = new CanvasTexture(canvas);
  wallPanelTexture.colorSpace = SRGBColorSpace;
  wallPanelTexture.minFilter = LinearFilter;
  wallPanelTexture.magFilter = LinearFilter;
  return wallPanelTexture;
}

function getFloorPanelTexture() {
  if (floorPanelTexture) return floorPanelTexture;

  const canvas = document.createElement("canvas");
  canvas.width = 512;
  canvas.height = 512;
  const context = canvas.getContext("2d");

  if (context) {
    context.fillStyle = "#1b2326";
    context.fillRect(0, 0, 512, 512);
    context.strokeStyle = "#080d0e";
    context.lineWidth = 5;
    context.strokeRect(7, 7, 498, 498);
    context.lineWidth = 2;
    [128, 256, 384].forEach((offset) => {
      context.beginPath();
      context.moveTo(offset, 8);
      context.lineTo(offset, 504);
      context.stroke();
      context.beginPath();
      context.moveTo(8, offset);
      context.lineTo(504, offset);
      context.stroke();
    });
    context.fillStyle = "#687170";
    [
      [20, 20],
      [492, 20],
      [20, 492],
      [492, 492],
    ].forEach(([x, y]) => {
      context.beginPath();
      context.arc(x, y, 6, 0, Math.PI * 2);
      context.fill();
      context.strokeStyle = "#242d2f";
      context.lineWidth = 2;
      context.beginPath();
      context.moveTo(x - 4, y);
      context.lineTo(x + 4, y);
      context.stroke();
    });
    context.strokeStyle = "rgba(130, 145, 143, 0.16)";
    context.lineWidth = 1;
    [
      [44, 90, 202, 68],
      [286, 414, 454, 386],
      [68, 344, 220, 362],
    ].forEach(([x1, y1, x2, y2]) => {
      context.beginPath();
      context.moveTo(x1, y1);
      context.lineTo(x2, y2);
      context.stroke();
    });
  }

  floorPanelTexture = new CanvasTexture(canvas);
  floorPanelTexture.colorSpace = SRGBColorSpace;
  floorPanelTexture.minFilter = LinearFilter;
  floorPanelTexture.magFilter = LinearFilter;
  return floorPanelTexture;
}

function StaticBox({
  position,
  scale,
}: {
  position: Vector3Tuple;
  scale: Vector3Tuple;
}) {
  const thinX = scale[0] <= 0.5 && scale[2] > 0.8;
  const thinZ = scale[2] <= 0.5 && scale[0] > 0.8;

  return (
    <RigidBody type="fixed" colliders={false} position={position}>
      <CuboidCollider args={[scale[0] / 2, scale[1] / 2, scale[2] / 2]} />
      <WallInstance scale={scale} />
      {thinZ &&
        [-1, 1].map((side) => (
          <WallPanelInstance
            key={side}
            position={[0, 0, side * (scale[2] / 2 + 0.018)]}
            scale={[Math.max(0.16, scale[0] - 0.18), scale[1] - 0.24, 0.026]}
          />
        ))}
      {thinX &&
        [-1, 1].map((side) => (
          <WallPanelInstance
            key={side}
            position={[side * (scale[0] / 2 + 0.018), 0, 0]}
            scale={[0.026, scale[1] - 0.24, Math.max(0.16, scale[2] - 0.18)]}
          />
        ))}
    </RigidBody>
  );
}

function FloorPlate({
  position,
  size,
  accent,
}: {
  position: Vector3Tuple;
  size: [number, number];
  accent: string;
}) {
  const [width, depth] = size;
  return (
    <group position={position}>
      <FloorBaseInstance scale={[width, 0.14, depth]} />
      <FloorPanelInstance
        position={[0, 0.081, 0]}
        rotation={[-Math.PI / 2, 0, 0]}
        scale={[width - 0.4, depth - 0.4, 1]}
      />
      <mesh position={[0, 0.09, -depth / 2 + 0.23]}>
        <boxGeometry args={[width - 0.55, 0.024, 0.08]} />
        <meshStandardMaterial color={accent} emissive={accent} emissiveIntensity={1.25} />
      </mesh>
    </group>
  );
}

function CeilingStrip({
  position,
  length = 5,
  color = "#dce5df",
  intensity = 1.5,
  rotation = [0, 0, 0],
}: {
  position: Vector3Tuple;
  length?: number;
  color?: string;
  intensity?: number;
  rotation?: Vector3Tuple;
}) {
  return (
    <group position={position} rotation={rotation}>
      <CeilingHousingInstance
        position={[0, 0.045, 0]}
        scale={[length + 0.26, 0.16, 0.3]}
      />
      <mesh position={[0, -0.035, 0.02]}>
        <boxGeometry args={[length, 0.065, 0.14]} />
        <meshStandardMaterial color={color} emissive={color} emissiveIntensity={3} />
      </mesh>
      {[-0.43, 0.43].map((factor) => (
        <CeilingBracketInstance
          key={factor}
          position={[length * factor, 0.16, 0]}
          scale={[0.12, 0.28, 0.38]}
        />
      ))}
      <pointLight
        color={color}
        intensity={intensity * 12}
        distance={9}
        decay={2}
        castShadow={false}
      />
    </group>
  );
}

function ZoneSign({
  position,
  code,
  name,
  accent,
  rotation = [0, 0, 0],
}: {
  position: Vector3Tuple;
  code: string;
  name: string;
  accent: string;
  rotation?: Vector3Tuple;
}) {
  const texture = useMemo(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 1024;
    canvas.height = 280;
    const context = canvas.getContext("2d");

    if (context) {
      context.fillStyle = "#0b1112";
      context.fillRect(0, 0, canvas.width, canvas.height);

      context.fillStyle = accent;
      context.fillRect(0, 0, 24, canvas.height);
      context.fillRect(24, canvas.height - 10, canvas.width - 24, 10);

      context.strokeStyle = "rgba(174, 200, 193, 0.16)";
      context.lineWidth = 2;
      for (let x = 64; x < canvas.width; x += 64) {
        context.beginPath();
        context.moveTo(x, 0);
        context.lineTo(x, canvas.height);
        context.stroke();
      }

      context.textBaseline = "middle";
      context.fillStyle = accent;
      context.font = '600 48px "IBM Plex Mono", monospace';
      context.fillText(code, 76, 67);

      context.textAlign = "right";
      context.fillStyle = "rgba(185, 207, 201, 0.58)";
      context.font = '500 30px "IBM Plex Mono", monospace';
      context.fillText("AUTHORIZED ACCESS", 950, 67);

      context.textAlign = "left";
      context.shadowColor = accent;
      context.shadowBlur = 12;
      context.fillStyle = "#eef5f1";
      context.font =
        '700 86px "Noto Sans KR Variable", "Noto Sans KR", "Malgun Gothic", sans-serif';
      context.fillText(name, 76, 177);
      context.shadowBlur = 0;

      context.fillStyle = "rgba(185, 207, 201, 0.5)";
      context.font = '500 25px "IBM Plex Mono", monospace';
      context.fillText("ARCADIA STATION // DECK 01", 78, 244);
    }

    const zoneTexture = new CanvasTexture(canvas);
    zoneTexture.colorSpace = SRGBColorSpace;
    zoneTexture.minFilter = LinearFilter;
    zoneTexture.magFilter = LinearFilter;
    zoneTexture.needsUpdate = true;
    return zoneTexture;
  }, [accent, code, name]);

  useEffect(() => () => texture.dispose(), [texture]);

  return (
    <group position={position} rotation={rotation} userData={{ zoneCode: code, zoneName: name }}>
      <RoundedBox args={[3.18, 0.96, 0.12]} radius={0.055} smoothness={3}>
        <meshStandardMaterial color="#222b2d" metalness={0.76} roughness={0.38} />
      </RoundedBox>
      <mesh position={[0, 0, 0.066]}>
        <boxGeometry args={[3.02, 0.8, 0.035]} />
        <meshBasicMaterial color="#071011" />
      </mesh>
      <mesh position={[0, 0, 0.084]}>
        <planeGeometry args={[2.92, 0.72]} />
        <meshBasicMaterial
          map={texture}
          side={DoubleSide}
          toneMapped={false}
          transparent
        />
      </mesh>
      <mesh position={[0, 0, -0.084]} rotation={[0, Math.PI, 0]}>
        <planeGeometry args={[2.92, 0.72]} />
        <meshBasicMaterial
          map={texture}
          side={DoubleSide}
          toneMapped={false}
          transparent
        />
      </mesh>
      <mesh position={[-1.54, 0, 0.09]}>
        <boxGeometry args={[0.035, 0.78, 0.035]} />
        <meshBasicMaterial color={accent} />
      </mesh>
      <mesh position={[1.46, 0.36, 0.09]}>
        <boxGeometry args={[0.1, 0.025, 0.025]} />
        <meshBasicMaterial color="#d8f6ed" />
      </mesh>
      <mesh position={[1.32, 0.36, 0.09]}>
        <boxGeometry args={[0.1, 0.025, 0.025]} />
        <meshBasicMaterial color={accent} />
      </mesh>
      <mesh position={[-1.2, -0.58, 0]}>
        <boxGeometry args={[0.12, 0.26, 0.12]} />
        <meshStandardMaterial color="#242d2f" metalness={0.78} roughness={0.36} />
      </mesh>
      <mesh position={[1.2, -0.58, 0]}>
        <boxGeometry args={[0.12, 0.26, 0.12]} />
        <meshStandardMaterial color="#242d2f" metalness={0.78} roughness={0.36} />
      </mesh>
    </group>
  );
}

function HubDirectory() {
  const texture = useMemo(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 1200;
    canvas.height = 920;
    const context = canvas.getContext("2d");
    const zones = [
      { code: "CO · 01", name: "사령관실", direction: "N", color: palette.alert },
      { code: "MD · 02", name: "의무실", direction: "W", color: palette.mint },
      { code: "EN · 03", name: "엔지니어링", direction: "W", color: palette.amber },
      { code: "CM · 04", name: "통신실", direction: "S", color: palette.violet },
      { code: "CG · 05", name: "화물·도킹", direction: "S", color: palette.magenta },
      { code: "CMN · 06", name: "공용 모듈", direction: "S", color: "#aaa89f" },
    ];

    if (context) {
      context.fillStyle = "#081011";
      context.fillRect(0, 0, canvas.width, canvas.height);

      const glow = context.createRadialGradient(280, 420, 20, 280, 420, 360);
      glow.addColorStop(0, "rgba(114, 203, 185, 0.14)");
      glow.addColorStop(1, "rgba(114, 203, 185, 0)");
      context.fillStyle = glow;
      context.fillRect(0, 0, 580, canvas.height);

      context.strokeStyle = "rgba(174, 200, 193, 0.1)";
      context.lineWidth = 2;
      for (let x = 0; x <= canvas.width; x += 60) {
        context.beginPath();
        context.moveTo(x, 0);
        context.lineTo(x, canvas.height);
        context.stroke();
      }
      for (let y = 0; y <= canvas.height; y += 60) {
        context.beginPath();
        context.moveTo(0, y);
        context.lineTo(canvas.width, y);
        context.stroke();
      }

      context.fillStyle = palette.mint;
      context.fillRect(54, 50, 12, 92);
      context.fillStyle = "#e7f0ec";
      context.font = '700 54px "IBM Plex Mono", monospace';
      context.fillText("STATION DIRECTORY", 92, 92);
      context.fillStyle = "rgba(185, 207, 201, 0.6)";
      context.font = '500 27px "IBM Plex Mono", monospace';
      context.fillText("CENTRAL HUB // DECK 01", 94, 134);

      context.strokeStyle = "rgba(114, 203, 185, 0.5)";
      context.lineWidth = 10;
      context.beginPath();
      context.arc(280, 455, 86, 0, Math.PI * 2);
      context.stroke();
      context.fillStyle = "#152123";
      context.beginPath();
      context.arc(280, 455, 67, 0, Math.PI * 2);
      context.fill();

      const branches = [
        [280, 368, 280, 218, palette.alert],
        [194, 455, 88, 455, palette.mint],
        [205, 510, 115, 615, palette.amber],
        [280, 542, 220, 700, palette.violet],
        [330, 530, 390, 700, palette.magenta],
        [366, 455, 480, 455, "#aaa89f"],
      ] as const;
      for (const [x1, y1, x2, y2, color] of branches) {
        context.strokeStyle = color;
        context.lineWidth = 12;
        context.beginPath();
        context.moveTo(x1, y1);
        context.lineTo(x2, y2);
        context.stroke();
        context.fillStyle = color;
        context.beginPath();
        context.arc(x2, y2, 16, 0, Math.PI * 2);
        context.fill();
      }

      context.textAlign = "center";
      context.fillStyle = "#dff5ef";
      context.font = '700 30px "IBM Plex Mono", monospace';
      context.fillText("YOU", 280, 448);
      context.font = '500 22px "IBM Plex Mono", monospace';
      context.fillStyle = palette.mint;
      context.fillText("ARE HERE", 280, 480);

      context.textAlign = "left";
      zones.forEach((zone, index) => {
        const y = 224 + index * 103;
        context.fillStyle = "rgba(14, 24, 25, 0.9)";
        context.fillRect(570, y - 42, 574, 82);
        context.fillStyle = zone.color;
        context.fillRect(570, y - 42, 8, 82);
        context.fillStyle = zone.color;
        context.font = '600 25px "IBM Plex Mono", monospace';
        context.fillText(zone.code, 600, y - 10);
        context.fillStyle = "#e6eeea";
        context.font =
          '700 38px "Noto Sans KR Variable", "Noto Sans KR", "Malgun Gothic", sans-serif';
        context.fillText(zone.name, 600, y + 27);
        context.textAlign = "right";
        context.fillStyle = "rgba(216, 236, 230, 0.66)";
        context.font = '600 30px "IBM Plex Mono", monospace';
        context.fillText(zone.direction, 1110, y + 8);
        context.textAlign = "left";
      });

      context.fillStyle = "rgba(185, 207, 201, 0.45)";
      context.font = '500 23px "IBM Plex Mono", monospace';
      context.fillText("CORVIS CONSORTIUM // WAYFINDING NODE HB-01", 58, 868);
      context.fillStyle = palette.mint;
      context.fillRect(58, 886, 1086, 8);
    }

    const directoryTexture = new CanvasTexture(canvas);
    directoryTexture.colorSpace = SRGBColorSpace;
    directoryTexture.minFilter = LinearFilter;
    directoryTexture.magFilter = LinearFilter;
    directoryTexture.needsUpdate = true;
    return directoryTexture;
  }, []);

  useEffect(() => () => texture.dispose(), [texture]);

  return (
    <RigidBody
      type="fixed"
      colliders={false}
      position={[4.1, 0, 0.6]}
      rotation={[0, -0.68, 0]}
    >
      <CuboidCollider args={[1.68, 1.52, 0.2]} position={[0, 1.52, 0]} />
      <group
      userData={{ zoneDirectory: true }}
      >
        <mesh position={[0, 0.12, 0]} receiveShadow>
          <cylinderGeometry args={[0.92, 1.12, 0.24, 8]} />
          <meshStandardMaterial color="#151d1f" metalness={0.78} roughness={0.38} />
        </mesh>
        {[-1.24, 1.24].map((x) => (
          <mesh key={x} position={[x, 1.35, -0.04]} castShadow>
            <boxGeometry args={[0.14, 2.35, 0.18]} />
            <meshStandardMaterial color="#263033" metalness={0.82} roughness={0.34} />
          </mesh>
        ))}
        <RoundedBox
          args={[3.25, 2.5, 0.18]}
          radius={0.08}
          smoothness={3}
          position={[0, 1.72, 0]}
          castShadow
        >
          <meshStandardMaterial color="#20292b" metalness={0.78} roughness={0.34} />
        </RoundedBox>
        <mesh position={[0, 1.72, 0.101]}>
          <planeGeometry args={[3.03, 2.28]} />
          <meshBasicMaterial map={texture} toneMapped={false} />
        </mesh>
        <mesh position={[0, 3.02, 0.04]}>
          <boxGeometry args={[3.18, 0.045, 0.12]} />
          <meshBasicMaterial color={palette.mint} />
        </mesh>
        <pointLight
          position={[0, 2.1, 0.5]}
          color={palette.mint}
          intensity={2.4}
          distance={3.8}
          decay={2}
        />
      </group>
    </RigidBody>
  );
}

function DoorFrame({
  position,
  accent,
  width = 3.2,
  rotation = [0, 0, 0],
}: {
  position: Vector3Tuple;
  accent: string;
  width?: number;
  rotation?: Vector3Tuple;
}) {
  return (
    <group position={position} rotation={rotation}>
      <DoorStructureInstance
        position={[-width / 2, 1.6, 0]}
        scale={[0.28, 3.2, 0.42]}
      />
      <DoorStructureInstance
        position={[width / 2, 1.6, 0]}
        scale={[0.28, 3.2, 0.42]}
      />
      <DoorStructureInstance
        position={[0, 3.16, 0]}
        scale={[width + 0.28, 0.28, 0.42]}
      />
      <mesh position={[0, 3.03, 0.23]}>
        <boxGeometry args={[width - 0.25, 0.055, 0.06]} />
        <meshStandardMaterial color={accent} emissive={accent} emissiveIntensity={2.3} />
      </mesh>
      {[-1, 1].map((side) => (
        <group key={side}>
          <mesh position={[side * (width / 2 - 0.17), 1.58, 0.235]}>
            <boxGeometry args={[0.045, 2.65, 0.065]} />
            <meshStandardMaterial color={accent} emissive={accent} emissiveIntensity={1.35} />
          </mesh>
          {[0.35, 2.8].map((height) => (
            <DoorBadgeInstance
              key={height}
              position={[side * (width / 2 - 0.17), height, 0.285]}
              scale={[0.18, 0.1, 0.09]}
            />
          ))}
        </group>
      ))}
    </group>
  );
}

function OpenPortal({
  position,
  rotation = [0, 0, 0],
  code,
  name,
  accent,
}: {
  position: Vector3Tuple;
  rotation?: Vector3Tuple;
  code: string;
  name: string;
  accent: string;
}) {
  return (
    <group position={position} rotation={rotation}>
      <DoorFrame position={[0, 0, 0]} width={3.4} accent={accent} />
      <ZoneSign position={[0, 3.65, 0]} code={code} name={name} accent={accent} />
    </group>
  );
}

function InteractiveGroup({
  id,
  children,
  position = [0, 0, 0],
  rotation = [0, 0, 0],
  markerPosition = [0, 1.4, 0],
}: PropsWithChildren<{
  id: string;
  position?: Vector3Tuple;
  rotation?: Vector3Tuple;
  markerPosition?: Vector3Tuple;
}>) {
  const discovered = useGameStore((state) => state.discoveredIds.includes(id));

  return (
    <group
      position={position}
      rotation={rotation}
      userData={{ interactionId: id, interactionMarker: markerPosition }}
    >
      {children}
      <pointLight
        position={markerPosition}
        color="#a8f6df"
        intensity={1.1}
        distance={3}
        visible={false}
        userData={{ interactionFocusLight: id }}
      />
      <group
        position={markerPosition}
        visible={false}
        userData={{ interactionScanEffect: true }}
      >
        <mesh rotation={[Math.PI / 4, Math.PI / 4, 0]}>
          <octahedronGeometry args={[0.11, 0]} />
          <meshBasicMaterial
            color={discovered ? "#5c706a" : "#8ce0c8"}
            wireframe
          />
        </mesh>
        <pointLight
          color={discovered ? "#5c706a" : "#8ce0c8"}
          intensity={0.55}
          distance={1.8}
          userData={{ interactionScanLight: true }}
        />
      </group>
    </group>
  );
}

function Screen({
  position = [0, 0, 0],
  size = [1.5, 0.86],
  color = palette.screen,
  rotation = [0, 0, 0],
}: {
  position?: Vector3Tuple;
  size?: [number, number];
  color?: string;
  rotation?: Vector3Tuple;
}) {
  return (
    <group position={position} rotation={rotation}>
      <ScreenFrameInstance
        position={[0, 0, -0.045]}
        scale={[size[0] + 0.18, size[1] + 0.18, 0.12]}
      />
      <mesh>
        <boxGeometry args={[size[0], size[1], 0.055]} />
        <meshStandardMaterial
          color={new Color(color).multiplyScalar(0.3)}
          emissive={color}
          emissiveIntensity={1.85}
          roughness={0.26}
          metalness={0.42}
        />
      </mesh>
      {[-0.34, -0.18, 0.08, 0.26].map((factor, index) => (
        <ScreenLineInstance
          key={factor}
          position={[-size[0] * 0.29, size[1] * factor, 0.036]}
          scale={[size[0] * (0.38 + index * 0.07), 0.012, 0.012]}
          color={index === 3 ? "#d7eee8" : color}
        />
      ))}
      <ScreenIndicatorInstance
        position={[size[0] * 0.4, -size[1] * 0.4, 0.045]}
      />
    </group>
  );
}

function PipeBank({
  position,
  length = 5,
  rotation = [0, 0, 0],
  accent = palette.mint,
}: {
  position: Vector3Tuple;
  length?: number;
  rotation?: Vector3Tuple;
  accent?: string;
}) {
  const joints = [-0.38, 0, 0.38];

  return (
    <group position={position} rotation={rotation}>
      {[-0.16, 0.16].map((offset, index) => (
        <group key={offset} position={[0, offset, 0]}>
          <mesh rotation={[0, 0, Math.PI / 2]} castShadow>
            <cylinderGeometry args={[0.045, 0.045, length, 10]} />
            <meshStandardMaterial
              color={index === 0 ? "#586467" : "#313b3e"}
              metalness={0.88}
              roughness={0.3}
            />
          </mesh>
          {joints.map((factor) => (
            <mesh key={factor} position={[length * factor, 0, 0]} rotation={[0, Math.PI / 2, 0]}>
              <torusGeometry args={[0.058, 0.018, 6, 12]} />
              <meshStandardMaterial color="#8a9391" metalness={0.9} roughness={0.24} />
            </mesh>
          ))}
        </group>
      ))}
      <mesh position={[length * 0.32, 0, 0.07]}>
        <boxGeometry args={[0.34, 0.42, 0.08]} />
        <meshStandardMaterial color="#20292b" metalness={0.76} roughness={0.38} />
      </mesh>
      <mesh position={[length * 0.32, 0, 0.116]}>
        <boxGeometry args={[0.19, 0.035, 0.015]} />
        <meshStandardMaterial color={accent} emissive={accent} emissiveIntensity={1.6} />
      </mesh>
    </group>
  );
}

function AtmosphereParticles() {
  const particles = useRef<Group>(null);
  const positions = useMemo(() => {
    const values = new Float32Array(150 * 3);
    let seed = 4729;
    const random = () => {
      seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
      return seed / 4294967296;
    };

    for (let index = 0; index < values.length; index += 3) {
      values[index] = (random() - 0.5) * 58;
      values[index + 1] = 0.2 + random() * 4.1;
      values[index + 2] = (random() - 0.5) * 72;
    }
    return values;
  }, []);

  useFrame(({ clock }) => {
    if (!particles.current) return;
    particles.current.position.y = Math.sin(clock.elapsedTime * 0.12) * 0.08;
    particles.current.rotation.y = Math.sin(clock.elapsedTime * 0.04) * 0.015;
  });

  return (
    <group ref={particles} userData={{ dynamicTransform: true }}>
      <points>
        <bufferGeometry>
          <bufferAttribute attach="attributes-position" args={[positions, 3]} />
        </bufferGeometry>
        <pointsMaterial
          color="#a5d9ce"
          size={0.028}
          transparent
          opacity={0.22}
          depthWrite={false}
          sizeAttenuation
          blending={AdditiveBlending}
        />
      </points>
    </group>
  );
}

function HubCoreEffects() {
  const rings = useRef<Group>(null);

  useFrame(({ clock }) => {
    if (!rings.current) return;
    rings.current.rotation.y = clock.elapsedTime * 0.08;
  });

  return (
    <group>
      <group ref={rings} userData={{ dynamicTransform: true }}>
        {[0.9, 1.78, 2.68].map((height, index) => (
          <group key={height} rotation={[0, index * 0.56, index === 1 ? 0.09 : -0.05]}>
            <mesh position={[0, height, 0]} rotation={[Math.PI / 2, 0, 0]}>
              <torusGeometry args={[0.72 + index * 0.06, 0.016, 6, 38]} />
              <meshBasicMaterial
                color={index === 1 ? "#d36d59" : palette.mint}
                transparent
                opacity={0.78}
                blending={AdditiveBlending}
              />
            </mesh>
            <mesh position={[0.72 + index * 0.06, height, 0]}>
              <octahedronGeometry args={[0.055, 0]} />
              <meshBasicMaterial color="#d9fff4" />
            </mesh>
          </group>
        ))}
      </group>
      {Array.from({ length: 6 }, (_, index) => {
        const angle = (index / 6) * Math.PI * 2;
        return (
          <group
            key={angle}
            position={[Math.cos(angle) * 1.52, 0.5, Math.sin(angle) * 1.52]}
            rotation={[0, -angle + Math.PI / 2, 0]}
          >
            <mesh castShadow>
              <boxGeometry args={[0.58, 0.38, 0.12]} />
              <meshStandardMaterial color="#253033" metalness={0.82} roughness={0.34} />
            </mesh>
            <mesh position={[0, 0, 0.066]}>
              <boxGeometry args={[0.32, 0.025, 0.012]} />
              <meshStandardMaterial
                color={index === 0 ? palette.alert : palette.mint}
                emissive={index === 0 ? palette.alert : palette.mint}
                emissiveIntensity={1.5}
              />
            </mesh>
          </group>
        );
      })}
      <pointLight position={[0, 2.05, 0]} color={palette.mint} intensity={16} distance={4.4} decay={2} />
    </group>
  );
}

function CrewMember({
  id,
  position,
  rotation = [0, 0, 0],
  accent,
  uniform,
  skin,
}: {
  id: string;
  position: Vector3Tuple;
  rotation?: Vector3Tuple;
  accent: string;
  uniform: string;
  skin: string;
}) {
  const body = useRef<Group>(null);
  const head = useRef<Group>(null);
  const phase = useRef(Math.random() * Math.PI * 2);
  const isMedical = id === "NPC_SOPHIA";
  const isEngineer = id === "NPC_JUNHO";
  const isComms = id === "NPC_KASIM";
  const isCargo = id === "NPC_YUNA";
  const isCommand = id === "NPC_MAYA";

  useFrame(({ clock }) => {
    const time = clock.elapsedTime + phase.current;
    if (body.current) {
      body.current.position.y = Math.sin(time * 1.35) * 0.008;
      body.current.rotation.z = Math.sin(time * 0.42) * 0.006;
    }
    if (head.current) {
      head.current.rotation.y = Math.sin(time * 0.27) * 0.12;
      head.current.rotation.x = Math.sin(time * 0.38) * 0.025;
    }
  });

  return (
    <InteractiveGroup id={id} position={position} rotation={rotation} markerPosition={[0, 2.55, 0]}>
      <group ref={body} userData={{ dynamicTransform: true }}>
        <mesh position={[-0.18, 0.48, 0]} castShadow>
          <capsuleGeometry args={[0.14, 0.62, 7, 12]} />
          <meshStandardMaterial color="#252c2e" roughness={0.72} />
        </mesh>
        <mesh position={[0.18, 0.48, 0]} castShadow>
          <capsuleGeometry args={[0.14, 0.62, 7, 12]} />
          <meshStandardMaterial color="#252c2e" roughness={0.72} />
        </mesh>
        <mesh position={[-0.18, 0.08, 0.08]} castShadow>
          <boxGeometry args={[0.28, 0.16, 0.52]} />
          <meshStandardMaterial color="#111718" metalness={0.34} roughness={0.68} />
        </mesh>
        <mesh position={[0.18, 0.08, 0.08]} castShadow>
          <boxGeometry args={[0.28, 0.16, 0.52]} />
          <meshStandardMaterial color="#111718" metalness={0.34} roughness={0.68} />
        </mesh>
        {[-0.18, 0.18].map((x) => (
          <mesh key={x} position={[x, 0.48, 0.14]} rotation={[0.1, 0, 0]} castShadow>
            <boxGeometry args={[0.22, 0.24, 0.075]} />
            <meshStandardMaterial color="#343d3f" metalness={0.52} roughness={0.5} />
          </mesh>
        ))}

        <mesh position={[0, 1.28, 0]} castShadow>
          <capsuleGeometry args={[0.37, 0.68, 8, 14]} />
          <meshStandardMaterial color={uniform} metalness={0.25} roughness={0.72} />
        </mesh>
        <mesh position={[0, 0.93, 0.02]} castShadow>
          <cylinderGeometry args={[0.37, 0.37, 0.12, 16]} />
          <meshStandardMaterial color="#151b1d" metalness={0.72} roughness={0.4} />
        </mesh>
        <mesh position={[0, 1.45, 0.31]} rotation={[-0.08, 0, 0]} castShadow>
          <boxGeometry args={[0.48, 0.5, 0.1]} />
          <meshStandardMaterial color="#303a3c" metalness={0.48} roughness={0.52} />
        </mesh>
        <mesh position={[0, 1.42, -0.35]} castShadow>
          <boxGeometry args={[0.62, 0.8, 0.18]} />
          <meshStandardMaterial color="#20292b" metalness={0.5} roughness={0.58} />
        </mesh>
        <mesh position={[0, 1.44, 0.375]}>
          <boxGeometry args={[0.52, 0.035, 0.03]} />
          <meshStandardMaterial color={accent} emissive={accent} emissiveIntensity={1.5} />
        </mesh>
        <mesh position={[0.2, 1.23, 0.382]}>
          <boxGeometry args={[0.16, 0.24, 0.035]} />
          <meshStandardMaterial color="#c7d2ce" emissive={accent} emissiveIntensity={0.28} />
        </mesh>

        <mesh position={[-0.49, 1.3, 0]} rotation={[0, 0, -0.16]} castShadow>
          <capsuleGeometry args={[0.12, 0.72, 7, 11]} />
          <meshStandardMaterial color={uniform} roughness={0.72} metalness={0.22} />
        </mesh>
        <mesh position={[0.49, 1.3, 0]} rotation={[0, 0, 0.16]} castShadow>
          <capsuleGeometry args={[0.12, 0.72, 7, 11]} />
          <meshStandardMaterial color={uniform} roughness={0.72} metalness={0.22} />
        </mesh>
        {[-0.48, 0.48].map((x) => (
          <group key={x}>
            <mesh position={[x, 1.61, 0]} rotation={[0, 0, x < 0 ? -0.13 : 0.13]} castShadow>
              <sphereGeometry args={[0.19, 12, 8, 0, Math.PI * 2, 0, Math.PI / 1.8]} />
              <meshStandardMaterial color="#354043" metalness={0.58} roughness={0.46} />
            </mesh>
            <mesh position={[x * 1.06, 1.03, 0.015]} castShadow>
              <cylinderGeometry args={[0.145, 0.145, 0.14, 12]} />
              <meshStandardMaterial color="#161d1f" metalness={0.62} roughness={0.43} />
            </mesh>
            <mesh position={[x * 1.1, 0.79, 0.02]} castShadow>
              <sphereGeometry args={[0.125, 12, 9]} />
              <meshStandardMaterial color={skin} roughness={0.76} />
            </mesh>
          </group>
        ))}
        <mesh position={[0, 1.82, 0]} rotation={[Math.PI / 2, 0, 0]}>
          <torusGeometry args={[0.23, 0.055, 8, 16]} />
          <meshStandardMaterial color="#242d30" metalness={0.55} roughness={0.48} />
        </mesh>

        {isMedical && (
          <group position={[-0.16, 1.5, 0.375]}>
            <mesh>
              <boxGeometry args={[0.06, 0.2, 0.035]} />
              <meshStandardMaterial color="#d9f1eb" emissive={accent} emissiveIntensity={0.55} />
            </mesh>
            <mesh>
              <boxGeometry args={[0.2, 0.06, 0.037]} />
              <meshStandardMaterial color="#d9f1eb" emissive={accent} emissiveIntensity={0.55} />
            </mesh>
          </group>
        )}
        {isEngineer && (
          <group position={[-0.4, 0.93, 0.04]} rotation={[0, 0, -0.18]}>
            <mesh>
              <cylinderGeometry args={[0.065, 0.065, 0.5, 10]} />
              <meshStandardMaterial color="#9d7448" metalness={0.74} roughness={0.36} />
            </mesh>
            <mesh position={[0, 0.28, 0]}>
              <boxGeometry args={[0.17, 0.08, 0.08]} />
              <meshStandardMaterial color="#c1c8c4" metalness={0.9} roughness={0.24} />
            </mesh>
          </group>
        )}
        {isComms && (
          <group position={[0.25, 1.8, -0.43]}>
            <mesh>
              <cylinderGeometry args={[0.025, 0.025, 0.65, 8]} />
              <meshStandardMaterial color="#8c9b99" metalness={0.88} roughness={0.28} />
            </mesh>
            <mesh position={[0, 0.35, 0]}>
              <sphereGeometry args={[0.052, 10, 8]} />
              <meshStandardMaterial color={accent} emissive={accent} emissiveIntensity={2} />
            </mesh>
          </group>
        )}
        {isCargo && (
          <group position={[0.48, 1.65, 0.13]}>
            <mesh>
              <boxGeometry args={[0.19, 0.14, 0.2]} />
              <meshStandardMaterial color="#20282a" metalness={0.72} roughness={0.4} />
            </mesh>
            <mesh position={[0, 0, 0.115]}>
              <circleGeometry args={[0.055, 12]} />
              <meshStandardMaterial color="#f0d4a4" emissive="#f0b25f" emissiveIntensity={2.4} />
            </mesh>
          </group>
        )}
        {isCommand && (
          <group position={[-0.16, 1.58, 0.376]}>
            {[-0.055, 0, 0.055].map((y, index) => (
              <mesh key={y} position={[0, y, index * 0.002]}>
                <boxGeometry args={[0.2 - index * 0.035, 0.018, 0.02]} />
                <meshBasicMaterial color={index === 0 ? "#e5b865" : accent} />
              </mesh>
            ))}
          </group>
        )}

        <group
          ref={head}
          position={[0, 2.02, 0]}
          userData={{ dynamicTransform: true }}
        >
          <mesh castShadow>
            <sphereGeometry args={[0.29, 20, 16]} />
            <meshStandardMaterial color={skin} roughness={0.78} />
          </mesh>
          <mesh position={[0, 0.15, -0.04]} scale={[1.04, 0.68, 1.02]} castShadow>
            <sphereGeometry args={[0.29, 18, 12, 0, Math.PI * 2, 0, Math.PI / 1.7]} />
            <meshStandardMaterial color="#171b1c" roughness={0.82} />
          </mesh>
          <mesh position={[-0.095, 0.02, 0.272]}>
            <sphereGeometry args={[0.018, 8, 8]} />
            <meshStandardMaterial color="#101414" />
          </mesh>
          <mesh position={[0.095, 0.02, 0.272]}>
            <sphereGeometry args={[0.018, 8, 8]} />
            <meshStandardMaterial color="#101414" />
          </mesh>
          <mesh position={[0, -0.045, 0.293]} rotation={[Math.PI / 2, 0, 0]}>
            <coneGeometry args={[0.035, 0.08, 8]} />
            <meshStandardMaterial color={skin} roughness={0.8} />
          </mesh>
          <mesh position={[0, -0.13, 0.275]} rotation={[0, 0, Math.PI / 2]}>
            <torusGeometry args={[0.045, 0.008, 6, 12, Math.PI]} />
            <meshStandardMaterial color="#6f4640" roughness={0.76} />
          </mesh>
          {[-0.285, 0.285].map((x) => (
            <mesh key={x} position={[x, -0.015, 0]}>
              <sphereGeometry args={[0.055, 10, 8]} />
              <meshStandardMaterial color={skin} roughness={0.78} />
            </mesh>
          ))}
        </group>
      </group>
    </InteractiveGroup>
  );
}

function HubArchitecture() {
  return (
    <group>
      <FloorPlate position={[0, 0, 0]} size={[23, 18]} accent="#899593" />
      <StaticBox position={[-11.5, 2.35, -7.7]} scale={[0.4, 4.7, 2.6]} />
      <StaticBox position={[-11.5, 2.35, 0]} scale={[0.4, 4.7, 6]} />
      <StaticBox position={[-11.5, 2.35, 7.7]} scale={[0.4, 4.7, 2.6]} />
      <StaticBox position={[11.5, 2.35, 2.5]} scale={[0.4, 4.7, 13]} />
      <StaticBox position={[-9.85, 2.35, 9]} scale={[3.3, 4.7, 0.4]} />
      <StaticBox position={[-3.25, 2.35, 9]} scale={[3.1, 4.7, 0.4]} />
      <StaticBox position={[3.25, 2.35, 9]} scale={[3.1, 4.7, 0.4]} />
      <StaticBox position={[9.85, 2.35, 9]} scale={[3.3, 4.7, 0.4]} />
      <StaticBox position={[-7.03, 2.35, -9]} scale={[8.95, 4.7, 0.4]} />
      <StaticBox position={[4.52, 2.35, -9]} scale={[3.95, 4.7, 0.4]} />

      <DoorFrame position={[0, 0, -8.9]} accent={palette.alert} />
      <ZoneSign
        position={[0, 3.65, -8.78]}
        code="CO · 01"
        name="사령관실"
        accent={palette.alert}
      />

      <OpenPortal
        position={[-11.28, 0, -4.7]}
        rotation={[0, Math.PI / 2, 0]}
        code="MD · 02"
        name="의무실"
        accent={palette.mint}
      />
      <OpenPortal
        position={[-11.28, 0, 4.7]}
        rotation={[0, Math.PI / 2, 0]}
        code="EN · 03"
        name="엔지니어링"
        accent={palette.amber}
      />
      <OpenPortal
        position={[-6.5, 0, 8.78]}
        rotation={[0, Math.PI, 0]}
        code="CM · 04"
        name="통신실"
        accent={palette.violet}
      />
      <OpenPortal
        position={[0, 0, 8.78]}
        rotation={[0, Math.PI, 0]}
        code="CG · 05"
        name="화물·도킹"
        accent={palette.magenta}
      />
      <OpenPortal
        position={[6.5, 0, 8.78]}
        rotation={[0, Math.PI, 0]}
        code="CMN · 06"
        name="공용 모듈"
        accent="#aaa89f"
      />

      <group position={[0, 0, 0.2]}>
        <mesh position={[0, 0.3, 0]} castShadow receiveShadow>
          <cylinderGeometry args={[2.1, 2.35, 0.6, 12]} />
          <meshStandardMaterial color="#273236" roughness={0.48} metalness={0.58} />
        </mesh>
        <mesh position={[0, 1.75, 0]} castShadow>
          <cylinderGeometry args={[0.58, 0.8, 3.15, 10]} />
          <meshStandardMaterial color="#313d40" roughness={0.5} metalness={0.54} />
        </mesh>
        <mesh position={[0, 2.1, 0]}>
          <cylinderGeometry args={[0.64, 0.64, 1.45, 10]} />
          <meshStandardMaterial
            color="#263d3b"
            emissive={palette.screen}
            emissiveIntensity={0.42}
            transparent
            opacity={0.78}
          />
        </mesh>
        <InteractiveGroup id="HB_MAINTENANCE" position={[0.67, 1.12, 0]}>
          <RoundedBox args={[0.9, 1.2, 0.18]} radius={0.06} smoothness={3} castShadow>
            <meshStandardMaterial color="#303c3f" metalness={0.58} roughness={0.44} />
          </RoundedBox>
          <Screen position={[0, 0.15, 0.11]} size={[0.62, 0.4]} />
        </InteractiveGroup>
        <HubCoreEffects />
      </group>

      <HubDirectory />
      <CeilingStrip position={[-6.5, 4.35, 0]} length={6.5} />
      <CeilingStrip position={[6.5, 4.35, 0]} length={6.5} />
      <CeilingStrip position={[0, 4.35, 5.5]} length={6} />
      <CeilingStrip position={[0, 4.35, -5.4]} length={6} color="#f2b0a2" intensity={1.2} />
      <PipeBank position={[-11.24, 3.6, 0]} length={5.2} rotation={[0, Math.PI / 2, 0]} />
      <PipeBank position={[11.24, 3.55, 3.2]} length={5.8} rotation={[0, Math.PI / 2, 0]} accent={palette.amber} />
    </group>
  );
}

function CommandCorridor() {
  return (
    <group>
      <FloorPlate position={[0, 0, -13.4]} size={[5.1, 9]} accent={palette.alert} />
      <StaticBox position={[-2.55, 2.35, -13.5]} scale={[0.3, 4.7, 9]} />
      <StaticBox position={[2.55, 2.35, -13.5]} scale={[0.3, 4.7, 9]} />
      <CeilingStrip position={[0, 4.25, -11.7]} length={3.4} color="#edb1a5" intensity={1.1} />
      <CeilingStrip position={[0, 4.25, -15.5]} length={3.4} color="#edb1a5" intensity={1.1} />
      <mesh position={[-2.34, 1.45, -13.3]} rotation={[0, Math.PI / 2, 0]}>
        <boxGeometry args={[3.8, 0.04, 1.05]} />
        <meshStandardMaterial color="#572f2a" emissive={palette.alert} emissiveIntensity={0.22} />
      </mesh>
      <PipeBank position={[2.32, 3.55, -13.5]} length={6.4} rotation={[0, Math.PI / 2, 0]} accent={palette.alert} />
    </group>
  );
}

function BodyModel() {
  const uniform = "#40484b";
  const trim = "#c5beb0";
  return (
    <group rotation={[0, 0.18, Math.PI / 2.08]}>
      <mesh position={[0, 0.34, 0]} castShadow>
        <capsuleGeometry args={[0.42, 1.05, 8, 12]} />
        <meshStandardMaterial color={uniform} roughness={0.75} />
      </mesh>
      <mesh position={[0, 1.25, 0.03]} castShadow>
        <sphereGeometry args={[0.33, 18, 14]} />
        <meshStandardMaterial color="#a7907c" roughness={0.82} />
      </mesh>
      <mesh position={[0.16, 0.84, 0.39]} rotation={[0.3, 0, -0.3]} castShadow>
        <capsuleGeometry args={[0.13, 0.95, 6, 10]} />
        <meshStandardMaterial color={uniform} roughness={0.75} />
      </mesh>
      <mesh position={[-0.12, -0.76, 0.1]} rotation={[0.18, 0, 0.05]} castShadow>
        <capsuleGeometry args={[0.16, 1.2, 6, 10]} />
        <meshStandardMaterial color="#2b3235" roughness={0.8} />
      </mesh>
      <mesh position={[0.17, -0.75, -0.12]} rotation={[-0.15, 0, -0.08]} castShadow>
        <capsuleGeometry args={[0.16, 1.2, 6, 10]} />
        <meshStandardMaterial color="#2b3235" roughness={0.8} />
      </mesh>
      <mesh position={[0, 0.28, 0.43]}>
        <boxGeometry args={[0.58, 0.08, 0.04]} />
        <meshStandardMaterial color={trim} roughness={0.5} />
      </mesh>
    </group>
  );
}

function CommandRoom() {
  return (
    <group>
      <FloorPlate position={[0, 0, -23.25]} size={[15, 10.5]} accent={palette.alert} />
      <StaticBox position={[-7.5, 2.35, -23.25]} scale={[0.4, 4.7, 10.5]} />
      <StaticBox position={[0, 2.35, -28.5]} scale={[15, 4.7, 0.4]} />
      <StaticBox position={[-5.1, 2.35, -18]} scale={[4.8, 4.7, 0.4]} />
      <StaticBox position={[5.1, 2.35, -18]} scale={[4.8, 4.7, 0.4]} />
      <StaticBox position={[7.5, 2.35, -27.2]} scale={[0.4, 4.7, 2.6]} />
      <StaticBox position={[7.5, 2.35, -19.55]} scale={[0.4, 4.7, 2.7]} />

      <DoorFrame position={[0, 0, -18.05]} accent={palette.alert} />
      <DoorFrame position={[7.42, 0, -23.4]} rotation={[0, Math.PI / 2, 0]} accent={palette.alert} />
      <ZoneSign
        position={[0, 3.7, -18.15]}
        code="CO · COMMAND"
        name="사령관실"
        accent={palette.alert}
        rotation={[0, Math.PI, 0]}
      />

      <mesh position={[0, 0.72, -25.5]} castShadow receiveShadow>
        <boxGeometry args={[5.2, 0.22, 1.65]} />
        <meshStandardMaterial color="#30383a" roughness={0.42} metalness={0.72} />
      </mesh>
      <mesh position={[-2.2, 0.34, -25.5]} castShadow>
        <boxGeometry args={[0.18, 0.68, 1.3]} />
        <meshStandardMaterial color="#22292c" metalness={0.68} roughness={0.55} />
      </mesh>
      <mesh position={[2.2, 0.34, -25.5]} castShadow>
        <boxGeometry args={[0.18, 0.68, 1.3]} />
        <meshStandardMaterial color="#22292c" metalness={0.68} roughness={0.55} />
      </mesh>

      <InteractiveGroup
        id="CO_TERMINAL"
        position={[0.65, 1.23, -25.55]}
        rotation={[-0.18, 0, 0]}
        markerPosition={[0, 1, 0]}
      >
        <RoundedBox args={[2.15, 1.18, 0.12]} radius={0.08} smoothness={4} castShadow>
          <meshStandardMaterial color="#1b2426" metalness={0.65} roughness={0.4} />
        </RoundedBox>
        <Screen position={[0, 0, 0.08]} size={[1.85, 0.9]} color="#e58f78" />
      </InteractiveGroup>

      <InteractiveGroup id="CO_BODY" position={[2.2, 0.16, -22.1]} markerPosition={[0, 1.25, 0]}>
        <BodyModel />
      </InteractiveGroup>

      <InteractiveGroup
        id="CO_DOOR_LOG"
        position={[-1.9, 1.48, -18.18]}
        rotation={[0, Math.PI, 0]}
      >
        <RoundedBox args={[0.74, 1.16, 0.18]} radius={0.06} smoothness={3} castShadow>
          <meshStandardMaterial color="#20282a" metalness={0.72} roughness={0.43} />
        </RoundedBox>
        <Screen position={[0, 0.14, 0.11]} size={[0.49, 0.54]} color="#ef917c" />
        <mesh position={[0, -0.37, 0.12]}>
          <boxGeometry args={[0.22, 0.06, 0.03]} />
          <meshStandardMaterial color="#ff6c55" emissive="#ff6c55" emissiveIntensity={2} />
        </mesh>
      </InteractiveGroup>

      <InteractiveGroup
        id="CO_ENV_PANEL"
        position={[-7.2, 1.56, -23.4]}
        rotation={[0, Math.PI / 2, 0]}
        markerPosition={[0, 1.3, 0]}
      >
        <RoundedBox args={[2.6, 2.5, 0.24]} radius={0.08} smoothness={3} castShadow>
          <meshStandardMaterial color="#1d282a" metalness={0.7} roughness={0.45} />
        </RoundedBox>
        <Screen position={[0, 0.42, 0.15]} size={[2.14, 0.85]} />
        {[-0.75, -0.25, 0.25, 0.75].map((x, index) => (
          <mesh key={x} position={[x, -0.54, 0.16]}>
            <circleGeometry args={[0.15, 18]} />
            <meshStandardMaterial
              color={index === 1 ? "#db6b55" : "#7ac9b6"}
              emissive={index === 1 ? "#db6b55" : "#7ac9b6"}
              emissiveIntensity={1.2}
            />
          </mesh>
        ))}
      </InteractiveGroup>

      <InteractiveGroup id="CO_SCANNER" position={[5.25, 0, -25.7]} markerPosition={[0, 2.2, 0]}>
        <mesh position={[0, 0.65, 0]} castShadow>
          <cylinderGeometry args={[0.68, 0.82, 0.42, 10]} />
          <meshStandardMaterial color="#242d30" metalness={0.76} roughness={0.43} />
        </mesh>
        <mesh position={[0, 1.55, 0]} castShadow>
          <cylinderGeometry args={[0.12, 0.16, 1.55, 10]} />
          <meshStandardMaterial color="#3b4648" metalness={0.78} roughness={0.38} />
        </mesh>
        <mesh position={[0, 2.25, 0]} rotation={[0.2, 0, 0]} castShadow>
          <boxGeometry args={[1.28, 0.74, 0.36]} />
          <meshStandardMaterial color="#1f292b" metalness={0.7} roughness={0.4} />
        </mesh>
        <Screen position={[0, 2.25, 0.2]} size={[0.94, 0.47]} />
        <pointLight position={[0, 2.1, 0.35]} color={palette.screen} intensity={0.65} distance={2.5} />
      </InteractiveGroup>

      <InteractiveGroup
        id="CO_XO_PASSAGE"
        position={[7.32, 1.45, -23.4]}
        rotation={[0, Math.PI / 2, 0]}
        markerPosition={[0, 1.5, 0]}
      >
        <mesh>
          <boxGeometry args={[2.5, 2.82, 0.12]} />
          <meshStandardMaterial color="#242b2d" roughness={0.48} metalness={0.72} />
        </mesh>
        <mesh position={[0, 0, 0.08]}>
          <boxGeometry args={[0.04, 2.35, 0.03]} />
          <meshStandardMaterial color={palette.alert} emissive={palette.alert} emissiveIntensity={1.5} />
        </mesh>
      </InteractiveGroup>

      <mesh position={[0, 2.5, -28.25]}>
        <boxGeometry args={[8.5, 2.1, 0.08]} />
        <meshStandardMaterial color="#20292b" roughness={0.52} metalness={0.62} />
      </mesh>
      <Screen position={[-2.8, 2.62, -28.18]} size={[2.1, 1.22]} color="#c78270" />
      <Screen position={[0, 2.62, -28.18]} size={[2.1, 1.22]} color="#8eb3ad" />
      <Screen position={[2.8, 2.62, -28.18]} size={[2.1, 1.22]} color="#c78270" />

      <CeilingStrip position={[-3.8, 4.35, -22.8]} length={4.6} color="#edc9c0" intensity={1.6} />
      <CeilingStrip position={[3.8, 4.35, -22.8]} length={4.6} color="#edc9c0" intensity={1.6} />
      <pointLight position={[1.5, 3, -22]} color="#d65a43" intensity={1.15} distance={8} />
    </group>
  );
}

function ExecutiveOffice() {
  return (
    <group>
      <FloorPlate position={[14, 0, -23.25]} size={[10, 10.5]} accent="#bcb6a9" />
      <StaticBox position={[19, 2.35, -23.25]} scale={[0.4, 4.7, 10.5]} />
      <StaticBox position={[14, 2.35, -28.5]} scale={[10, 4.7, 0.4]} />
      <StaticBox position={[17, 2.35, -18]} scale={[4, 4.7, 0.4]} />
      <StaticBox position={[10.1, 2.35, -18]} scale={[2.2, 4.7, 0.4]} />
      <StaticBox position={[7.5, 2.35, -27.2]} scale={[0.4, 4.7, 2.6]} />
      <StaticBox position={[7.5, 2.35, -19.55]} scale={[0.4, 4.7, 2.7]} />

      <DoorFrame position={[9, 0, -18.05]} accent="#bcb6a9" />
      <DoorFrame position={[7.58, 0, -23.4]} rotation={[0, Math.PI / 2, 0]} accent={palette.alert} />
      <ZoneSign
        position={[9, 3.68, -18.16]}
        code="XO · COMMAND"
        name="부사령관 집무실"
        accent="#bcb6a9"
        rotation={[0, Math.PI, 0]}
      />

      <mesh position={[14.4, 0.76, -24.8]} castShadow receiveShadow>
        <boxGeometry args={[4.25, 0.18, 1.45]} />
        <meshStandardMaterial color="#343a39" metalness={0.58} roughness={0.5} />
      </mesh>
      <mesh position={[12.6, 0.36, -24.8]} castShadow>
        <boxGeometry args={[0.18, 0.72, 1.1]} />
        <meshStandardMaterial color="#252c2e" metalness={0.68} roughness={0.48} />
      </mesh>
      <mesh position={[16.2, 0.36, -24.8]} castShadow>
        <boxGeometry args={[0.18, 0.72, 1.1]} />
        <meshStandardMaterial color="#252c2e" metalness={0.68} roughness={0.48} />
      </mesh>

      <InteractiveGroup
        id="XO_RESOURCE_BOARD"
        position={[18.76, 2.25, -23.5]}
        rotation={[0, -Math.PI / 2, 0]}
        markerPosition={[0, 1, 0]}
      >
        <RoundedBox args={[3.6, 2.35, 0.16]} radius={0.06} smoothness={3}>
          <meshStandardMaterial color="#222b2d" metalness={0.62} roughness={0.46} />
        </RoundedBox>
        <Screen position={[-0.9, 0.28, 0.11]} size={[1.38, 1.35]} color="#aaa89f" />
        <Screen position={[0.9, 0.28, 0.11]} size={[1.38, 1.35]} color="#d08a76" />
      </InteractiveGroup>

      <CrewMember
        id="NPC_MAYA"
        position={[15.3, 0, -21.6]}
        rotation={[0, -2.55, 0]}
        accent={palette.alert}
        uniform="#4b4b48"
        skin="#aa806d"
      />
      <CeilingStrip position={[14, 4.35, -23.2]} length={6.4} color="#e2ded1" intensity={1.5} />
    </group>
  );
}

function RoomShell({
  center,
  size,
  accent,
  entrance,
  code,
  name,
  secondaryEast = false,
}: {
  center: [number, number];
  size: [number, number];
  accent: string;
  entrance: "north" | "east" | "west";
  code: string;
  name: string;
  secondaryEast?: boolean;
}) {
  const [x, z] = center;
  const [width, depth] = size;
  const opening = 3.4;
  const wallHeight = 4.7;
  const wallY = wallHeight / 2;

  const horizontalWall = (wallZ: number, segmented: boolean) => {
    if (!segmented) {
      return <StaticBox position={[x, wallY, wallZ]} scale={[width, wallHeight, 0.4]} />;
    }
    const segmentWidth = (width - opening) / 2;
    return (
      <>
        <StaticBox
          position={[x - opening / 2 - segmentWidth / 2, wallY, wallZ]}
          scale={[segmentWidth, wallHeight, 0.4]}
        />
        <StaticBox
          position={[x + opening / 2 + segmentWidth / 2, wallY, wallZ]}
          scale={[segmentWidth, wallHeight, 0.4]}
        />
      </>
    );
  };

  const verticalWall = (wallX: number, segmented: boolean) => {
    if (!segmented) {
      return <StaticBox position={[wallX, wallY, z]} scale={[0.4, wallHeight, depth]} />;
    }
    const segmentDepth = (depth - opening) / 2;
    return (
      <>
        <StaticBox
          position={[wallX, wallY, z - opening / 2 - segmentDepth / 2]}
          scale={[0.4, wallHeight, segmentDepth]}
        />
        <StaticBox
          position={[wallX, wallY, z + opening / 2 + segmentDepth / 2]}
          scale={[0.4, wallHeight, segmentDepth]}
        />
      </>
    );
  };

  const entrancePosition: Vector3Tuple =
    entrance === "north"
      ? [x, 0, z - depth / 2 + 0.06]
      : entrance === "east"
        ? [x + width / 2 - 0.06, 0, z]
        : [x - width / 2 + 0.06, 0, z];
  const entranceRotation: Vector3Tuple =
    entrance === "north"
      ? [0, 0, 0]
      : entrance === "east"
        ? [0, Math.PI / 2, 0]
        : [0, -Math.PI / 2, 0];

  return (
    <group>
      <FloorPlate position={[x, 0, z]} size={[width, depth]} accent={accent} />
      {horizontalWall(z - depth / 2, entrance === "north")}
      {horizontalWall(z + depth / 2, false)}
      {verticalWall(x - width / 2, entrance === "west")}
      {verticalWall(x + width / 2, entrance === "east" || secondaryEast)}
      <DoorFrame position={entrancePosition} rotation={entranceRotation} width={3.4} accent={accent} />
      <ZoneSign
        position={[
          entrancePosition[0],
          3.65,
          entrancePosition[2],
        ]}
        rotation={entranceRotation}
        code={code}
        name={name}
        accent={accent}
      />
      <CeilingStrip position={[x - width * 0.23, 4.25, z]} length={Math.min(4.5, depth * 0.55)} rotation={[0, Math.PI / 2, 0]} color={accent} intensity={0.85} />
      <CeilingStrip position={[x + width * 0.23, 4.25, z]} length={Math.min(4.5, depth * 0.55)} rotation={[0, Math.PI / 2, 0]} color="#d8dfda" intensity={1.05} />
    </group>
  );
}

function MedicalBay() {
  const center: [number, number] = [-19, -4.7];
  return (
    <group>
      <FloorPlate position={[-12.75, 0, -4.7]} size={[2.5, 3.4]} accent={palette.mint} />
      <RoomShell center={center} size={[10, 8.4]} accent={palette.mint} entrance="east" code="MD · 02" name="의무실" />

      {[-21.6, -17.8].map((x) => (
        <group key={x} position={[x, 0, -6.4]}>
          <RoundedBox
            args={[2.3, 0.28, 1.05]}
            radius={0.08}
            smoothness={3}
            position={[0, 0.42, 0]}
            castShadow
            receiveShadow
          >
            <meshStandardMaterial color="#ccd3cc" roughness={0.64} metalness={0.24} />
          </RoundedBox>
          <RoundedBox
            args={[2.08, 0.16, 0.9]}
            radius={0.06}
            smoothness={3}
            position={[0, 0.61, 0]}
            castShadow
          >
            <meshStandardMaterial color="#dce7e2" roughness={0.82} metalness={0.06} />
          </RoundedBox>
          <mesh position={[0, 0.72, -0.46]} castShadow>
            <boxGeometry args={[2.3, 0.72, 0.12]} />
            <meshStandardMaterial color="#303b3b" metalness={0.58} roughness={0.5} />
          </mesh>
          <RoundedBox
            args={[0.55, 0.13, 0.72]}
            radius={0.055}
            smoothness={3}
            position={[-0.76, 0.74, 0]}
          >
            <meshStandardMaterial color="#9fc6bc" roughness={0.78} />
          </RoundedBox>
          <mesh position={[0.36, 0.73, 0]}>
            <boxGeometry args={[1.15, 0.08, 0.82]} />
            <meshStandardMaterial color="#789b94" roughness={0.86} />
          </mesh>
          {[-0.48, 0.48].map((z) => (
            <mesh key={z} position={[0.25, 0.86, z]} castShadow>
              <boxGeometry args={[1.35, 0.06, 0.045]} />
              <meshStandardMaterial color="#687775" metalness={0.74} roughness={0.38} />
            </mesh>
          ))}
          {[-0.86, 0.86].flatMap((legX) =>
            [-0.35, 0.35].map((legZ) => (
              <mesh key={`${legX}-${legZ}`} position={[legX, 0.2, legZ]} castShadow>
                <cylinderGeometry args={[0.055, 0.07, 0.32, 8]} />
                <meshStandardMaterial color="#52605f" metalness={0.76} roughness={0.4} />
              </mesh>
            )),
          )}
          <Screen position={[0.63, 1.02, -0.535]} size={[0.46, 0.25]} color={palette.mint} />
        </group>
      ))}

      <InteractiveGroup id="MD_MEDICAL_STORAGE" position={[-22.7, 0, -2.7]} markerPosition={[0, 2.8, 0]}>
        <RoundedBox args={[2.2, 2.75, 0.52]} radius={0.08} smoothness={3} position={[0, 1.38, 0]} castShadow>
          <meshStandardMaterial color="#d2d4cd" metalness={0.35} roughness={0.62} />
        </RoundedBox>
        <Screen position={[0, 1.82, 0.29]} size={[1.52, 0.62]} color={palette.mint} />
        <mesh position={[0, 0.62, 0.29]}>
          <boxGeometry args={[1.55, 0.06, 0.03]} />
          <meshStandardMaterial color={palette.mint} emissive={palette.mint} emissiveIntensity={1.4} />
        </mesh>
      </InteractiveGroup>

      <InteractiveGroup id="MD_MEDICAL_TERMINAL" position={[-16, 0, -2.65]} markerPosition={[0, 2.45, 0]}>
        <mesh position={[0, 0.58, 0]} castShadow>
          <cylinderGeometry args={[0.75, 0.9, 1.16, 12]} />
          <meshStandardMaterial color="#d7d9d3" roughness={0.58} metalness={0.42} />
        </mesh>
        <mesh position={[0, 1.55, 0]} castShadow>
          <torusGeometry args={[0.73, 0.08, 10, 28]} />
          <meshStandardMaterial color={palette.mint} emissive={palette.mint} emissiveIntensity={0.72} />
        </mesh>
        <pointLight position={[0, 1.5, 0]} color={palette.mint} intensity={0.62} distance={4} />
      </InteractiveGroup>

      <CrewMember
        id="NPC_SOPHIA"
        position={[-19.2, 0, -3.2]}
        rotation={[0, 2.7, 0]}
        accent={palette.mint}
        uniform="#b7c8c2"
        skin="#a77962"
      />
    </group>
  );
}

function EngineeringBay() {
  const center: [number, number] = [-20.5, 4.7];
  return (
    <group>
      <FloorPlate position={[-12.75, 0, 4.7]} size={[2.5, 3.4]} accent={palette.amber} />
      <RoomShell center={center} size={[13, 9.4]} accent={palette.amber} entrance="east" code="EN · 03" name="엔지니어링" />

      {[-24.2, -20.5, -16.8].map((x, index) => (
        <group key={x} position={[x, 0, 4.5]}>
          <mesh position={[0, 1.48, 0]} castShadow>
            <cylinderGeometry args={[0.86, 1.02, 2.95, 14]} />
            <meshStandardMaterial color="#2d3739" metalness={0.76} roughness={0.42} />
          </mesh>
          <mesh position={[0, 1.8, 0]}>
            <torusGeometry args={[0.88, 0.08, 8, 26]} />
            <meshStandardMaterial
              color={index === 1 ? palette.alert : palette.amber}
              emissive={index === 1 ? palette.alert : palette.amber}
              emissiveIntensity={1.4}
            />
          </mesh>
          <Screen position={[0, 1.1, 0.91]} size={[0.84, 0.52]} color={index === 1 ? palette.alert : palette.amber} />
        </group>
      ))}

      {[-24, -22.4, -20.8].map((x) => (
        <group key={x} position={[x, 0, 8.1]}>
          <mesh position={[0, 0.8, 0]} castShadow>
            <cylinderGeometry args={[0.55, 0.64, 1.6, 12]} />
            <meshStandardMaterial color="#7d8d89" metalness={0.62} roughness={0.45} />
          </mesh>
          <mesh position={[0, 1.15, 0]}>
            <torusGeometry args={[0.57, 0.05, 8, 24]} />
            <meshStandardMaterial color={palette.amber} emissive={palette.amber} emissiveIntensity={0.85} />
          </mesh>
        </group>
      ))}
      <PipeBank position={[-20.5, 3.65, 8.95]} length={8.6} accent={palette.amber} />

      <InteractiveGroup id="EN_LIFE_SUPPORT" position={[-16.7, 0, 7.75]} markerPosition={[0, 1.85, 0]}>
        <mesh position={[0, 0.76, 0]} castShadow>
          <boxGeometry args={[3.2, 0.2, 1.1]} />
          <meshStandardMaterial color="#3b3d38" metalness={0.64} roughness={0.52} />
        </mesh>
        <mesh position={[0, 1.15, 0.47]}>
          <boxGeometry args={[2.8, 0.58, 0.1]} />
          <meshStandardMaterial color="#252e30" metalness={0.7} roughness={0.44} />
        </mesh>
        <Screen position={[0, 1.47, 0.55]} size={[1.9, 0.62]} color={palette.amber} rotation={[-0.12, 0, 0]} />
      </InteractiveGroup>

      <CrewMember
        id="NPC_JUNHO"
        position={[-18.3, 0, 7.2]}
        rotation={[0, -2.7, 0]}
        accent={palette.amber}
        uniform="#4f4d43"
        skin="#b58a73"
      />
    </group>
  );
}

function CommunicationsRoom() {
  return (
    <group>
      <FloorPlate position={[-6.5, 0, 10]} size={[3.4, 2]} accent={palette.violet} />
      <RoomShell center={[-6.5, 15]} size={[7, 8]} accent={palette.violet} entrance="north" code="CM · 04" name="통신실" />

      {[-8.8, -6.5, -4.2].map((x, index) => (
        <group key={x} position={[x, 0, 17.85]}>
          <mesh position={[0, 1.7, 0]} castShadow>
            <boxGeometry args={[1.55, 3.4, 0.65]} />
            <meshStandardMaterial color="#20272c" metalness={0.74} roughness={0.4} />
          </mesh>
          {[-0.9, -0.45, 0, 0.45, 0.9].map((y, lightIndex) => (
            <mesh key={y} position={[0, 1.7 + y, -0.34]}>
              <boxGeometry args={[1.1, 0.04, 0.025]} />
              <meshStandardMaterial
                color={lightIndex === index ? "#d2768f" : palette.violet}
                emissive={lightIndex === index ? "#d2768f" : palette.violet}
                emissiveIntensity={1.4}
              />
            </mesh>
          ))}
        </group>
      ))}

      <InteractiveGroup id="CM_SECURITY_ARCHIVE" position={[-6.5, 0, 13.7]} markerPosition={[0, 2.1, 0]}>
        <mesh position={[0, 0.78, 0]} castShadow>
          <boxGeometry args={[4.7, 0.18, 1.25]} />
          <meshStandardMaterial color="#30343a" metalness={0.68} roughness={0.46} />
        </mesh>
        <Screen position={[-1.2, 1.38, 0.46]} size={[1.65, 0.92]} color={palette.violet} rotation={[-0.18, 0, 0]} />
        <Screen position={[1.2, 1.38, 0.46]} size={[1.65, 0.92]} color="#d2768f" rotation={[-0.18, 0, 0]} />
      </InteractiveGroup>
      <PipeBank position={[-6.5, 3.65, 18.55]} length={5.2} accent={palette.violet} />

      <CrewMember
        id="NPC_KASIM"
        position={[-6.5, 0, 12.5]}
        rotation={[0, Math.PI, 0]}
        accent={palette.violet}
        uniform="#36364d"
        skin="#8d6858"
      />
    </group>
  );
}

function CargoDock() {
  return (
    <group>
      <FloorPlate position={[0, 0, 10]} size={[3.4, 2]} accent={palette.magenta} />
      <RoomShell center={[0, 17]} size={[9, 12]} accent={palette.magenta} entrance="north" code="CG · 05" name="화물·도킹" />

      <InteractiveGroup id="CG_AIRLOCK_LOG" position={[0, 0, 22.75]} markerPosition={[0, 3.55, 0]}>
        <DoorFrame position={[0, 0, 0]} width={5.2} accent={palette.magenta} />
        <mesh position={[0, 1.58, 0.08]} castShadow>
          <cylinderGeometry args={[2.25, 2.25, 0.28, 28]} />
          <meshStandardMaterial color="#24282c" metalness={0.82} roughness={0.38} />
        </mesh>
        <mesh position={[0, 1.58, -0.12]}>
          <torusGeometry args={[1.82, 0.08, 10, 36]} />
          <meshStandardMaterial color={palette.magenta} emissive={palette.magenta} emissiveIntensity={1.5} />
        </mesh>
        <mesh position={[0, 1.55, 0.26]}>
          <boxGeometry args={[1.5, 0.24, 0.05]} />
          <meshStandardMaterial
            color="#301817"
            emissive={palette.alert}
            emissiveIntensity={0.35}
          />
        </mesh>
      </InteractiveGroup>

      <mesh position={[1.9, 0.08, 16.8]} receiveShadow>
        <boxGeometry args={[3.4, 0.06, 5.5]} />
        <meshStandardMaterial color="#111517" roughness={0.82} metalness={0.52} />
      </mesh>
      <mesh position={[1.9, 0.12, 16.8]}>
        <ringGeometry args={[1.25, 1.44, 4]} />
        <meshStandardMaterial color={palette.magenta} emissive={palette.magenta} emissiveIntensity={0.7} />
      </mesh>

      <InteractiveGroup id="CG_CARGO_MANIFEST" markerPosition={[-2.4, 2.6, 17]}>
        {[
          [-2.8, 0.65, 14.2],
          [-2.5, 0.65, 17],
          [-2.4, 1.35, 17],
          [-1.8, 0.65, 19.5],
        ].map(([x, y, z], index) => (
          <group key={index} position={[x, y, z]}>
            <RoundedBox
              args={[1.55, 1.25, 1.45]}
              radius={0.08}
              smoothness={2}
              castShadow
            >
              <meshStandardMaterial
                color={index === 3 ? "#715065" : "#545d5c"}
                metalness={0.52}
                roughness={0.6}
              />
            </RoundedBox>
            {[-0.46, 0.46].map((offset) => (
              <mesh key={offset} position={[offset, 0, 0.73]}>
                <boxGeometry args={[0.075, 1.08, 0.035]} />
                <meshStandardMaterial color="#aab1ad" metalness={0.82} roughness={0.3} />
              </mesh>
            ))}
            <mesh position={[0, 0.24, 0.755]}>
              <boxGeometry args={[0.34, 0.2, 0.025]} />
              <meshStandardMaterial
                color={index === 3 ? palette.magenta : "#98c7bc"}
                emissive={index === 3 ? palette.magenta : palette.mint}
                emissiveIntensity={0.48}
              />
            </mesh>
          </group>
        ))}
      </InteractiveGroup>

      <group position={[3.25, 0, 14]}>
        <mesh position={[0, 1.8, 0]} rotation={[0, 0, -0.32]} castShadow>
          <boxGeometry args={[0.34, 3.6, 0.5]} />
          <meshStandardMaterial color="#c5a15d" metalness={0.66} roughness={0.48} />
        </mesh>
        <mesh position={[-0.55, 3.2, 0]} rotation={[0, 0, Math.PI / 2.5]} castShadow>
          <boxGeometry args={[0.3, 1.8, 0.42]} />
          <meshStandardMaterial color="#c5a15d" metalness={0.66} roughness={0.48} />
        </mesh>
      </group>

      <CrewMember
        id="NPC_YUNA"
        position={[2.9, 0, 19.2]}
        rotation={[0, -0.45, 0]}
        accent={palette.magenta}
        uniform="#503d47"
        skin="#b68572"
      />
    </group>
  );
}

function CommonModule() {
  return (
    <group>
      <FloorPlate position={[6.5, 0, 10]} size={[3.4, 2]} accent="#aaa89f" />
      <RoomShell
        center={[7, 15]}
        size={[7, 8]}
        accent="#aaa89f"
        entrance="north"
        code="CMN · 06"
        name="식당·라운지"
        secondaryEast
      />

      {[-0.95, 1.15].map((zOffset) => (
        <group key={zOffset} position={[6.1, 0, 15 + zOffset]}>
          <mesh position={[0, 0.75, 0]} castShadow>
            <cylinderGeometry args={[1.05, 1.05, 0.12, 18]} />
            <meshStandardMaterial color="#aaa79d" roughness={0.66} metalness={0.35} />
          </mesh>
          <mesh position={[0, 0.38, 0]} castShadow>
            <cylinderGeometry args={[0.18, 0.28, 0.76, 12]} />
            <meshStandardMaterial color="#434a49" metalness={0.65} roughness={0.48} />
          </mesh>
          {[-1.38, 1.38].map((x, index) => (
            <group key={x} position={[x, 0, 0]} rotation={[0, index === 0 ? -Math.PI / 2 : Math.PI / 2, 0]}>
              <mesh position={[0, 0.47, 0]} castShadow>
                <cylinderGeometry args={[0.38, 0.38, 0.12, 14]} />
                <meshStandardMaterial color="#606866" roughness={0.66} metalness={0.36} />
              </mesh>
              <mesh position={[0.37, 0.83, 0]} rotation={[0, 0, -0.1]} castShadow>
                <boxGeometry args={[0.12, 0.7, 0.68]} />
                <meshStandardMaterial color="#4c5554" roughness={0.68} metalness={0.4} />
              </mesh>
            </group>
          ))}
        </group>
      ))}

      <InteractiveGroup id="CMN_FOOD_STATION" position={[8.6, 0, 17.6]} markerPosition={[0, 2.7, 0]}>
        <RoundedBox args={[2.6, 2.45, 0.6]} radius={0.08} smoothness={3} position={[0, 1.23, 0]} castShadow>
          <meshStandardMaterial color="#b9b7ae" metalness={0.28} roughness={0.64} />
        </RoundedBox>
        <Screen position={[0, 1.56, 0.34]} size={[1.7, 0.58]} color="#d7bd87" />
        <mesh position={[0, 0.72, 0.33]}>
          <boxGeometry args={[1.75, 0.12, 0.05]} />
          <meshStandardMaterial color="#d7bd87" emissive="#d7bd87" emissiveIntensity={0.8} />
        </mesh>
      </InteractiveGroup>
    </group>
  );
}

function CrewQuarters() {
  const roomXs = [12.9, 15, 17.1];
  return (
    <group>
      <RoomShell center={[15, 15]} size={[9, 12]} accent="#858b87" entrance="west" code="QT · 07" name="승무원 숙소" />
      <FloorPlate position={[11.25, 0, 15]} size={[1.5, 3.4]} accent="#858b87" />

      {roomXs.map((x, index) => (
        <group key={`north-${x}`} position={[x, 0, 9.3]}>
          <DoorFrame position={[0, 0, 0]} width={1.65} accent={index === 0 ? palette.alert : "#858b87"} />
          <mesh position={[0, 1.45, 0.1]} castShadow>
            <boxGeometry args={[1.4, 2.65, 0.16]} />
            <meshStandardMaterial color="#282f31" metalness={0.64} roughness={0.52} />
          </mesh>
        </group>
      ))}
      {roomXs.map((x, index) => (
        <group key={`south-${x}`} position={[x, 0, 20.7]} rotation={[0, Math.PI, 0]}>
          <DoorFrame position={[0, 0, 0]} width={1.65} accent={index === 2 ? palette.mint : "#858b87"} />
          <mesh position={[0, 1.45, 0.1]} castShadow>
            <boxGeometry args={[1.4, 2.65, 0.16]} />
            <meshStandardMaterial color="#282f31" metalness={0.64} roughness={0.52} />
          </mesh>
        </group>
      ))}

      <InteractiveGroup
        id="QT_ACCESS_BUFFER"
        position={[10.72, 1.45, 13.4]}
        rotation={[0, -Math.PI / 2, 0]}
        markerPosition={[0, 1.2, 0]}
      >
        <RoundedBox args={[0.84, 1.25, 0.18]} radius={0.05} smoothness={3}>
          <meshStandardMaterial color="#252d2f" metalness={0.68} roughness={0.46} />
        </RoundedBox>
        <Screen position={[0, 0.13, 0.11]} size={[0.58, 0.62]} color="#aab2ad" />
      </InteractiveGroup>

      <CeilingStrip position={[15, 4.15, 15]} length={7.2} color="#c9d0cb" intensity={1.1} />
    </group>
  );
}

function ExecutiveCorridor() {
  return (
    <group>
      <FloorPlate position={[9, 0, -13.4]} size={[5, 9]} accent="#bcb6a9" />
      <StaticBox position={[6.5, 2.35, -13.5]} scale={[0.3, 4.7, 9]} />
      <StaticBox position={[11.5, 2.35, -13.5]} scale={[0.3, 4.7, 9]} />
      <CeilingStrip position={[9, 4.25, -13.5]} length={5.6} rotation={[0, Math.PI / 2, 0]} color="#d7d2c5" intensity={1.1} />
    </group>
  );
}

function StationCollisionFloor() {
  return (
    <RigidBody type="fixed" colliders={false}>
      <CuboidCollider args={[30, 1, 37.5]} position={[0, -1.03, 0]} />
      <mesh visible={false}>
        <boxGeometry args={[60, 0.5, 75]} />
        <meshBasicMaterial />
      </mesh>
    </RigidBody>
  );
}

export function StationWorld() {
  const world = useRef<InstancedMesh>(null);

  useLayoutEffect(() => {
    const root = world.current;
    if (!root) return;

    const originalAutoUpdates = new Map<Object3D, boolean>();
    root.traverse((object) => {
      object.updateMatrix();
      if (object.userData.dynamicTransform !== true) {
        originalAutoUpdates.set(object, object.matrixAutoUpdate);
        object.matrixAutoUpdate = false;
      }
    });
    root.updateWorldMatrix(true, true);

    return () => {
      originalAutoUpdates.forEach((matrixAutoUpdate, object) => {
        object.matrixAutoUpdate = matrixAutoUpdate;
      });
    };
  }, []);

  return (
    <FloorBaseInstances ref={world} limit={32} frames={1} receiveShadow>
      <boxGeometry />
      <meshStandardMaterial color={palette.floor} roughness={0.82} metalness={0.55} />
      <FloorPanelInstances limit={32} frames={1} receiveShadow>
        <planeGeometry />
        <meshStandardMaterial
          color="#d6dddc"
          map={getFloorPanelTexture()}
          roughness={0.74}
          metalness={0.42}
        />
        <CeilingHousingInstances limit={32} frames={1} castShadow>
          <boxGeometry />
          <meshStandardMaterial color="#232c2e" metalness={0.82} roughness={0.34} />
          <CeilingBracketInstances limit={64} frames={1} castShadow>
            <boxGeometry />
            <meshStandardMaterial color="#303a3c" metalness={0.86} roughness={0.3} />
            <DoorStructureInstances limit={96} frames={1} castShadow>
              <boxGeometry />
              <meshStandardMaterial
                color={palette.structureLight}
                metalness={0.76}
                roughness={0.42}
              />
              <DoorBadgeInstances limit={128} frames={1}>
                <boxGeometry />
                <meshStandardMaterial color="#566164" metalness={0.88} roughness={0.26} />
                <ScreenFrameInstances limit={32} frames={1} castShadow>
                  <boxGeometry />
                  <meshStandardMaterial color="#111819" metalness={0.82} roughness={0.3} />
                  <ScreenLineInstances limit={96} frames={1}>
                    <boxGeometry />
                    <meshBasicMaterial color="#ffffff" vertexColors />
                    <ScreenIndicatorInstances limit={32} frames={1}>
                      <circleGeometry args={[0.025, 10]} />
                      <meshBasicMaterial color="#d9aa62" />
                      <WallInstances limit={64} frames={1} castShadow receiveShadow>
                        <boxGeometry />
                        <meshStandardMaterial
                          color={palette.structure}
                          roughness={0.72}
                          metalness={0.62}
                        />
                        <WallPanelInstances limit={128} frames={1} receiveShadow>
                          <boxGeometry />
                          <meshStandardMaterial
                            color="#c7cfce"
                            map={getWallPanelTexture()}
                            metalness={0.56}
                            roughness={0.52}
                          />
                          <group>
                            <StationCollisionFloor />
                            <AtmosphereParticles />
                            <HubArchitecture />
                            <CommandCorridor />
                            <CommandRoom />
                            <ExecutiveCorridor />
                            <ExecutiveOffice />
                            <MedicalBay />
                            <EngineeringBay />
                            <CommunicationsRoom />
                            <CargoDock />
                            <CommonModule />
                            <CrewQuarters />
                          </group>
                        </WallPanelInstances>
                      </WallInstances>
                    </ScreenIndicatorInstances>
                  </ScreenLineInstances>
                </ScreenFrameInstances>
              </DoorBadgeInstances>
            </DoorStructureInstances>
          </CeilingBracketInstances>
        </CeilingHousingInstances>
      </FloorPanelInstances>
    </FloorBaseInstances>
  );
}
