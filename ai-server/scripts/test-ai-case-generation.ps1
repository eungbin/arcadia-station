[CmdletBinding()]
param(
    [ValidateSet('fallback', 'gemini', 'openai')]
    [string]$Mode = 'fallback',

    [ValidateRange(1024, 65535)]
    [int]$Port = 8081,

    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 180,

    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$aiServerDirectory = (Resolve-Path (Join-Path $scriptDirectory '..')).Path
$mavenWrapper = Join-Path $aiServerDirectory 'mvnw.cmd'
$targetDirectory = Join-Path $aiServerDirectory 'target'
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDirectory = Join-Path $targetDirectory "live-smoke-$runId"
$stdoutPath = Join-Path $logDirectory 'ai-server.log'
$stderrPath = Join-Path $logDirectory 'ai-server-error.log'
$buildStdoutPath = Join-Path $logDirectory 'build.log'
$buildStderrPath = Join-Path $logDirectory 'build-error.log'
$serverProcess = $null
$result = $null
$failure = $null

$managedEnvironment = @(
    'PORT',
    'AI_INTERNAL_API_KEY',
    'AI_ENABLED',
    'AI_OFFLINE_MODE',
    'AI_PROVIDER',
    'GEMINI_API_KEY',
    'GEMINI_BASE_URL',
    'OPENAI_API_KEY',
    'OPENAI_BASE_URL',
    'AI_CASE_GENERATION_MAX_RETRIES',
    'AI_CASE_GENERATION_TIMEOUT',
    'AI_QUOTA_COOLDOWN',
    'LOGGING_LEVEL_ROOT',
    'LOGGING_LEVEL_ARC_AI_CASE_AUDIT',
    'LOGGING_PATTERN_CONSOLE'
)
$originalEnvironment = @{}
foreach ($name in $managedEnvironment) {
    $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable(
        $name,
        'Process'
    )
}

function Write-Section {
    param([string]$Title)

    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Cyan
    Write-Host " $Title" -ForegroundColor Cyan
    Write-Host '============================================================' -ForegroundColor Cyan
}

function Require-Java {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -eq $javaCommand) {
        throw 'JDK 21이 PATH에 없습니다. JDK 21 설치 후 새 PowerShell에서 다시 실행하세요.'
    }
    return $javaCommand.Source
}

function Read-ProviderKey {
    param([string]$EnvironmentName)

    $configured = [Environment]::GetEnvironmentVariable($EnvironmentName, 'Process')
    if (-not [string]::IsNullOrWhiteSpace($configured)) {
        return $configured
    }
    if ([Console]::IsInputRedirected) {
        throw "$EnvironmentName 환경변수가 없습니다. 실제 API 키를 설정한 뒤 다시 실행하세요."
    }
    $secureValue = Read-Host "$EnvironmentName 입력 (화면과 로그에 표시되지 않음)" -AsSecureString
    $credential = New-Object System.Management.Automation.PSCredential(
        'arcadia-local',
        $secureValue
    )
    $plainValue = $credential.GetNetworkCredential().Password
    if ([string]::IsNullOrWhiteSpace($plainValue)) {
        throw "$EnvironmentName 값이 비어 있습니다."
    }
    return $plainValue
}

