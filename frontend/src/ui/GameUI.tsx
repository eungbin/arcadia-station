import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import {
  INVESTIGATION_OBJECTS,
  NPC_DIALOGUE,
  SUSPECTS,
  TIMELINE,
  type TargetKind,
} from "../data/investigation";
import {
  getRequiredProgress,
  useGameStore,
  type NotebookTab,
} from "../store/gameStore";
import { useSettingsStore } from "../store/settingsStore";
import {
  useCompleteDay,
  useCreateSession,
  useAskAssistant,
  useCaseState,
  useInspectObject,
  useInterrogationSession,
  useSaveTheory,
  useSendInterrogationMessage,
  useSubmitVerdict,
} from "../api/hooks";
import { validateTheory } from "../domain/theoryValidation";
import type { DiscoveredEvidence, EvidenceType } from "../api/contracts";

/** 3D 소품의 종류. 물리 계층 표시에만 쓴다. */
const KIND_LABELS: Record<TargetKind, string> = {
  PHYSICAL: "물리 단서",
  DIGITAL: "디지털 기록",
  MOTIVE: "동기 자료",
  WORLD: "구조 정보",
  PERSON: "용의자 심문",
};

/** 서버 단서의 종류. 수첩과 이론 구성에 쓴다. */
const EVIDENCE_TYPE_LABELS: Record<EvidenceType, string> = {
  PHYSICAL: "물리 증거",
  DIGITAL: "디지털 기록",
  MOTIVE: "동기 자료",
  OPPORTUNITY: "기회와 권한",
};

/** 서버 용의자 ID를 화면 표시용 정보로 옮긴다. 정적 로스터에 없으면 ID를 그대로 쓴다. */
function suspectProfile(id: string) {
  return (
    SUSPECTS.find((suspect) => suspect.id === id) ?? {
      id,
      name: id,
      role: "승무원",
      color: "#8f86e8",
    }
  );
}

/** 이 사건의 용의자 목록. 서버가 알려준 명단을 우선한다. */
function useSuspects() {
  const suspectIds = useGameStore((state) => state.suspectIds);
  return useMemo(
    () => (suspectIds.length > 0 ? suspectIds.map(suspectProfile) : SUSPECTS),
    [suspectIds],
  );
}

function OpeningOverlay() {
  const beginInvestigation = useGameStore((state) => state.beginInvestigation);
  const syncCaseState = useGameStore((state) => state.syncCaseState);
  const caseBriefing = useGameStore((state) => state.caseBriefing);
  const createSession = useCreateSession();

  const enterStation = () => {
    createSession.mutate(undefined, {
      onSuccess: ({ session, caseState }) => {
        syncCaseState(caseState);
        beginInvestigation(session.sessionId, session.version);
      },
    });
  };

  return (
    <section className="opening-overlay" aria-label="사건 브리핑">
      <div className="opening-noise" />
      <header className="opening-header">
        <span>CORVIS CONSORTIUM // SECURITY CHANNEL 04</span>
        <span className="status-live"><i /> LIVE</span>
      </header>

      <div className="opening-content">
        <p className="opening-kicker">격리 프로토콜 발령 · D1 07:24</p>
        <h1>
          ARCADIA
          <span>INCIDENT 72</span>
        </h1>
        <div className="opening-rule" />
        {/* 사건 개요는 서버가 세션마다 생성한다. 아직 못 받았으면 격리 상황 안내를 보여준다. */}
        <p className="opening-lead">
          {caseBriefing ?? (
            <>
              사령관 다니엘 로스가 사망했다.
              <br />
              구조선 도착까지 남은 시간은 72시간.
            </>
          )}
        </p>

        <dl className="brief-grid">
          <div>
            <dt>현장</dt>
            <dd>사령관실 CO · 01</dd>
          </div>
          <div>
            <dt>최초 발견자</dt>
            <dd>마야 헨드릭스</dd>
          </div>
          <div>
            <dt>잔류 인원</dt>
            <dd>생존 6명</dd>
          </div>
          <div>
            <dt>외부 통신</dt>
            <dd className="danger-text">완전 두절</dd>
          </div>
        </dl>

        <button
          className="primary-action"
          type="button"
          disabled={createSession.isPending}
          onClick={enterStation}
        >
          <span>{createSession.isPending ? "격리 채널 동기화 중" : "보안 권한으로 현장 진입"}</span>
          <kbd>{createSession.isPending ? "..." : "ENTER"}</kbd>
        </button>
        {createSession.isError && (
          <div className="opening-error" role="alert">
            <span>CONNECTION REFUSED</span>
            <p>{createSession.error.message}</p>
            <button type="button" onClick={enterStation}>채널 재시도</button>
          </div>
        )}
        <p className="opening-footnote">
          사건 기록 ARK-D1-0724 · 모든 조사 행위가 로컬 보안 장치에 기록됩니다.
        </p>
      </div>

      <aside className="opening-call">
        <div className="call-avatar">MH</div>
        <div>
          <span>긴급 호출 수신</span>
          <strong>마야 헨드릭스</strong>
          <p>“보안담당관, 즉시 사령관실로 와주세요.”</p>
        </div>
      </aside>

      <div className="opening-index">072</div>
    </section>
  );
}

