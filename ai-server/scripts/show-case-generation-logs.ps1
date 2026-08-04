[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$aiServerDirectory = (Resolve-Path (Join-Path $scriptDirectory '..')).Path
$mavenWrapper = Join-Path $aiServerDirectory 'mvnw.cmd'
$runId = [Guid]::NewGuid().ToString('N')
$stdoutPath = Join-Path ([IO.Path]::GetTempPath()) "arcadia-ai-test-$runId.out.log"
$stderrPath = Join-Path ([IO.Path]::GetTempPath()) "arcadia-ai-test-$runId.err.log"
$previousJavaToolOptions = [Environment]::GetEnvironmentVariable(
    'JAVA_TOOL_OPTIONS',
    'Process'
)
$process = $null

function Write-AuditLine {
    param([string]$Message)

    $color = switch -Regex ($Message) {
        '^\[AI-MODE\]' { 'Cyan'; break }
        '^\[AI-API\]\[FAILURE\]' { 'Red'; break }
        '^\[AI-API\]' { 'Magenta'; break }
        '^\[AI-CASE\]\[RESULT\]' { 'Green'; break }
        '^\[AI-CASE\]\[CLUE\]' { 'Gray'; break }
        '^\[GAME-SESSION\]\[FAILED\]' { 'Red'; break }
        default { 'White' }
    }
    Write-Host $Message -ForegroundColor $color
}

try {
    if (-not (Test-Path $mavenWrapper)) {
        throw "Maven Wrapper를 찾지 못했습니다: $mavenWrapper"
    }
    if (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) {
        throw 'JDK 21이 PATH에 없습니다. JDK 21을 설치하고 새 PowerShell을 연 뒤 다시 실행하세요.'
    }

    $encodingOptions = '-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8'
    $javaToolOptions = if ([string]::IsNullOrWhiteSpace($previousJavaToolOptions)) {
        $encodingOptions
    } else {
        "$previousJavaToolOptions $encodingOptions"
    }
    [Environment]::SetEnvironmentVariable(
        'JAVA_TOOL_OPTIONS',
        $javaToolOptions,
        'Process'
    )

    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Cyan
    Write-Host ' Arcadia AI 사건 생성 로그 테스트 (외부 API 비용 없음)' -ForegroundColor Cyan
    Write-Host '============================================================' -ForegroundColor Cyan
    Write-Host 'API 표시는 provider test double이며 실제 Gemini 호출은 아닙니다.'
    Write-Host '첫 API 단서의 특수 문자열은 로그 인젝션 방어용 보안 테스트입니다.'
    Write-Host ''

    $arguments = @(
        '-q',
        '-Dtest=FallbackHttpCaseGenerationLoggingTest,AiHttpCaseGenerationLoggingTest',
        'test'
    )
    $process = Start-Process `
        -FilePath $mavenWrapper `
        -ArgumentList $arguments `
        -WorkingDirectory $aiServerDirectory `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -NoNewWindow `
        -Wait `
        -PassThru

    $lines = @()
    if (Test-Path $stdoutPath) {
        $lines += [IO.File]::ReadAllLines($stdoutPath, [Text.Encoding]::UTF8)
    }
    if (Test-Path $stderrPath) {
        $lines += [IO.File]::ReadAllLines($stderrPath, [Text.Encoding]::UTF8)
    }

    if ($process.ExitCode -ne 0) {
        Write-Host "테스트 실패 (exit=$($process.ExitCode))" -ForegroundColor Red
        Write-Host '----- 원본 로그 마지막 120줄 -----' -ForegroundColor Yellow
        $lines | Select-Object -Last 120 | ForEach-Object { Write-Host $_ }
        Write-Host "stdout: $stdoutPath"
        Write-Host "stderr: $stderrPath"
        exit $process.ExitCode
    }

    $currentMode = $null
    foreach ($line in $lines) {
        if ($line -notmatch 'ARC_AI_CASE_AUDIT\s+:\s+(?<message>\[(?:AI-MODE|AI-API|AI-CASE|GAME-SESSION)\].*)$') {
            continue
        }
        $message = $Matches['message'] -replace ' event=[^ ]+', ''
        if ($message -match '^\[AI-MODE\].*configuredMode=(?<mode>API|FALLBACK)') {
            $mode = $Matches['mode']
            if ($mode -ne $currentMode) {
                $currentMode = $mode
                Write-Host ''
                Write-Host "==================== $mode ====================" -ForegroundColor Yellow
            }
        }
        Write-AuditLine -Message $message
    }

    Write-Host ''
    Write-Host 'PASS: FALLBACK/API 경로와 단서 14개 로그를 모두 검증했습니다.' -ForegroundColor Green
} finally {
    [Environment]::SetEnvironmentVariable(
        'JAVA_TOOL_OPTIONS',
        $previousJavaToolOptions,
        'Process'
    )
    if ($null -eq $process -or $process.ExitCode -eq 0) {
        Remove-Item -LiteralPath $stdoutPath -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
    }
}
