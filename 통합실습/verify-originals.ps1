$ErrorActionPreference='Stop'
$manifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'original-source-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach($file in $manifest.files) {
    $path=Join-Path $PSScriptRoot $file.path
    if(!(Test-Path -LiteralPath $path)) { throw "원본 누락: $($file.path)" }
    if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $file.sha256) { throw "원본 변경: $($file.path)" }
}
Write-Output "PASS: $($manifest.files.Count) original files unchanged; commit $($manifest.commit)"