function MissionHud() {
  const openSettings = useSettingsStore((state) => state.setOpen);
  const focusedId = useGameStore((state) => state.focusedId);
  const discoveredIds = useGameStore((state) => state.discoveredIds);
  const hasMoved = useGameStore((state) => state.hasMoved);
  const interviewedIds = useGameStore((state) => state.interviewedIds);
  const phase = useGameStore((state) => state.phase);
  const activateScan = useGameStore((state) => state.activateScan);
  const toggleNotebook = useGameStore((state) => state.toggleNotebook);
  const openDayReview = useGameStore((state) => state.openDayReview);
  const progress = getRequiredProgress(discoveredIds);
  const readyForReview = progress.complete && interviewedIds.length >= 3;
  const focused = focusedId ? INVESTIGATION_OBJECTS[focusedId] : null;

  return (
    <div className="hud-layer">
      <div className="hud-topline">
        <div className="hud-brand">
          <i />
          <div>
            <span>ARK SECURITY</span>
            <strong>INCIDENT // 072</strong>
          </div>
        </div>
        <div className="hud-time">
          <span>구조선 도착 예상</span>
          <strong>71 : 42 : 18</strong>
        </div>
      </div>

      <aside className="objective-panel">
        <header>
          <span>PRIMARY OBJECTIVE</span>
          <em>{phase}</em>
        </header>
        <h2>
          {phase === "DAY2"
            ? "기록 교차 검증"
            : readyForReview
              ? "D1 조사 요건 충족"
              : progress.complete
                ? "용의자 1차 진술 확보"
                : "사령관실 현장 보존"}
        </h2>
        <p>
          {phase === "DAY2"
            ? "현장 단서와 각 시스템 원본을 비교해 모순을 찾으십시오."
            : readyForReview
              ? "확보한 자료를 정리하고 심층 조사로 전환할 수 있습니다."
              : progress.complete
                ? `용의자 진술 ${interviewedIds.length} / 3 · 담당 구역에서 직접 심문하십시오.`
                : "피해자와 현장 시스템에서 필수 기록을 확보하십시오."}
        </p>
        <div className="objective-progress">
          <span style={{ width: `${(progress.found / progress.total) * 100}%` }} />
        </div>
        <small>
          필수 기록 {String(progress.found).padStart(2, "0")} / {String(progress.total).padStart(2, "0")}
        </small>
        {phase === "DAY1" && readyForReview && (
          <button className="day-review-action" type="button" onClick={openDayReview}>
            D1 조사 정리
          </button>
        )}
      </aside>

      <div className={`crosshair ${focused ? "is-focused" : ""}`}>
        <i />
        <i />
        <i />
        <i />
      </div>

      {focused && (
        <div className="interaction-prompt">
          <kbd>E</kbd>
          <div>
            <span>{KIND_LABELS[focused.kind]}</span>
            <strong>{focused.title}</strong>
          </div>
        </div>
      )}

      <div className="hud-controls">
        {!hasMoved && <span><kbd>WASD</kbd> 이동</span>}
        <button type="button" onClick={activateScan}><kbd>Q</kbd> 조사 스캔</button>
        <button className="notebook-trigger" type="button" onClick={toggleNotebook}>
          <kbd>TAB</kbd> 사건 수첩
        </button>
      </div>

      <button
        className="settings-trigger"
        type="button"
        aria-label="설정 열기"
        onClick={() => openSettings(true)}
      >
        SYS <kbd>ESC</kbd>
      </button>

      <div className="signal-meter">
        <span>EXTERNAL LINK</span>
        <div><i /><i /><i /><i /><i /></div>
        <strong>NO SIGNAL</strong>
      </div>
    </div>
  );
}

/**
 * 조사 패널.
 *
 * 오브젝트의 이름과 위치는 3D 소품의 물리적 정체성이라 프런트엔드가 갖고 있지만, 조사해서
 * 얻는 기록은 전부 서버가 생성한 사건 단서다. 사건마다 단서 배치가 달라 아무것도 나오지 않는
 * 오브젝트가 있고, 그건 오류가 아니다.
 */
function InspectionPanel() {
  const sessionId = useGameStore((state) => state.sessionId);
  const updateSessionVersion = useGameStore((state) => state.updateSessionVersion);
  const selectedId = useGameStore((state) => state.selectedId);
  const discoveredIds = useGameStore((state) => state.discoveredIds);
  const evidence = useGameStore((state) => state.evidence);
  const closeOverlay = useGameStore((state) => state.closeOverlay);
  const markInspected = useGameStore((state) => state.markInspected);
  const recordEvidence = useGameStore((state) => state.recordEvidence);
  const inspectObject = useInspectObject(sessionId);
  const item = selectedId ? INVESTIGATION_OBJECTS[selectedId] : null;

  if (!item) return null;

  const isInspected = discoveredIds.includes(item.id);
  const found = evidence.filter((record) => record.sourceObjectId === item.id);

  return (
    <section className="inspection-shell" aria-label={`${item.title} 조사`}>
      <button className="overlay-scrim" aria-label="조사 화면 닫기" onClick={closeOverlay} />
      <article className="inspection-card">
        <header className="inspection-card__head">
          <div>
            <span>{item.eyebrow}</span>
            <small>{item.zone} // {item.id}</small>
          </div>
          <button type="button" onClick={closeOverlay} aria-label="닫기">×</button>
        </header>

        <div className="inspection-visual">
          <div className={`evidence-glyph evidence-glyph--${item.kind.toLowerCase()}`}>
            <i />
            <span>{item.id.slice(-2)}</span>
          </div>
          <div className="scan-lines" />
          <span className="inspection-kind">{KIND_LABELS[item.kind]}</span>
        </div>

        <div className="inspection-copy">
          <p className="inspection-index">OBSERVATION // {item.id}</p>
          <h2>{item.title}</h2>

          {!isInspected && (
            <p className="inspection-summary">
              아직 조사하지 않았습니다. 정밀 조사로 사건 기록을 확인하십시오.
            </p>
          )}

          {isInspected && found.length === 0 && (
            <p className="inspection-summary">
              이곳에서는 이번 사건과 연결되는 기록을 찾지 못했습니다. 새 기록을 확보한 뒤
              다시 조사하면 놓쳤던 흔적이 보일 수 있습니다.
            </p>
          )}

          {found.map((record) => (
            <div className="inspection-detail" key={record.clueId}>
              <span>{EVIDENCE_TYPE_LABELS[record.clueType]} · {record.title}</span>
              <p>{record.playerText}</p>
            </div>
          ))}
        </div>

        <footer>
          <div>
            <span>확보 기록</span>
            <strong>{isInspected ? `${found.length}건` : "조사 전"}</strong>
          </div>
          {/* 검색으로 선행 기록을 얻으면 이미 조사한 방에서 새 흔적이 열릴 수 있어 재조사를 막지 않는다. */}
          <button
            className={isInspected ? "record-action is-recorded" : "record-action"}
            type="button"
            disabled={inspectObject.isPending}
            onClick={() =>
              inspectObject.mutate(item.id, {
                onSuccess: (result) => {
                  updateSessionVersion(result.version);
                  recordEvidence(result.discoveredEvidence);
                  markInspected(item.id);
                },
              })
            }
          >
            {inspectObject.isPending
              ? "보안 기록 조회 중"
              : isInspected
                ? "다시 조사"
                : "정밀 조사"}
          </button>
          {inspectObject.isError && (
            <small className="inline-error" role="alert">{inspectObject.error.message}</small>
          )}
        </footer>
      </article>
    </section>
  );
}