function Wait-ForHealth {
    param(
        [string]$Uri,
        [DateTime]$Deadline
    )

    while ((Get-Date) -lt $Deadline) {
        if ($null -ne $serverProcess -and $serverProcess.HasExited) {
            throw "AI 서버가 시작 중 종료되었습니다 (exit=$($serverProcess.ExitCode))."
        }
        try {
            $health = Invoke-RestMethod -Method Get -Uri $Uri -TimeoutSec 2
            if ($health.status -eq 'UP') {
                return
            }
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    throw "AI 서버 health check가 $TimeoutSeconds 초 안에 성공하지 못했습니다."
}

function Wait-ForCase {
    param(
        [string]$Uri,
        [hashtable]$Headers,
        [DateTime]$Deadline
    )

    while ((Get-Date) -lt $Deadline) {
        $status = Invoke-RestMethod `
            -Method Get `
            -Uri $Uri `
            -Headers $Headers `
            -TimeoutSec 10
        if ($status.status -eq 'READY') {
            return $status
        }
        if ($status.status -eq 'FAILED') {
            throw "사건 생성 세션이 실패했습니다: errorCode=$($status.errorCode)"
        }
        Start-Sleep -Milliseconds 500
    }
    throw "사건 생성 결과가 $TimeoutSeconds 초 안에 READY가 되지 않았습니다."
}

function Stop-AiServer {
    if ($null -eq $script:serverProcess -or $script:serverProcess.HasExited) {
        return
    }
    Stop-Process -Id $script:serverProcess.Id -Force -ErrorAction SilentlyContinue
    Wait-Process -Id $script:serverProcess.Id -Timeout 5 -ErrorAction SilentlyContinue
}

function Read-ServerLogLines {
    $lines = @()
    if (Test-Path $stdoutPath) {
        $lines += [IO.File]::ReadAllLines($stdoutPath, [Text.Encoding]::UTF8)
    }
    if (Test-Path $stderrPath) {
        $lines += [IO.File]::ReadAllLines($stderrPath, [Text.Encoding]::UTF8)
    }
    return $lines
}

function Convert-ToReadableText {
    param([object]$Value)

    $text = [string]$Value
    if ($text -notmatch '[\u0080-\u00ff]') {
        return $text
    }
    try {
        # Windows PowerShell 5.1 may decode application/json UTF-8 as Latin-1.
        $bytes = [Text.Encoding]::GetEncoding(28591).GetBytes($text)
        $repaired = [Text.Encoding]::UTF8.GetString($bytes)
        if ($repaired -match '[\uac00-\ud7a3]' -and $repaired -notmatch '\ufffd') {
            return $repaired
        }
    } catch {
        # Preserve the original value if it was already decoded correctly.
    }
    return $text
}

function Show-ReadableAudit {
    param([string[]]$Lines)

    Write-Section '실제 실행 경로'
    $markers = $Lines | Where-Object {
        $_ -match '^\[(AI-MODE|AI-API|GAME-SESSION|AI-CASE)\]'
    } | Where-Object {
        $_ -notmatch '^\[AI-CASE\]\[CLUE\]'
    }
    if (@($markers).Count -eq 0) {
        Write-Host '진단 마커를 찾지 못했습니다.' -ForegroundColor Yellow
    } else {
        foreach ($line in $markers) {
            $color = if ($line -match '\[FAILURE\]|\[FAILED\]') {
                'Red'
            } elseif ($line -match '\[SUCCESS\]|\[READY\]|\[RESULT\]') {
                'Green'
            } else {
                'White'
            }
            Write-Host ($line -replace ' event=[^ ]+', '') -ForegroundColor $color
        }
    }
}

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

try {
    Write-Section "Arcadia AI 사건 생성 스모크 테스트: $($Mode.ToUpperInvariant())"
    $javaExecutable = Require-Java
    if (-not (Test-Path $mavenWrapper)) {
        throw "Maven Wrapper를 찾지 못했습니다: $mavenWrapper"
    }

    $portOwner = Get-NetTCPConnection `
        -State Listen `
        -LocalPort $Port `
        -ErrorAction SilentlyContinue
    if ($null -ne $portOwner) {
        throw "포트 $Port 가 이미 사용 중입니다. 기존 서버를 종료하거나 -Port로 다른 값을 지정하세요."
    }

    $internalKey = 'arcadia-smoke-' + [Guid]::NewGuid().ToString('N')
    [Environment]::SetEnvironmentVariable('PORT', "$Port", 'Process')
    [Environment]::SetEnvironmentVariable('AI_INTERNAL_API_KEY', $internalKey, 'Process')
    [Environment]::SetEnvironmentVariable('AI_ENABLED', 'true', 'Process')
    [Environment]::SetEnvironmentVariable(
        'AI_OFFLINE_MODE',
        $(if ($Mode -eq 'fallback') { 'true' } else { 'false' }),
        'Process'
    )
    [Environment]::SetEnvironmentVariable(
        'AI_PROVIDER',
        $(if ($Mode -eq 'fallback') { 'gemini' } else { $Mode }),
        'Process'
    )
    if ($Mode -eq 'gemini') {
        [Environment]::SetEnvironmentVariable(
            'GEMINI_BASE_URL',
            'https://generativelanguage.googleapis.com/v1beta',
            'Process'
        )
        [Environment]::SetEnvironmentVariable(
            'GEMINI_API_KEY',
            (Read-ProviderKey -EnvironmentName 'GEMINI_API_KEY'),
            'Process'
        )
    }
    if ($Mode -eq 'openai') {
        [Environment]::SetEnvironmentVariable(
            'OPENAI_BASE_URL',
            'https://api.openai.com/v1',
            'Process'
        )
        [Environment]::SetEnvironmentVariable(
            'OPENAI_API_KEY',
            (Read-ProviderKey -EnvironmentName 'OPENAI_API_KEY'),
            'Process'
        )
    }
    [Environment]::SetEnvironmentVariable(
        'AI_CASE_GENERATION_MAX_RETRIES',
        '0',
        'Process'
    )
    [Environment]::SetEnvironmentVariable(
        'AI_CASE_GENERATION_TIMEOUT',
        '90s',
        'Process'
    )
    [Environment]::SetEnvironmentVariable('AI_QUOTA_COOLDOWN', '0s', 'Process')
    [Environment]::SetEnvironmentVariable('LOGGING_LEVEL_ROOT', 'WARN', 'Process')
    [Environment]::SetEnvironmentVariable(
        'LOGGING_LEVEL_ARC_AI_CASE_AUDIT',
        'INFO',
        'Process'
    )
    [Environment]::SetEnvironmentVariable(
        'LOGGING_PATTERN_CONSOLE',
        '%msg%n',
        'Process'
    )

    if (-not $SkipBuild) {
        Write-Host '[1/4] 애플리케이션 빌드 중...'
        $buildProcess = Start-Process `
            -FilePath $mavenWrapper `
            -ArgumentList @('-q', '-DskipTests', 'package') `
            -WorkingDirectory $aiServerDirectory `
            -RedirectStandardOutput $buildStdoutPath `
            -RedirectStandardError $buildStderrPath `
            -NoNewWindow `
            -Wait `
            -PassThru
        if ($buildProcess.ExitCode -ne 0) {
            throw "빌드가 실패했습니다 (exit=$($buildProcess.ExitCode))."
        }
    } else {
        Write-Host '[1/4] 빌드 생략 (-SkipBuild)'
    }

    $jar = Get-ChildItem -Path $targetDirectory -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $jar) {
        throw '실행할 JAR가 없습니다. -SkipBuild를 빼고 다시 실행하세요.'
    }

    Write-Host "[2/4] AI 서버 시작 중: http://127.0.0.1:$Port"
    $javaArguments = '-jar "{0}"' -f $jar.FullName
    $serverProcess = Start-Process `
        -FilePath $javaExecutable `
        -ArgumentList $javaArguments `
        -WorkingDirectory $aiServerDirectory `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -WindowStyle Hidden `
        -PassThru

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    Wait-ForHealth `
        -Uri "http://127.0.0.1:$Port/actuator/health" `
        -Deadline $deadline

    Write-Host '[3/4] 내부 사건 생성 API 호출 중...'
    $sessionId = 'live-smoke-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $headers = @{ 'X-Internal-AI-Key' = $internalKey }
    $body = @{
        sessionId = $sessionId
        seed = [Guid]::NewGuid().ToString('N')
    } | ConvertTo-Json -Compress
    Invoke-RestMethod `
        -Method Post `
        -Uri "http://127.0.0.1:$Port/internal/v1/cases" `
        -Headers $headers `
        -ContentType 'application/json' `
        -Body $body `
        -TimeoutSec 10 | Out-Null

    $result = Wait-ForCase `
        -Uri "http://127.0.0.1:$Port/internal/v1/cases/$sessionId" `
        -Headers $headers `
        -Deadline $deadline
    Write-Host '[4/4] 사건 생성 결과 수신 완료.'
} catch {
    $failure = $_
} finally {
    Stop-AiServer
    Start-Sleep -Milliseconds 200
    foreach ($name in $managedEnvironment) {
        [Environment]::SetEnvironmentVariable(
            $name,
            $originalEnvironment[$name],
            'Process'
        )
    }
}

$logLines = @(Read-ServerLogLines)
Show-ReadableAudit -Lines $logLines

if ($null -ne $result -and $null -ne $result.generation) {
    $clues = @($result.generation.caseBlueprint.clues)
    Write-Section "생성된 단서 목록: $($clues.Count)개"
    for ($index = 0; $index -lt $clues.Count; $index++) {
        $clue = $clues[$index]
        $readableTitle = Convert-ToReadableText -Value $clue.title
        Write-Host ('{0,2}. {1} | {2}' -f ($index + 1), $clue.clueId, $readableTitle) `
            -ForegroundColor White
        Write-Host ('    획득={0} | 장소={1} | 오브젝트={2}' -f `
            $clue.acquisition.type, `
            $clue.acquisition.locationId, `
            $clue.source.sourceId) -ForegroundColor DarkGray
    }
}

Write-Host ''
Write-Host "원본 AI 로그: $stdoutPath"
if ((Test-Path $stderrPath) -and (Get-Item $stderrPath).Length -gt 0) {
    Write-Host "오류 로그: $stderrPath"
}

if ($null -ne $failure) {
    if (Test-Path $buildStderrPath) {
        $buildTail = [IO.File]::ReadAllLines(
            $buildStderrPath,
            [Text.Encoding]::UTF8
        ) | Select-Object -Last 40
        if (@($buildTail).Count -gt 0) {
            Write-Host '----- 빌드 오류 마지막 40줄 -----' -ForegroundColor Yellow
            $buildTail | ForEach-Object { Write-Host $_ }
        }
    }
    Write-Host "FAIL: $($failure.Exception.Message)" -ForegroundColor Red
    exit 1
}

$generationSource = [string]$result.generation.generationSource
if ($Mode -eq 'fallback') {
    if ($generationSource -ne 'FALLBACK') {
        Write-Host "FAIL: fallback 테스트인데 generationSource=$generationSource 입니다." `
            -ForegroundColor Red
        exit 1
    }
    Write-Host 'PASS: 외부 호출 없이 FALLBACK 사건과 단서 로그를 확인했습니다.' `
        -ForegroundColor Green
    exit 0
}

$providerSucceeded = $logLines | Where-Object {
    $_ -match '^\[AI-API\]\[SUCCESS\].*purpose=CASE_GENERATION'
}
if ($generationSource -ne 'AI' -or @($providerSucceeded).Count -eq 0) {
    Write-Host 'FAIL: 외부 API 사건 생성에 성공하지 못했습니다.' -ForegroundColor Red
    Write-Host '위 [AI-API][FAILURE]의 httpStatus/failureCategory와' `
        ' [AI-CASE][RESULT]의 fallbackReason을 확인하세요.' -ForegroundColor Yellow
    exit 1
}

Write-Host "PASS: 실제 $($Mode.ToUpperInvariant()) API 호출과 AI 단서 생성을 확인했습니다." `
    -ForegroundColor Green
exit 0
