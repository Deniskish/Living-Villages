$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools') -Directory -Filter 'jdk-21*' | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user'
$env:DEBUG = ''
$ErrorActionPreference = 'Continue'
& (Join-Path $projectRoot 'gradlew.bat') @args
exit $LASTEXITCODE
