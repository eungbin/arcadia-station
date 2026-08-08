import { SUSPECTS } from "./investigation";

/**
 * 인물 한 명.
 *
 * `SUSPECTS`는 서버 판정에 쓰는 최소 정보(이름·직책·색)만 갖고 있다. 2D로 오면서 화면에
 * 얼굴이 뜨기 시작했으므로 초상과 표시용 정보를 여기 모은다. 사건 로직은 여전히 `SUSPECTS`와
 * 서버 응답을 기준으로 돌고, 이 파일은 "어떻게 보이는가"만 책임진다.
 */
export type Character = {
  id: string;
  name: string;
  role: string;
  /**
   * 용의자만 재판과 배제 논증에 오른다. 피해자와 플레이어가 그 목록에 섞이면
   * 서버가 모르는 ID를 제출하게 되므로 종류를 명시적으로 갈라 둔다.
   */
  kind: "SUSPECT" | "VICTIM" | "PLAYER";
  /** 심문·재판·수첩에 쓰는 초상. `scripts/slice-portraits.py`가 만든다. */
  portrait: string;
  accent: string;
  /**
   * 초상에서 얼굴이 있는 자리. 0~1 비율이며 `r`은 세로 기준 반지름이다.
   *
   * 정거장을 걸어 다닐 때 이 부분만 동그랗게 잘라 머리로 쓴다. 인물마다 초상 안에서
   * 얼굴 위치가 달라서(앉아 있거나 손으로 가리거나) 한 값으로는 맞지 않는다.
   */
  face: { x: number; y: number; r: number };
};

const portrait = (file: string) => `/assets/characters/${file}.webp`;

/** 인물별 얼굴 위치. 잘라 본 결과를 눈으로 확인해서 잡았다. */
const FACES: Record<string, Character["face"]> = {
  PLAYER: { x: 0.42, y: 0.16, r: 0.14 },
  MAYA: { x: 0.49, y: 0.185, r: 0.13 },
  JUNHO: { x: 0.55, y: 0.2, r: 0.14 },
  SOPHIA: { x: 0.55, y: 0.205, r: 0.13 },
  KASIM: { x: 0.47, y: 0.19, r: 0.135 },
  YUNA: { x: 0.5, y: 0.185, r: 0.135 },
  ROSS: { x: 0.55, y: 0.205, r: 0.125 },
};

const DEFAULT_FACE = { x: 0.5, y: 0.19, r: 0.13 };

/** 용의자가 아닌 인물. 사건 로직이 아니라 화면에만 등장한다. */
const NON_SUSPECTS: Character[] = [
  {
    id: "PLAYER",
    name: "보안담당관",
    role: "정거장 보안",
    kind: "PLAYER",
    portrait: portrait("player"),
    accent: "#8ce0c8",
    face: FACES.PLAYER,
  },
  {
    id: "ROSS",
    name: "다니엘 로스",
    role: "사령관",
    kind: "VICTIM",
    portrait: portrait("ross"),
    accent: "#d65a43",
    face: FACES.ROSS,
  },
];

/** 용의자 초상. 파일명은 `SUSPECTS[].id`를 소문자로 바꾼 것과 같다. */
export const CHARACTERS: Record<string, Character> = Object.fromEntries(
  [
    ...SUSPECTS.map<Character>((suspect) => ({
      id: suspect.id,
      name: suspect.name,
      role: suspect.role,
      kind: "SUSPECT",
      portrait: portrait(suspect.id.toLowerCase()),
      accent: suspect.color,
      face: FACES[suspect.id] ?? DEFAULT_FACE,
    })),
    ...NON_SUSPECTS,
  ].map((character) => [character.id, character]),
);

export const PLAYER = CHARACTERS.PLAYER;

/**
 * 3D 소품 ID를 인물 ID로 옮긴다.
 *
 * 현장에 서 있는 승무원은 `NPC_MAYA`처럼 조사 오브젝트 ID로 식별되지만, 서버 판정과 재판은
 * `MAYA`를 쓴다. 두 이름이 섞이면 초상이 뜨지 않거나 엉뚱한 사람이 뜬다.
 */
export function characterIdFromNpc(npcId: string): string {
  return npcId.startsWith("NPC_") ? npcId.slice(4) : npcId;
}

/**
 * 초상 경로. 인물 ID와 NPC 오브젝트 ID를 모두 받는다.
 *
 * 서버가 정적 로스터에 없는 인물을 돌려줄 수 있으므로 없으면 `null`을 준다. 부르는 쪽은
 * 이니셜 폴백으로 물러난다.
 */
export function portraitFor(id: string): string | null {
  return CHARACTERS[characterIdFromNpc(id)]?.portrait ?? null;
}

/** 화면에 쓸 인물 정보. 모르는 ID면 ID를 이름으로 삼아 화면이 비지 않게 한다. */
export function characterFor(id: string): Character {
  const key = characterIdFromNpc(id);
  return (
    CHARACTERS[key] ?? {
      id: key,
      name: key,
      role: "승무원",
      kind: "SUSPECT",
      portrait: "",
      accent: "#8f86e8",
      face: DEFAULT_FACE,
    }
  );
}

/**
 * 초상을 미리 받아 둔다.
 *
 * 심문 창이 열리는 순간 회색 사각형이 보이지 않게 하는 것이 첫째고, 정거장 화면이
 * 인물 머리를 그릴 때 바로 쓸 수 있게 하는 것이 둘째다. 캔버스는 로딩을 기다릴 수 없어서
 * 받아 둔 이미지가 없으면 단색 원으로 물러난다.
 */
const loaded = new Map<string, HTMLImageElement>();

export function preloadPortraits(): void {
  for (const character of Object.values(CHARACTERS)) {
    if (!character.portrait || loaded.has(character.id)) continue;
    const image = new Image();
    image.decoding = "async";
    image.src = character.portrait;
    loaded.set(character.id, image);
  }
}

/** 그릴 준비가 끝난 초상. 아직이면 `null`. */
export function loadedPortrait(id: string): HTMLImageElement | null {
  const image = loaded.get(characterIdFromNpc(id));
  return image?.complete && image.naturalWidth > 0 ? image : null;
}