const NOTEBOOK_TABS: Array<{ id: NotebookTab; label: string; index: string }> = [
  { id: "evidence", label: "증거", index: "01" },
  { id: "timeline", label: "타임라인", index: "02" },
  { id: "suspects", label: "용의자", index: "03" },
  { id: "assistant", label: "수사 보조", index: "04" },
  { id: "theory", label: "사건 재구성", index: "05" },
];

function EvidenceTab({ evidence }: { evidence: DiscoveredEvidence[] }) {
  if (evidence.length === 0) {
    return (
      <div className="notebook-empty">
        <span>NO EVIDENCE LOGGED</span>
        <h3>확보한 기록이 없습니다.</h3>
        <p>현장 오브젝트를 조사하거나 수사 보조로 사건 기록을 검색하십시오.</p>
      </div>
    );
  }

  return (
    <div className="evidence-grid">
      {evidence.map((record, index) => {
        const source = record.sourceObjectId
          ? INVESTIGATION_OBJECTS[record.sourceObjectId]
          : null;
        const kindClass = record.clueType.toLowerCase();
        return (
          <article className="evidence-card" key={record.clueId}>
            <header>
              <span>{String(index + 1).padStart(2, "0")}</span>
              <em>{EVIDENCE_TYPE_LABELS[record.clueType]}</em>
            </header>
            <div className={`evidence-card__mark evidence-card__mark--${kindClass}`} />
            <small>{source ? `${source.zone} · ${source.title}` : "사건 기록 검색"}</small>
            <h3>{record.title}</h3>
            <p>{record.playerText}</p>
            <footer>{record.clueId}</footer>
          </article>
        );
      })}
    </div>
  );
}

function TimelineTab() {
  return (
    <div className="timeline-list">
      {TIMELINE.map((event, index) => (
        <article key={`${event.time}-${event.title}`}>
          <div className="timeline-node">
            <i className={index === TIMELINE.length - 1 ? "is-current" : ""} />
          </div>
          <time>{event.time}</time>
          <div>
            <h3>{event.title}</h3>
            <p>{event.detail}</p>
          </div>
        </article>
      ))}
    </div>
  );
}

function SuspectsTab() {
  const interviewedIds = useGameStore((state) => state.interviewedIds);
  const suspects = useSuspects();
  return (
    <div className="suspect-list">
      {suspects.map((suspect, index) => (
        <article key={suspect.id}>
          <div className="suspect-number" style={{ "--suspect": suspect.color } as React.CSSProperties}>
            {String(index + 1).padStart(2, "0")}
          </div>
          <div>
            <span>{suspect.role}</span>
            <h3>{suspect.name}</h3>
          </div>
          <dl>
            <div>
              <dt>진술</dt>
              <dd>{interviewedIds.includes(`NPC_${suspect.id}`) ? "1차 확보" : "미확보"}</dd>
            </div>
            <div><dt>알리바이</dt><dd>검증 전</dd></div>
          </dl>
          <button type="button" disabled>
            {interviewedIds.includes(`NPC_${suspect.id}`) ? "진술 기록됨" : "심문 필요"}
          </button>
        </article>
      ))}
    </div>
  );
}

const ASSISTANT_QUERIES = [
  "02시 전후의 환경 제어 기록을 시간순으로 비교해줘.",
  "출입 기록과 각 구역 원본 사이의 모순을 찾아줘.",
  "현재 증거만으로 확인할 수 없는 부분을 알려줘.",
];

function AssistantTab() {
  const sessionId = useGameStore((state) => state.sessionId);
  const evidence = useGameStore((state) => state.evidence);
  const [query, setQuery] = useState(ASSISTANT_QUERIES[0]);
  const assistant = useAskAssistant(sessionId);
  const caseState = useCaseState(sessionId);
  const submit = (nextQuery = query) => {
    const normalized = nextQuery.trim();
    if (!normalized) return;
    setQuery(normalized);
    assistant.mutate(
      { query: normalized, discoveredEvidenceIds: evidence.map((record) => record.clueId) },
      // 검색으로 새 단서가 열릴 수 있다. 서버 공개 상태를 다시 읽어 수첩을 맞춘다.
      { onSuccess: () => void caseState.refetch() },
    );
  };

  return (
    <div className="assistant-console">
      <header>
        <div>
          <span>LOCAL EVIDENCE INDEX // READ ONLY</span>
          <h3>아르카디아 수사 보조</h3>
        </div>
        <strong>{evidence.length} RECORDS AVAILABLE</strong>
      </header>

      <div className="assistant-presets">
        {ASSISTANT_QUERIES.map((preset) => (
          <button type="button" key={preset} onClick={() => submit(preset)}>
            {preset}
          </button>
        ))}
      </div>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
      >
        <label htmlFor="assistant-query">발견한 기록에 질문</label>
        <div>
          <input
            id="assistant-query"
            value={query}
            maxLength={160}
            onChange={(event) => setQuery(event.target.value)}
          />
          <button type="submit" disabled={!query.trim() || assistant.isPending}>
            {assistant.isPending ? "검색 중" : "기록 비교"}
          </button>
        </div>
      </form>

      {assistant.isError && (
        <article className="assistant-answer is-fallback" role="status">
          <span>STATIC FALLBACK // AI OFFLINE</span>
          <h4>자동 비교를 완료하지 못했습니다.</h4>
          <p>
            증거 탭에서 시간·구역·접근 주체를 직접 대조할 수 있습니다. 핵심 단서
            획득과 재판 진행은 수사 보조 상태와 무관합니다.
          </p>
          <button type="button" onClick={() => submit()}>다시 검색</button>
        </article>
      )}

      {assistant.data && (
        <article className="assistant-answer" aria-live="polite">
          <span>{assistant.data.fallback ? "STATIC FALLBACK" : "SUPPORTED FINDING"}</span>
          <h4>{assistant.data.summary}</h4>
          <p>{assistant.data.observation}</p>
          <div>
            {assistant.data.citations.map((id) => (
              <button
                type="button"
                key={id}
                onClick={() => useGameStore.getState().setNotebookTab("evidence")}
              >
                {evidence.find((record) => record.clueId === id)?.title ?? id}
              </button>
            ))}
          </div>
          {assistant.data.suggestedQuery && (
            <button type="button" onClick={() => submit(assistant.data!.suggestedQuery!)}>
              후속 질문 · {assistant.data.suggestedQuery}
            </button>
          )}
        </article>
      )}
    </div>
  );
}

