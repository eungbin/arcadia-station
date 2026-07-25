import { useFrame, useThree } from "@react-three/fiber";
import { Raycaster, Vector2, type Object3D } from "three";
import { useGameStore } from "../store/gameStore";

const raycaster = new Raycaster();
const center = new Vector2(0, 0);

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

  useFrame(() => {
    const state = useGameStore.getState();
    if (state.layer !== "playing") {
      state.setFocused(null);
      return;
    }

    raycaster.setFromCamera(center, camera);
    raycaster.far = 2.8;
    const firstHit = raycaster.intersectObjects(scene.children, true)[0];
    state.setFocused(firstHit ? findInteractionId(firstHit.object) : null);
  });

  return null;
}
