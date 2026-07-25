import { PointerLockControls } from "@react-three/drei";
import { CapsuleCollider, RigidBody, type RapierRigidBody } from "@react-three/rapier";
import { useFrame, useThree } from "@react-three/fiber";
import { useEffect, useRef } from "react";
import { MathUtils, Vector3 } from "three";
import { useGameStore } from "../store/gameStore";
import { useSettingsStore } from "../store/settingsStore";

const forwardVector = new Vector3();
const rightVector = new Vector3();
const movementVector = new Vector3();
const upVector = new Vector3(0, 1, 0);
const velocityEpsilon = 0.0001;

export function PlayerController() {
  const body = useRef<RapierRigidBody>(null);
  const pressedKeys = useRef(new Set<string>());
  const camera = useThree((state) => state.camera);
  const gl = useThree((state) => state.gl);
  const scene = useThree((state) => state.scene);
  const layer = useGameStore((state) => state.layer);
  const settingsOpen = useSettingsStore((state) => state.open);
  const mouseSensitivity = useSettingsStore((state) => state.mouseSensitivity);
  const markMoved = useGameStore((state) => state.markMoved);

  useEffect(() => {
    if (layer !== "playing") document.exitPointerLock?.();
  }, [layer]);

  useEffect(() => {
    const press = (event: KeyboardEvent) => pressedKeys.current.add(event.code);
    const release = (event: KeyboardEvent) => pressedKeys.current.delete(event.code);
    const virtualMove = (event: Event) => {
      const { code, pressed } = (event as CustomEvent<{ code: string; pressed: boolean }>).detail;
      if (pressed) pressedKeys.current.add(code);
      else pressedKeys.current.delete(code);
    };
    const virtualLook = (event: Event) => {
      if (layer !== "playing" || settingsOpen) return;
      const { x, y } = (event as CustomEvent<{ x: number; y: number }>).detail;
      camera.rotation.order = "YXZ";
      camera.rotation.y -= x * 0.003 * mouseSensitivity;
      camera.rotation.x = MathUtils.clamp(
        camera.rotation.x - y * 0.003 * mouseSensitivity,
        -Math.PI / 2 + 0.05,
        Math.PI / 2 - 0.05,
      );
    };
    const clear = () => pressedKeys.current.clear();
    window.addEventListener("keydown", press);
    window.addEventListener("keyup", release);
    window.addEventListener("blur", clear);
    window.addEventListener("arcadia:move", virtualMove);
    window.addEventListener("arcadia:look", virtualLook);
    return () => {
      window.removeEventListener("keydown", press);
      window.removeEventListener("keyup", release);
      window.removeEventListener("blur", clear);
      window.removeEventListener("arcadia:move", virtualMove);
      window.removeEventListener("arcadia:look", virtualLook);
    };
  }, [camera, layer, mouseSensitivity, settingsOpen]);

  useFrame(() => {
    if (!body.current) return;

    let position = body.current.translation();
    if (position.y < -1) {
      body.current.setTranslation({ x: 0, y: 1.05, z: 6 }, true);
      body.current.setLinvel({ x: 0, y: 0, z: 0 }, true);
      position = body.current.translation();
    }
    camera.position.set(position.x, position.y + 0.68, position.z);
    if (import.meta.env.DEV) {
      camera.getWorldDirection(forwardVector);
      gl.domElement.dataset.cameraPosition =
        `${camera.position.x.toFixed(2)},${camera.position.y.toFixed(2)},${camera.position.z.toFixed(2)}`;
      gl.domElement.dataset.cameraDirection =
        `${forwardVector.x.toFixed(2)},${forwardVector.y.toFixed(2)},${forwardVector.z.toFixed(2)}`;
      gl.domElement.dataset.renderStats = JSON.stringify({
        calls: gl.info.render.calls,
        triangles: gl.info.render.triangles,
        sceneChildren: scene.children.length,
      });
    }

    if (layer !== "playing") {
      const velocity = body.current.linvel();
      if (Math.abs(velocity.x) > velocityEpsilon || Math.abs(velocity.z) > velocityEpsilon) {
        body.current.setLinvel({ x: 0, y: velocity.y, z: 0 }, true);
      }
      return;
    }

    const forward = pressedKeys.current.has("KeyW") || pressedKeys.current.has("ArrowUp");
    const backward = pressedKeys.current.has("KeyS") || pressedKeys.current.has("ArrowDown");
    const left = pressedKeys.current.has("KeyA") || pressedKeys.current.has("ArrowLeft");
    const right = pressedKeys.current.has("KeyD") || pressedKeys.current.has("ArrowRight");
    const xAxis = Number(right) - Number(left);
    const zAxis = Number(forward) - Number(backward);

    camera.getWorldDirection(forwardVector);
    forwardVector.y = 0;
    forwardVector.normalize();
    rightVector.crossVectors(forwardVector, upVector).normalize();

    movementVector
      .set(0, 0, 0)
      .addScaledVector(forwardVector, zAxis)
      .addScaledVector(rightVector, xAxis);

    const hasMovement = movementVector.lengthSq() > 0;
    if (hasMovement) {
      movementVector.normalize().multiplyScalar(3.25);
      markMoved();
    }

    const velocity = body.current.linvel();
    if (
      hasMovement ||
      Math.abs(velocity.x) > velocityEpsilon ||
      Math.abs(velocity.z) > velocityEpsilon
    ) {
      body.current.setLinvel(
        { x: movementVector.x, y: velocity.y, z: movementVector.z },
        true,
      );
    }
  });

  return (
    <>
      <RigidBody
        ref={body}
        colliders={false}
        enabledRotations={[false, false, false]}
        position={[0, 1.05, 6]}
        friction={0}
        linearDamping={8}
        ccd
      >
        <CapsuleCollider args={[0.52, 0.34]} />
      </RigidBody>
      <PointerLockControls
        enabled={layer === "playing" && !settingsOpen}
        pointerSpeed={mouseSensitivity}
        selector=".scene-canvas"
      />
    </>
  );
}
