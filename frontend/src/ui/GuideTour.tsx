import { useCallback, useEffect, useLayoutEffect, useState } from "react";
import { useSettingsStore } from "../store/settingsStore";

type Placement = "top" | "bottom" | "left" | "right" | "center";

type TourStep = {
  title: string;
  body: string;
  /** 짚어 줄 HUD 요소. 없으면 화면 가운데에 설명만 띄운다. */
  target?: string;
  placement?: Placement;
};

/**
 * 처음 플레이하는 사람에게 실제 화면 요소를 하나씩 짚어 주는 단계별 안내.
 *
 * 각 단계는 `data-tour` 속성으로 HUD 요소를 찾는다. 클래스명 대신 전용 속성을 쓰는 이유는
 * 스타일을 바꿔도 안내가 따라 깨지지 않게 하려는 것이다.
 */
const TOUR_STEPS: TourStep[] = [
  {
    title: "목표 패널 — 지금 할 일은 여기에",
    body:
      "현재 목표와 필수 기록 진행도가 항상 이 패널에 표시됩니다. 무엇을 해야 할지 막히면 여기를 먼저 확인하세요.",
    target: '[data-tour="objective"]',
    placement: "right",
  },
  {
    title: "이동과 시선",
    body:
      "W A S D로 걷고 마우스로 둘러봅니다. 화면을 한 번 클릭하면 시선이 고정되고, 방향키로도 움직일 수 있습니다.",
    placement: "center",
  },
  {
    title: "조준점 — 보고 있는 것을 조사한다",
    body:
      "화면 가운데 조준점을 오브젝트나 인물에 맞추면 이름이 뜹니다. 그때 E를 누르면 조사하거나 말을 겁니다.",
    target: '[data-tour="crosshair"]',
    placement: "bottom",
  },
  {
    title: "조사 스캔 — 무엇을 조사할지 모를 때",
    body:
      "Q를 누르면 주변에서 조사할 수 있는 대상이 3초간 표시됩니다. 사건마다 단서 배치가 달라 아무것도 나오지 않는 오브젝트도 있습니다.",
    target: '[data-tour="scan"]',
    placement: "top",
  },
  {
    title: "용의자 심문",
    body:
      "각 구역에 흩어진 승무원에게 E로 말을 겁니다. 준비된 질문을 고르거나 직접 질문을 입력할 수 있고, 확보한 증거를 제시하면 진술이 바뀝니다. 1일차에는 최소 3명을 심문해야 합니다.",
    placement: "center",
  },
  {
    title: "사건 수첩 — 모은 것을 정리한다",
    body:
      "TAB으로 수첩을 엽니다. 증거·타임라인·용의자·수사 보조(기록 검색)·사건 재구성 다섯 탭이 있습니다.",
    target: '[data-tour="notebook"]',
    placement: "top",
  },
  {
    title: "사건 재구성과 재판",
    body:
      "수첩의 사건 재구성에서 범인을 지목하고 준비·실행·기회·동기를 각각 서로 다른 증거로 연결합니다. 나머지 용의자를 배제할 근거까지 갖추면 재판을 열 수 있습니다. 정답은 직접 확보한 기록으로만 인정됩니다.",
    placement: "center",
  },
  {
    title: "다시 보기와 설정",
    body:
      "이 안내는 ? 키나 안내 버튼으로 언제든 다시 볼 수 있습니다. ESC를 누르면 설정이 열립니다.",
    target: '[data-tour="system"]',
    placement: "left",
  },
];

const CARD_WIDTH = 344;
/** 카드 높이를 재지 않고 화면 안으로 넣기 위한 상한. CSS의 max-height와 같이 유지한다. */
const CARD_MAX_HEIGHT = 260;
const GAP = 14;
const EDGE = 16;

type CardPosition = {
  left?: number;
  top?: number;
  right?: number;
  bottom?: number;
};

const clamp = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), Math.max(min, max));