function TheoryTab() {
  const sessionId = useGameStore((state) => state.sessionId);
  const sessionVersion = useGameStore((state) => state.sessionVersion);
  const phase = useGameStore((state) => state.phase);
  const theory = useGameStore((state) => state.theory);
  const evidence = useGameStore((state) => state.evidence);
  const suspects = useSuspects();
  const updateSessionVersion = useGameStore((state) => state.updateSessionVersion);
  const setTheorySuspect = useGameStore((state) => state.setTheorySuspect);
  const setTheoryEvidence = useGameStore((state) => state.setTheoryEvidence);
  const setTheoryExclusion = useGameStore((state) => state.setTheoryExclusion);
  const startTrial = useGameStore((state) => state.startTrial);
  const saveTheory = useSaveTheory(sessionId);

  const otherSuspects = suspects.filter((suspect) => suspect.id !== theory.suspectId);
  const validation = validateTheory(
    theory,
    evidence.map((record) => record.clueId),
    suspects.map((suspect) => suspect.id),
  );
  const ready = phase === "DAY2" && validation.valid;
  const submitTheory = () => {
    if (!ready) return;
    saveTheory.mutate(
      { theory, version: sessionVersion },
      {
        onSuccess: ({ version }) => {
          updateSessionVersion(version);
          startTrial();
        },
      },
    );
  };

  if (phase !== "DAY2") {
    return (
      <div className="theory-locked">
        <span>RECONSTRUCTION LOCKED</span>
        <i />
        <h3>D2 심층 조사 이후 개방됩니다.</h3>
        <p>첫날 현장 기록과 용의자 진술을 먼저 확보하십시오.</p>
      </div>
    );
  }

  const evidenceOptions = (value: string | null, onChange: (id: string) => void) => (
    <select value={value ?? ""} onChange={(event) => onChange(event.target.value)}>
      <option value="" disabled>기록 선택</option>
      {evidence.map((record) => (
        <option key={record.clueId} value={record.clueId}>
          [{EVIDENCE_TYPE_LABELS[record.clueType]}] {record.title}
        </option>
      ))}
    </select>
  );

  return (
    <div className="theory-builder">
      <section className="theory-accused">
        <header>
          <span>01 // ACCUSED</span>
          <h3>범인 지목</h3>
          <p>범행 조건을 모두 만족하는 한 명을 선택합니다.</p>
        </header>
        <div>
          {suspects.map((suspect) => (
            <button
              key={suspect.id}
              type="button"
              className={theory.suspectId === suspect.id ? "is-selected" : ""}
              style={{ "--suspect": suspect.color } as React.CSSProperties}
              onClick={() => setTheorySuspect(suspect.id)}
            >
              <i>{suspect.name.slice(0, 1)}</i>
              <span>{suspect.role}</span>
              <strong>{suspect.name}</strong>
            </button>
          ))}
        </div>
      </section>

      <section className="theory-core">
        <header>
          <span>02 // CORE PROOF</span>
          <h3>핵심 입증</h3>
          <p>준비·실행·기회·동기를 서로 다른 기록으로 구성합니다.</p>
        </header>
        <div>
          <label>
            <span>SETUP</span>
            <strong>범행 준비</strong>
            {evidenceOptions(theory.setup, (id) => setTheoryEvidence("setup", id))}
          </label>
          <label>
            <span>TRIGGER</span>
            <strong>실행 트리거</strong>
            {evidenceOptions(theory.trigger, (id) => setTheoryEvidence("trigger", id))}
          </label>
          <label>
            <span>OPPORTUNITY</span>
            <strong>기회와 권한</strong>
            {evidenceOptions(theory.opportunity, (id) => setTheoryEvidence("opportunity", id))}
          </label>
          <label>
            <span>MOTIVE</span>
            <strong>범행 동기</strong>
            {evidenceOptions(theory.motive, (id) => setTheoryEvidence("motive", id))}
          </label>
        </div>
      </section>

      <section className="theory-exclusions">
        <header>
          <span>03 // EXCLUSION</span>
          <h3>다른 용의자 배제</h3>
          <p>권한 부재가 아닌 알리바이·전문성·물리 흔적으로 배제합니다.</p>
        </header>
        {!theory.suspectId ? (
          <div className="theory-placeholder">범인을 먼저 지목하십시오.</div>
        ) : (
          <div>
            {otherSuspects.map((suspect) => (
              <label key={suspect.id}>
                <i style={{ "--suspect": suspect.color } as React.CSSProperties}>
                  {suspect.name.slice(0, 1)}
                </i>
                <span>{suspect.name}</span>
                {evidenceOptions(
                  theory.exclusions[suspect.id] ?? null,
                  (id) => setTheoryExclusion(suspect.id, id),
                )}
              </label>
            ))}
          </div>
        )}
      </section>

      <footer className="theory-submit">
        <div>
          <span>TRIAL READINESS</span>
          <strong>{ready ? "재판 준비 완료" : validation.message}</strong>
        </div>
        {saveTheory.isError && (
          <p className="inline-api-error" role="alert">{saveTheory.error.message}</p>
        )}
        <button
          type="button"
          disabled={!ready || saveTheory.isPending}
          onClick={submitTheory}
        >
          {saveTheory.isPending ? "사건 재구성 봉인 중" : "D3 생존자 재판 시작"} <i>→</i>
        </button>
      </footer>
    </div>
  );
}

/** 심문 한 번의 문답. `answer`가 null이면 응답을 기다리는 중이다. */
type InterrogationTurn = {
  id: number;
  kind: "choice" | "free" | "evidence";
  question: string;
  answer: string | null;
};

