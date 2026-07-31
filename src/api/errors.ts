/**
 * API 오류 정규화. mock 어댑터와 HTTP 어댑터가 같은 오류 타입을 던져야
 * 화면의 재시도 분기가 모드와 무관하게 동작한다.
 */
export class ArcadiaApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly retryable: boolean,
  ) {
    super(message);
    this.name = "ArcadiaApiError";
  }
}