function cardPosition(rect: DOMRect | null, placement: Placement): CardPosition {
  const viewportWidth = window.innerWidth;
  const viewportHeight = window.innerHeight;
  const maxLeft = viewportWidth - CARD_WIDTH - EDGE;
  const maxTop = viewportHeight - CARD_MAX_HEIGHT - EDGE;

  if (!rect || placement === "center") {
    return {
      left: Math.max(EDGE, (viewportWidth - CARD_WIDTH) / 2),
      top: Math.max(EDGE, viewportHeight * 0.3),
    };
  }

  const centeredLeft = clamp(
    rect.left + rect.width / 2 - CARD_WIDTH / 2,
    EDGE,
    maxLeft,
  );

  switch (placement) {
    case "bottom":
      return { left: centeredLeft, top: clamp(rect.bottom + GAP, EDGE, maxTop) };
    case "top":
      return {
        left: centeredLeft,
        bottom: clamp(viewportHeight - rect.top + GAP, EDGE, viewportHeight - EDGE),
      };
    case "right":
      return {
        left: clamp(rect.right + GAP, EDGE, maxLeft),
        top: clamp(rect.top, EDGE, maxTop),
      };
    case "left":
    default:
      return {
        right: clamp(viewportWidth - rect.left + GAP, EDGE, viewportWidth - EDGE),
        top: clamp(rect.top, EDGE, maxTop),
      };
  }
}

export function GuideTour() {
  const closeGuide = useSettingsStore((state) => state.closeGuide);
  const [index, setIndex] = useState(0);
  const [rect, setRect] = useState<DOMRect | null>(null);

  const step = TOUR_STEPS[index];
  const isLast = index === TOUR_STEPS.length - 1;

  const measure = useCallback(() => {
    if (!step.target) {
      setRect(null);
      return;
    }
    const element = document.querySelector(step.target);
    setRect(element ? element.getBoundingClientRect() : null);
  }, [step.target]);

  // 단계가 바뀌면 대상 위치를 다시 잡는다. 창 크기가 변할 때도 따라가야 한다.
  useLayoutEffect(() => {
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [measure]);

  const goNext = useCallback(() => {
    if (isLast) {
      closeGuide();
      return;
    }
    setIndex((current) => current + 1);
  }, [closeGuide, isLast]);

  const goBack = useCallback(() => setIndex((current) => Math.max(0, current - 1)), []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.code === "Escape") {
        closeGuide();
        return;
      }
      if (event.code === "ArrowRight" || event.code === "Enter" || event.code === "Space") {
        event.preventDefault();
        goNext();
        return;
      }
      if (event.code === "ArrowLeft") {
        event.preventDefault();
        goBack();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [closeGuide, goBack, goNext]);

  const position = cardPosition(rect, step.placement ?? "center");
  // 대상만 남기고 주변을 덮는다. 사각형 네 장이라 잘라내기 없이도 강조가 된다.
  const scrims = rect
    ? [
        { key: "top", top: 0, left: 0, width: "100%", height: Math.max(0, rect.top) },
        {
          key: "bottom",
          top: rect.bottom,
          left: 0,
          width: "100%",
          height: Math.max(0, window.innerHeight - rect.bottom),
        },
        { key: "left", top: rect.top, left: 0, width: Math.max(0, rect.left), height: rect.height },
        {
          key: "right",
          top: rect.top,
          left: rect.right,
          width: Math.max(0, window.innerWidth - rect.right),
          height: rect.height,
        },
      ]
    : [{ key: "all", top: 0, left: 0, width: "100%", height: "100%" }];

  return (
    <section
      className="tour-layer"
      role="dialog"
      aria-modal="true"
      aria-label="플레이 안내"
      data-tour-step={index + 1}
    >
      {scrims.map((scrim) => (
        <div
          key={scrim.key}
          className="tour-scrim"
          style={{
            top: scrim.top,
            left: scrim.left,
            width: scrim.width,
            height: scrim.height,
          }}
        />
      ))}

      {rect && (
        <div
          className="tour-highlight"
          style={{
            top: rect.top - 4,
            left: rect.left - 4,
            width: rect.width + 8,
            height: rect.height + 8,
          }}
        />
      )}

      <article className="tour-card" style={position}>
        <header>
          <span className="tour-count">
            {index + 1} / {TOUR_STEPS.length}
          </span>
          <div className="tour-dots">
            {TOUR_STEPS.map((tourStep, dotIndex) => (
              <i
                key={tourStep.title}
                className={dotIndex === index ? "is-current" : dotIndex < index ? "is-done" : ""}
              />
            ))}
          </div>
        </header>
        <h2>
          <em>{index + 1}</em>
          {step.title}
        </h2>
        <p>{step.body}</p>
        <footer>
          <button className="tour-skip" type="button" onClick={closeGuide}>
            건너뛰기
          </button>
          <div className="tour-nav">
            {index > 0 && (
              <button className="tour-back" type="button" onClick={goBack}>
                이전
              </button>
            )}
            <button className="tour-next" type="button" onClick={goNext}>
              {isLast ? "시작하기" : "다음"}
            </button>
          </div>
        </footer>
      </article>
    </section>
  );
}