function InterrogationPanel() {
  const subtitles = useSettingsStore((state) => state.subtitles);
  const sessionId = useGameStore((state) => state.sessionId);
  const selectedId = useGameStore((state) => state.selectedId);
  const evidence = useGameStore((state) => state.evidence);
  const closeOverlay = useGameStore((state) => state.closeOverlay);
  const markInterviewed = useGameStore((state) => state.markInterviewed);
  const updateSessionVersion = useGameStore((state) => state.updateSessionVersion);
  const [turns, setTurns] = useState<InterrogationTurn[]>([]);
  const [activeChoice, setActiveChoice] = useState<string | null>(null);
  const [showEvidence, setShowEvidence] = useState(false);
  const [freeQuestion, setFreeQuestion] = useState("");
  const turnIdRef = useRef(0);
  const logRef = useRef<HTMLDivElement>(null);

  const target = selectedId ? INVESTIGATION_OBJECTS[selectedId] : null;
  const dialogue = selectedId ? NPC_DIALOGUE[selectedId] : null;
  const interrogation = useInterrogationSession(sessionId, selectedId);
  const sendMessage = useSendInterrogationMessage(
    interrogation.data?.interrogationId ?? null,
  );

  useEffect(() => {
    setTurns([]);
    setActiveChoice(null);
    setShowEvidence(false);
    setFreeQuestion("");
    turnIdRef.current = 0;
  }, [selectedId]);

  // 새 답변이 도착하면 대화 기록의 끝으로 따라간다.
  useEffect(() => {
    const log = logRef.current;
    if (log) log.scrollTop = log.scrollHeight;
  }, [turns]);

  if (!target || !dialogue || !selectedId) return null;

  const npcId = selectedId;

  /** 질문을 기록에 먼저 남기고, 답변이 오면 그 자리를 채운다. */
  const openTurn = (kind: InterrogationTurn["kind"], question: string) => {
    turnIdRef.current += 1;
    const turnId = turnIdRef.current;
    setTurns((current) => [...current, { id: turnId, kind, question, answer: null }]);
    return turnId;
  };

  const closeTurn = (turnId: number, answer: string) => {
    setTurns((current) =>
      current.map((turn) => (turn.id === turnId ? { ...turn, answer } : turn)),
    );
    setActiveChoice(null);
    markInterviewed(npcId);
  };

  const send = (
    payload: Parameters<typeof sendMessage.mutate>[0],
    turnId: number,
    fallbackResponse: string,
  ) => {
    if (interrogation.isError) {
      closeTurn(turnId, fallbackResponse);
      return;
    }
    sendMessage.mutate(payload, {
      onSuccess: (message) => {
        closeTurn(turnId, message.response);
        updateSessionVersion(message.version);
      },
      onError: () => closeTurn(turnId, fallbackResponse),
    });
  };

  const ask = (choiceId: string) => {
    const selectedChoice = dialogue.choices.find((choice) => choice.id === choiceId);
    setActiveChoice(choiceId);
    setShowEvidence(false);
    const turnId = openTurn("choice", selectedChoice?.label ?? "질문");
    send(
      { npcId, choiceId },
      turnId,
      selectedChoice?.response ?? "지금은 답변할 수 없습니다.",
    );
  };

  const presentEvidence = (evidenceId: string) => {
    setActiveChoice(`evidence-${evidenceId}`);
    setShowEvidence(false);
    const title =
      evidence.find((record) => record.clueId === evidenceId)?.title ?? evidenceId;
    const turnId = openTurn("evidence", `증거 제시 · ${title}`);
    send(
      { npcId, evidenceId },
      turnId,
      "해당 기록은 확인하겠습니다. 다만 그 자료만으로 제 행동과 사망을 직접 연결할 수는 없습니다.",
    );
  };

  const askFreeQuestion = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const query = freeQuestion.trim();
    if (!query) return;

    setActiveChoice("free-question");
    setShowEvidence(false);
    setFreeQuestion("");
    const turnId = openTurn("free", query);
    send(
      { npcId, query },
      turnId,
      `“${query}”에 대해선 당시 보안 기록과 제 동선으로 판단해 주십시오. 추측으로 답하지 않겠습니다.`,
    );
  };

  const askedLabels = new Set(
    turns.filter((turn) => turn.kind === "choice").map((turn) => turn.question),
  );

  return (
    <section className="interrogation-shell" aria-label={`${target.title} 심문`}>
      <div className="interrogation-backdrop" />
      <header className="interrogation-topbar">
        <div>
          <span>INTERROGATION CHANNEL // D1</span>
          <strong>{dialogue.callSign}</strong>
        </div>
        <div className="interrogation-state">
          <i />
          <span>로컬 보안 기록 중</span>
        </div>
        <button type="button" onClick={closeOverlay}>심문 종료 <kbd>ESC</kbd></button>
      </header>

      <div className="interrogation-layout">
        <aside className="suspect-portrait">
          <div className="portrait-grid" />
          <div className="portrait-silhouette">
            <i />
            <i />
          </div>
          <div className="portrait-id">{target.title.slice(0, 1)}</div>
          <footer>
            <span>{target.eyebrow}</span>
            <strong>{target.title}</strong>
            <small>{dialogue.posture}</small>
          </footer>
        </aside>

        <main className="interrogation-main">
          <div className="dialogue-transcript">
            <div className="speaker-line">
              <span>{dialogue.callSign}</span>
              <time>LIVE · {String(turns.length + 1).padStart(2, "0")}</time>
            </div>
            {/* 첫 진술은 위에 남겨 두고, 주고받은 문답은 아래에 계속 쌓는다. */}
            <blockquote
              className={`${subtitles ? "is-subtitled" : ""} ${
                turns.length > 0 ? "is-compact" : ""
              }`.trim()}
            >
              {interrogation.isPending
                ? "보안 음성 채널을 동기화하고 있습니다…"
                : interrogation.data?.opening ?? dialogue.opening}
            </blockquote>
            {turns.length > 0 && (
              <div className="dialogue-log" ref={logRef} aria-live="polite">
                {turns.map((turn) => (
                  <article
                    key={turn.id}
                    className={`transcript-turn${turn.answer === null ? " is-pending" : ""}`}
                  >
                    <div className="interrogator-query">
                      <span>
                        {turn.kind === "evidence" ? "INVESTIGATOR // EVIDENCE" : "INVESTIGATOR // QUERY"}
                      </span>
                      <p>{turn.question}</p>
                    </div>
                    <blockquote className={subtitles ? "is-subtitled" : ""}>
                      {turn.answer ?? "응답을 수신하고 있습니다…"}
                    </blockquote>
                  </article>
                ))}
              </div>
            )}
            {(interrogation.isError || sendMessage.isError) && (
              <div className="inline-api-error" role="alert">
                <span>
                  {interrogation.error?.message ?? sendMessage.error?.message}
                </span>
                {interrogation.isError && (
                  <button type="button" onClick={() => interrogation.refetch()}>
                    채널 재연결
                  </button>
                )}
              </div>
            )}
          </div>

          {!showEvidence && (
            <div className="question-list">
              <span className="question-label">
                {turns.length > 0 ? "이어서 질문" : "질문 선택"}
              </span>
              {dialogue.choices.map((choice, index) => {
                const asked = askedLabels.has(choice.label);
                return (
                  <button
                    key={choice.id}
                    type="button"
                    className={[
                      activeChoice === choice.id ? "is-active" : "",
                      asked ? "is-asked" : "",
                    ]
                      .filter(Boolean)
                      .join(" ")}
                    disabled={interrogation.isPending || sendMessage.isPending}
                    onClick={() => ask(choice.id)}
                  >
                    <em>{String(index + 1).padStart(2, "0")}</em>
                    <span>{choice.label}</span>
                    <i>{asked ? "✓" : "→"}</i>
                  </button>
                );
              })}
              <form className="free-question-form" onSubmit={askFreeQuestion}>
                <label htmlFor="free-interrogation-question">
                  <span>FREE QUESTION</span>
                  직접 질문
                </label>
                <div>
                  <input
                    id="free-interrogation-question"
                    type="text"
                    value={freeQuestion}
                    maxLength={240}
                    placeholder="용의자에게 직접 질문을 입력하십시오."
                    disabled={interrogation.isPending || sendMessage.isPending}
                    onChange={(event) => setFreeQuestion(event.target.value)}
                  />
                  <button
                    type="submit"
                    disabled={
                      !freeQuestion.trim() ||
                      interrogation.isPending ||
                      sendMessage.isPending
                    }
                  >
                    {sendMessage.isPending && activeChoice === "free-question"
                      ? "전송 중"
                      : "질문 전송"}
                    <i>→</i>
                  </button>
                </div>
                <small>{freeQuestion.length} / 240</small>
              </form>
              <button
                className="present-evidence"
                type="button"
                onClick={() => setShowEvidence(true)}
                disabled={
                  evidence.length === 0 ||
                  interrogation.isPending ||
                  sendMessage.isPending
                }
              >
                <em>EV</em>
                <span>{evidence.length > 0 ? "발견한 증거 제시" : "제시할 증거 없음"}</span>
                <i>+</i>
              </button>
            </div>
          )}

          {showEvidence && (
            <div className="interrogation-evidence">
              <header>
                <div>
                  <span>EVIDENCE PRESENTATION</span>
                  <h3>제시할 증거 선택</h3>
                </div>
                <button type="button" onClick={() => setShowEvidence(false)}>돌아가기</button>
              </header>
              <div>
                {evidence.map((record) => (
                  <button
                    key={record.clueId}
                    type="button"
                    disabled={sendMessage.isPending}
                    onClick={() => presentEvidence(record.clueId)}
                  >
                    <small>
                      {record.sourceObjectId
                        ? INVESTIGATION_OBJECTS[record.sourceObjectId]?.zone
                        : "검색"}
                    </small>
                    <strong>{record.title}</strong>
                    <span>{EVIDENCE_TYPE_LABELS[record.clueType]}</span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </main>
      </div>
    </section>
  );
}

function Notebook() {
  const sessionId = useGameStore((state) => state.sessionId);
  const closeOverlay = useGameStore((state) => state.closeOverlay);
  const tab = useGameStore((state) => state.notebookTab);
  const setTab = useGameStore((state) => state.setNotebookTab);
  const discoveredIds = useGameStore((state) => state.discoveredIds);
  const evidence = useGameStore((state) => state.evidence);
  const progress = getRequiredProgress(discoveredIds);
  // 수첩을 열 때마다 서버 공개 상태로 맞춘다. 배경에서 열린 단서가 여기서 반영된다.
  useCaseState(sessionId);

  return (
    <section className="notebook-shell" aria-label="사건 수첩">
      <div className="notebook-topbar">
        <div className="notebook-brand">
          <span>ARK / SECURITY ARCHIVE</span>
          <strong>사건 기록 장치</strong>
        </div>
        <div className="notebook-meta">
          <span>CASE</span>
          <strong>ARK-D1-0724</strong>
          <span>SYNC</span>
          <strong className="danger-text">OFFLINE</strong>
        </div>
        <button type="button" onClick={closeOverlay}>수첩 닫기 <kbd>TAB</kbd></button>
      </div>

      <div className="notebook-body">
        <nav>
          {NOTEBOOK_TABS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={tab === item.id ? "is-active" : ""}
              onClick={() => setTab(item.id)}
            >
              <span>{item.index}</span>
              {item.label}
            </button>
          ))}
        </nav>

        <main>
          <header className="notebook-title">
            <div>
              <p>INVESTIGATION DATABASE</p>
              <h2>{NOTEBOOK_TABS.find((item) => item.id === tab)?.label}</h2>
            </div>
            <div className="notebook-progress">
              <span>D1 필수 기록</span>
              <strong>{progress.found} / {progress.total}</strong>
              <i><b style={{ width: `${(progress.found / progress.total) * 100}%` }} /></i>
            </div>
          </header>

          <div className="notebook-content">
            {tab === "evidence" && <EvidenceTab evidence={evidence} />}
            {tab === "timeline" && <TimelineTab />}
            {tab === "suspects" && <SuspectsTab />}
            {tab === "assistant" && <AssistantTab />}
            {tab === "theory" && <TheoryTab />}
          </div>
        </main>
      </div>
    </section>
  );
}

function ScanEffect() {
  const scanUntil = useGameStore((state) => state.scanUntil);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const remaining = scanUntil - performance.now();
    if (remaining <= 0) return;
    setVisible(true);
    const timer = window.setTimeout(() => setVisible(false), remaining);
    return () => window.clearTimeout(timer);
  }, [scanUntil]);

  return visible ? <div className="scan-sweep" /> : null;
}

