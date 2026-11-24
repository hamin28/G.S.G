param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference='Stop'
$OutputEncoding=[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false)
if ($JdkHome) {
    $javac=Join-Path $JdkHome 'bin/javac.exe'
} else { $javac=(Get-Command javac -ErrorAction Stop).Source }
$gson=Join-Path $PSScriptRoot 'lib/gson-2.13.1.jar'
if (!(Test-Path -LiteralPath $gson)) {
    $cached=Join-Path $env:USERPROFILE '.m2/repository/com/google/code/gson/gson/2.13.1/gson-2.13.1.jar'
    if (!(Test-Path -LiteralPath $cached)) { throw 'Gson 2.13.1이 필요합니다. IntelliJ에서 루트 pom.xml을 Maven 프로젝트로 불러오세요.' }
    New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'lib') -Force | Out-Null
    Copy-Item -LiteralPath $cached -Destination $gson
}
$classes=Join-Path $PSScriptRoot 'target/classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sourceRoots=@('src/UI','src/server','src/GameLogic/src/main/java','src/GameLogic/src/test/java','integration-tests')
$sources=@(foreach($source in $sourceRoots) {
    $path=Join-Path $PSScriptRoot $source
    if(Test-Path -LiteralPath $path) { Get-ChildItem -LiteralPath $path -Recurse -Filter '*.java' | ForEach-Object FullName }
})
& $javac --release 21 -encoding UTF-8 -cp $gson -d $classes $sources
if($LASTEXITCODE -ne 0) { throw '컴파일 실패' }
$images=Join-Path $classes 'resource/hwatu_image'
New-Item -ItemType Directory -Path $images -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'hwatu_image') -File | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $images
}
Write-Output 'BUILD PASSED: GitHub UI + server + game logic (Java 21)'
