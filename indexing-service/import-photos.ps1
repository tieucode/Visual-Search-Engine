[CmdletBinding()]
param(
    [string]$AiUrls = 'http://127.0.0.1:8002'
)

$ErrorActionPreference = 'Stop'
$servicePath = $PSScriptRoot
$repoPath = Split-Path -Parent $servicePath
$csvPath = Join-Path $repoPath 'infra/photos.csv'
if (-not (Test-Path -LiteralPath $csvPath -PathType Leaf)) {
    throw "CSV does not exist at $csvPath"
}

Push-Location $servicePath
try {
    & mvn -q -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/import-classpath.txt' '-DincludeScope=runtime'
    if ($LASTEXITCODE -ne 0) { throw 'Importer build failed' }
    $dependencies = (Get-Content -LiteralPath 'target/import-classpath.txt' -Raw).Trim()
    $classesPath = Join-Path $servicePath 'target/classes'
    $classPath = $classesPath + [IO.Path]::PathSeparator + $dependencies
    & java '-Xmx768m' '-Djava.awt.headless=true' -cp $classPath com.visualsearch.photoimport.PhotoImportApplication "--photo-import.csv=$csvPath" "--photo-import.ai-urls=$AiUrls"
    $importExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $importExitCode