function DayReview() {
  const sessionId = useGameStore((state) => state.sessionId);
  const discoveredIds = useGameStore((state) => state.discoveredIds);
  const interviewedIds = useGameStore((state) => state.interviewedIds);
  const updateSessionVersion = useGameStore((state) => state.updateSessionVersion);
  const beginDayTwo = useGameStore((state) => state.beginDayTwo);
  const completeDay = useCompleteDay(sessionId);
  const progress = getRequiredProgress(discoveredIds);
  const continueToDayTwo = () => {
    completeDay.mutate(1, {
      onSuccess: ({ version }) => {
        updateSessionVersion(version);
        beginDayTwo();
      },
    });
  };

  return (
    <section className="day-review-shell" aria-label="D1 조사 정리">
      <div className="day-review-atmosphere" />
      <header>
        <span>INVESTIGATION CYCLE COMPLETE</span>
        <strong>DAY 01</strong>
        <small>아르카디아 표준시 · 22:40</small>
      </header>

      <main>
        <div className="day-review-title">
          <p>현장 보존 및 1차 진술</p>
          <h2>첫날의 기록이<br />동기보다 기회를 가리킨다.</h2>
          <span>
            모든 용의자는 숨기는 것이 있다. 하지만 거짓말의 존재만으로 살인을 증명할 수는 없다.
          </span>
        </div>

        <div className="day-review-results">
          <article>
            <span>01</span>
            <div>
              <small>SCENE RECORDS</small>
              <strong>{progress.found} / {progress.total}</strong>
              <p>사령관실 필수 기록 확보</p>
            </div>
          </article>
          <article>
            <span>02</span>
            <div>
              <small>TESTIMONIES</small>
              <strong>{interviewedIds.length} / 5</strong>
              <p>용의자 1차 진술 확보</p>
            </div>
          </article>
          <article>
            <span>03</span>
            <div>
              <small>LOG INTEGRITY</small>
              <strong>UNVERIFIED</strong>
              <p>중앙 기록과 로컬 원본 비교 필요</p>
            </div>
          </article>
        </div>

        <div className="day-review-next">
          <div>
            <span>NEXT // DAY 02</span>
            <h3>심층 조사 개방</h3>
            <p>의료 원시 데이터, 정비 기록, 통신 원본과 화물 로그를 교차 검증합니다.</p>
          </div>
          <button
            type="button"
            disabled={completeDay.isPending}
            onClick={continueToDayTwo}
          >
            <span>{completeDay.isPending ? "D1 기록 봉인 중" : "D2 조사 시작"}</span>
            <i>→</i>
          </button>
        </div>
        {completeDay.isError && (
          <p className="inline-api-error" role="alert">{completeDay.error.message}</p>
        )}
      </main>

      <footer>
        <span>구조선 도착 예상</span>
        <strong>47 : 20 : 00</strong>
      </footer>
    </section>
  );
}

