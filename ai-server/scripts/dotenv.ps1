function Import-ArcadiaDotEnv {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [switch]$Required
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        if ($Required) {
            throw ".env 파일을 찾을 수 없습니다: $Path"
        }
        return $false
    }

    $lineNumber = 0
    foreach ($rawLine in Get-Content -LiteralPath $Path -Encoding utf8) {
        $lineNumber++
        $line = $rawLine.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith('#')) {
            continue
        }
        if ($line -notmatch '^(?<name>[A-Za-z_][A-Za-z0-9_]*)=(?<value>.*)$') {
            throw ".env 형식이 올바르지 않습니다: ${Path}:$lineNumber"
        }
        $name = $Matches['name']
        $value = $Matches['value'].Trim()
        if ($value.Length -ge 2 -and (
                ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))
            )) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
    return $true
}

function Set-ArcadiaDotEnvValue {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter(Mandatory)]
        [string]$Name,

        [Parameter(Mandatory)]
        [string]$Value
    )

    if ($Value -match "[\r\n]") {
        throw "$Name 값에는 줄바꿈을 사용할 수 없습니다."
    }

    $updated = $false
    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        if ($line -match ("^\s*" + [Regex]::Escape($Name) + "\s*=")) {
            $lines.Add("$Name=$Value")
            $updated = $true
        } else {
            $lines.Add($line)
        }
    }
    if (-not $updated) {
        $lines.Add("$Name=$Value")
    }
    [System.IO.File]::WriteAllLines(
        $Path,
        $lines,
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Read-ArcadiaSecret {
    param([Parameter(Mandatory)][string]$Label)

    $secureValue = Read-Host "$Label 입력 (화면과 로그에 표시되지 않음)" -AsSecureString
    $credential = New-Object System.Management.Automation.PSCredential(
        'arcadia-local',
        $secureValue
    )
    $value = $credential.GetNetworkCredential().Password
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Label 값이 비어 있습니다."
    }
    return $value
}
