$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runName = 'run-restart-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$runPath = Join-Path $projectRoot $runName
New-Item -ItemType Directory -Path $runPath | Out-Null
Set-Content -LiteralPath (Join-Path $runPath 'server.properties') -Value 'level-name=world' -Encoding Ascii
foreach ($phase in @('Write','Read')) {
    & (Join-Path $PSScriptRoot 'gradle.ps1') "runRestart$phase" "-PacceptanceDir=$runName" *> (Join-Path $runPath "$phase.log")
    if ($LASTEXITCODE -ne 0) {
        Get-Content (Join-Path $runPath "$phase.log") -Tail 50
        throw "Restart $phase failed; retained evidence: $runPath"
    }
}
Write-Output "Two separate server JVMs passed. Retained world and logs: $runPath"