function TrialScreen() {
  const sessionId = useGameStore((state) => state.sessionId);
  const theory = useGameStore((state) => state.theory);
  const evidenceRecords = useGameStore((state) => state.evidence);
  const suspects = useSuspects();
  const updateSessionVersion = useGameStore((state) => state.updateSessionVersion);
  const completeTrial = useGameStore((state) => state.completeTrial);
  const submitVerdict = useSubmitVerdict(sessionId);
  const [step, setStep] = useState(0);
  const accused = suspects.find((suspect) => suspect.id === theory.suspectId);
  const findEvidence = (clueId: string | null | undefined) =>
    clueId ? evidenceRecords.find((record) => record.clueId === clueId) : undefined;

  if (
    !accused ||
    !theory.setup ||
    !theory.trigger ||
    !theory.opportunity ||
    !theory.motive
  ) {
    return null;
  }

  const stages = [
    {
      code: "ACCUSATION",
      title: `${accused.name}을 살인 혐의로 지목합니다.`,
      line: "당신의 추론에는 빈틈이 있습니다. 접근할 수 있었다는 사실과 실제로 죽였다는 사실은 다릅니다.",
      evidenceId: null,
      action: "기소 내용 확정",
    },
    {
      code: "SETUP",
      title: "범행 준비를 입증하십시오.",
      line: "그 기록은 제 담당 업무만 보여줄 뿐입니다. 준비 행위였다고 단정할 수 있습니까?",
      evidenceId: theory.setup,
      action: "준비 증거 제시",
    },
    {
      code: "TRIGGER",
      title: "실행 시점을 입증하십시오.",
      line: "작업이 실행됐다는 것과 제가 실행했다는 것은 다릅니다. 사망 시점까지 연결할 수 있습니까?",
      evidenceId: theory.trigger,
      action: "실행 증거 제시",
    },
    {
      code: "MOTIVE",
      title: "살인의 직접 동기를 입증하십시오.",
      line: "감사 대상은 저뿐만이 아니었습니다. 비밀을 숨겼다는 이유로 모두를 살인자로 만들 수는 없습니다.",
      evidenceId: theory.motive,
      action: "동기 증거 제시",
    },
    {
      code: "EXCLUSION",
      title: "다른 용의자를 배제하십시오.",
      line: "다른 사람도 같은 시간과 장소에 접근할 수 있었습니다. 왜 반드시 저여야 합니까?",
      evidenceId: theory.opportunity,
      action: "배제 논리 제출",
    },
    {
      code: "VOTE",
      title: "생존자 투표를 요청합니다.",
      line: "이 결정은 되돌릴 수 없습니다. 당신이 제시한 기록만으로 한 사람을 진공에 내보내려는 겁니까?",
      evidenceId: null,
      action: "최종 투표 진행",
    },
  ];
  const stage = stages[step];
  const evidence = findEvidence(stage.evidenceId);
  const exclusions = suspects
    .filter((suspect) => suspect.id !== theory.suspectId)
    .map((suspect) => ({
      suspect,
      evidence: findEvidence(theory.exclusions[suspect.id]),
    }));

  const proceed = () => {
    if (step < stages.length - 1) {
      setStep((current) => current + 1);
      return;
    }
    submitVerdict.mutate(theory, {
      onSuccess: ({ version, ...result }) => {
        updateSessionVersion(version);
        completeTrial(result);
      },
    });
  };

  return (
    <section className="trial-shell" aria-label="D3 생존자 재판">
      <div className="trial-atmosphere" />
      <header className="trial-topbar">
        <div>
          <span>EMERGENCY TRIBUNAL // DAY 03</span>
          <strong>생존 승무원 과반 의결 규정</strong>
        </div>
        <div className="trial-steps">
          {stages.map((item, index) => (
            <i key={item.code} className={index <= step ? "is-active" : ""}>
              {String(index + 1).padStart(2, "0")}
            </i>
          ))}
        </div>
        <div className="trial-clock">
          <span>구조선 도착 예상</span>
          <strong>00 : 38 : 12</strong>
        </div>
      </header>

      <div className="trial-jury">
        {suspects.map((suspect) => (
          <div
            key={suspect.id}
            className={suspect.id === accused.id ? "is-accused" : ""}
            style={{ "--suspect": suspect.color } as React.CSSProperties}
          >
            <i>{suspect.name.slice(0, 1)}</i>
            <span>{suspect.role}</span>
            <strong>{suspect.name}</strong>
          </div>
        ))}
      </div>

      <main className="trial-main">
        <div className="trial-stage-copy">
          <p>{String(step + 1).padStart(2, "0")} // {stage.code}</p>
          <h2>{stage.title}</h2>
          <blockquote>
            <span>{accused.name}</span>
            {stage.line}
          </blockquote>
        </div>

        <div className="trial-proof">
          {evidence && (
            <article>
              <header>
                <span>{EVIDENCE_TYPE_LABELS[evidence.clueType]}</span>
                <small>
                  {evidence.sourceObjectId
                    ? INVESTIGATION_OBJECTS[evidence.sourceObjectId]?.title
                    : "사건 기록 검색"}
                </small>
              </header>
              <div
                className={`trial-proof-mark trial-proof-mark--${evidence.clueType.toLowerCase()}`}
              />
              <h3>{evidence.title}</h3>
              <p>{evidence.playerText}</p>
            </article>
          )}

          {stage.code === "EXCLUSION" && (
            <div className="trial-exclusions">
              {exclusions.map(({ suspect, evidence: exclusionEvidence }) => (
                <article key={suspect.id}>
                  <i style={{ "--suspect": suspect.color } as React.CSSProperties}>
                    {suspect.name.slice(0, 1)}
                  </i>
                  <div>
                    <span>{suspect.name}</span>
                    <strong>{exclusionEvidence?.title}</strong>
                  </div>
                </article>
              ))}
            </div>
          )}

          {stage.code === "ACCUSATION" && (
            <div className="accusation-seal">
              <span>ACCUSED</span>
              <strong>{accused.name}</strong>
              <small>{accused.role}</small>
            </div>
          )}

          {stage.code === "VOTE" && (
            <div className="vote-warning">
              <span>IRREVERSIBLE ACTION</span>
              <h3>추방에는 생존자 6명 중 4표가 필요합니다.</h3>
              <p>오답으로 확정된 추방 역시 취소하거나 되돌릴 수 없습니다.</p>
            </div>
          )}
        </div>
      </main>

      <footer className="trial-actions">
        <div>
          <span>PLAYER VOTE</span>
          <strong>추방 찬성 · 1표</strong>
          {submitVerdict.isError && (
            <small className="inline-api-error" role="alert">
              {submitVerdict.error.message}
            </small>
          )}
        </div>
        <button type="button" disabled={submitVerdict.isPending} onClick={proceed}>
          {submitVerdict.isPending ? "투표 집계 중" : stage.action} <i>→</i>
        </button>
      </footer>
    </section>
  );
}

