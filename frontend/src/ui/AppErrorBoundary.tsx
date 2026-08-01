import { Component, type ErrorInfo, type ReactNode } from "react";

type Props = { children: ReactNode };
type State = { error: Error | null };

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("ARCADIA_FATAL", error, info.componentStack);
  }

  private resetSession = () => {
    localStorage.removeItem("arcadia-station-session-v1");
    location.reload();
  };

  render() {
    if (!this.state.error) return this.props.children;

    return (
      <main className="fatal-shell">
        <span>SYSTEM RECOVERY // ARK-072</span>
        <h1>공간 데이터 복원에 실패했습니다.</h1>
        <p>
          화면을 다시 불러오거나, 문제가 반복되면 저장된 사건 세션만 초기화할 수
          있습니다. 그래픽·오디오 설정은 유지됩니다.
        </p>
        <code>{this.state.error.message}</code>
        <div>
          <button type="button" onClick={() => location.reload()}>
            화면 다시 불러오기
          </button>
          <button type="button" onClick={this.resetSession}>
            사건 세션 초기화
          </button>
        </div>
      </main>
    );
  }
}
