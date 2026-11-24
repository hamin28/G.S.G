param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
if ($JdkHome) {
    $compiler = Join-Path $JdkHome 'bin/javac.exe'
    $runtime = Join-Path $JdkHome 'bin/java.exe'
} else {
    $compiler = (Get-Command javac -ErrorAction Stop).Source
    $runtime = (Get-Command java -ErrorAction Stop).Source
}
if (!(Test-Path -LiteralPath $compiler) -or !(Test-Path -LiteralPath $runtime)) {
    throw 'JDK 경로를 확인하고 -JdkHome 옵션으로 지정하세요.'
}
$build = Join-Path $PSScriptRoot 'target/classes'
New-Item -ItemType Directory -Path $build -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& $compiler -source 8 -target 8 -Xlint:-options -encoding UTF-8 -d $build $sources
if ($LASTEXITCODE -ne 0) { throw 'Java 컴파일 실패' }
$output = [System.Collections.Generic.List[string]]::new()
foreach ($class in @('GameRulesCheck','Step5Check','Step6Check','Step7Check','FullGameCheck')) {
    $source = Join-Path $PSScriptRoot "src/test/java/gallaemallae/game/$class.java"
    if (Test-Path -LiteralPath $source) {
        $lines = @(& $runtime '-Dfile.encoding=UTF-8' '-Dsun.stdout.encoding=UTF-8' '-Dsun.stderr.encoding=UTF-8' -cp $build "gallaemallae.game.$class" 2>&1)
        $resultCode = $LASTEXITCODE
        foreach ($line in $lines) { Write-Output $line; $output.Add([string]$line) }
        if ($resultCode -ne 0) { throw "$class 검사 실패" }
    }
}
$output | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'TEST_RESULTS.txt') -Encoding UTF8
Write-Output 'ALL CHECKS PASSED'