const ENDING_COPY = {
  CULPRIT_EXPELLED: {
    eyebrow: "CASE CLOSED",
    title: "진범은 에어록 너머로 사라졌다.",
    text: "제시된 수단·동기·흔적이 하나의 실행자를 가리켰다. 구조선은 진실이 확정된 정거장에 도착한다.",
    accent: "#8ce0c8",
  },
  CULPRIT_SURVIVED: {
    eyebrow: "INSUFFICIENT PROOF",
    title: "진범을 알았지만 입증하지 못했다.",
    text: "과반의 동의를 얻지 못했다. 피고는 생존한 채 구조선을 기다리고, 사건은 미완의 기록으로 남는다.",
    accent: "#e2a164",
  },
  INNOCENT_EXPELLED: {
    eyebrow: "FALSE CONVICTION",
    title: "무고한 사람이 진공으로 방출됐다.",
    text: "표는 충분했지만 결론은 틀렸다. 진범은 남은 생존자 사이에서 조용히 구조선을 기다린다.",
    accent: "#de6952",
  },
  TRIAL_DEADLOCK: {
    eyebrow: "TRIAL DEADLOCK",
    title: "재판은 결론 없이 끝났다.",
    text: "증거는 누구도 설득하지 못했다. 추방은 집행되지 않았고 살인자는 정거장에 남아 있다.",
    accent: "#9aa29f",
  },
};

function ResultScreen() {
  const result = useGameStore((state) => state.trialResult);
  const toggleNotebook = useGameStore((state) => state.toggleNotebook);
  const resetSession = useGameStore((state) => state.resetSession);
  if (!result) return null;

  const accused = SUSPECTS.find((suspect) => suspect.id === result.accusedId);
  const copy = ENDING_COPY[result.ending];

  return (
    <section
      className="result-shell"
      aria-label="재판 결과"
      style={{ "--ending-accent": copy.accent } as React.CSSProperties}
    >
      <div className="result-airlock">
        <div className="airlock-ring"><i /><i /><i /></div>
        <div className="airlock-person" />
        <div className="airlock-haze" />
      </div>
      <div className="result-content">
        <span>{copy.eyebrow}</span>
        <h1>{copy.title}</h1>
        <p>{copy.text}</p>

        <div className="result-verdict">
          <div>
            <span>지목 인물</span>
            <strong>{accused?.name}</strong>
          </div>
          <div>
            <span>추방 찬성</span>
            <strong>{result.votesFor} / 6</strong>
          </div>
          <div>
            <span>판정</span>
            <strong>{result.correctAccusation ? "정답" : "오답"}</strong>
          </div>
        </div>

        <div className="vote-dots">
          {Array.from({ length: 6 }, (_, index) => (
            <i key={index} className={index < result.votesFor ? "is-for" : ""} />
          ))}
        </div>

        <div className="result-actions">
          <button type="button" onClick={toggleNotebook}>사건 기록 검토</button>
          <button type="button" onClick={resetSession}>새 사건 시작</button>
        </div>
      </div>
      <footer>
        <span>ARCADIA STATION // INCIDENT 72</span>
        <strong>구조선 도킹까지 00 : 12 : 40</strong>
      </footer>
    </section>
  );
}

export function GameUI() {
  const layer = useGameStore((state) => state.layer);
  const selectedId = useGameStore((state) => state.selectedId);
  const title = useMemo(
    () => (selectedId ? INVESTIGATION_OBJECTS[selectedId]?.title : null),
    [selectedId],
  );

  useEffect(() => {
    document.title = title ? `${title} // ARCADIA` : "ARCADIA // INCIDENT 72";
  }, [title]);

  if (layer === "opening") return <OpeningOverlay />;

  return (
    <>
      <MissionHud />
      <ScanEffect />
      {layer === "inspection" && <InspectionPanel />}
      {layer === "interrogation" && <InterrogationPanel />}
      {layer === "notebook" && <Notebook />}
      {layer === "dayReview" && <DayReview />}
      {layer === "trial" && <TrialScreen />}
      {layer === "result" && <ResultScreen />}
    </>
  );
}
