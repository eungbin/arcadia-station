import { useFrame, useThree } from "@react-three/fiber";
import { useEffect, useRef } from "react";
import {
  Box3,
  Raycaster,
  Sphere,
  Vector2,
  Vector3,
  Object3D,
  type Intersection,
} from "three";
import { useGameStore } from "../store/gameStore";

const raycaster = new Raycaster();
const center = new Vector2(0, 0);
const markerPosition = new Vector3();
const intersections: Intersection[] = [];

function findInteractionId(object: Object3D | null): string | null {
  let current = object;
  while (current) {
    if (typeof current.userData.interactionId === "string") {
      return current.userData.interactionId;
    }
    current = current.parent;
  }
  return null;
}

export function InteractionController() {
  const { camera, scene } = useThree();
  const interactionBounds = useRef<Sphere[]>([]);
  const raycastTargets = useRef<Object3D[]>([]);

  useEffect(() => {
    const bounds: Sphere[] = [];
    const targets: Object3D[] = [];

    scene.traverse((object) => {
      if (object.raycast !== Object3D.prototype.raycast) {
        targets.push(object);
      }
      if (typeof object.userData.interactionId !== "string") return;

      const sphere = new Box3().setFromObject(object).getBoundingSphere(new Sphere());
      const marker = object.userData.interactionMarker;
      if (Array.isArray(marker) && marker.length === 3) {
        markerPosition.fromArray(marker).applyMatrix4(object.matrixWorld);
        sphere.radius = Math.max(
          sphere.radius,
          sphere.center.distanceTo(markerPosition) + 0.2,
        );
      }
      sphere.radius += 0.25;
      bounds.push(sphere);
    });

    interactionBounds.current = bounds;
    raycastTargets.current = targets;
    return () => {
      interactionBounds.current = [];
      raycastTargets.current = [];
    };
  }, [scene]);

  useFrame(() => {
    const state = useGameStore.getState();
    if (state.layer !== "playing") {
      state.setFocused(null);
      return;
    }

    raycaster.setFromCamera(center, camera);
    raycaster.far = 2.8;
    const canHitInteraction =
      interactionBounds.current.length === 0 ||
      interactionBounds.current.some(
        (sphere) =>
          raycaster.ray.origin.distanceTo(sphere.center) - sphere.radius <= raycaster.far &&
          raycaster.ray.intersectsSphere(sphere),
      );
    if (!canHitInteraction) {
      state.setFocused(null);
      return;
    }

    raycaster.intersectObjects(raycastTargets.current, false, intersections);
    const firstHit = intersections.find(({ object }) => {
      let current: Object3D | null = object;
      while (current) {
        if (!current.visible) return false;
        current = current.parent;
      }
      return true;
    });
    state.setFocused(firstHit ? findInteractionId(firstHit.object) : null);
    intersections.length = 0;
  });

  return null;
}
