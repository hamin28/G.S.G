param(
    [ValidateSet('server','client','check')][string]$Mode='client',
    [string]$JdkHome=$env:JAVA_HOME,
    [string]$ServerHost='localhost',
    [int]$Port=9999,
    [string]$Nickname=''
)
$ErrorActionPreference='Stop'
$OutputEncoding=[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false)
if($JdkHome) { $java=Join-Path $JdkHome 'bin/java.exe' }
else { $java=(Get-Command java -ErrorAction Stop).Source }
$cp=(Join-Path $PSScriptRoot 'target/classes')+';'+(Join-Path $PSScriptRoot 'lib/gson-2.13.1.jar')
$options=@('-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-Dsun.stderr.encoding=UTF-8','-cp',$cp)
if($Mode -eq 'server') { & $java @options server.ServerMain $Port }
elseif($Mode -eq 'client') {
    $clientArgs=@($ServerHost,[string]$Port)
    if($Nickname) { $clientArgs+=$Nickname }
    & $java @options gsg.client.ui.ClientMain @clientArgs
} else {
    foreach($class in @('gallaemallae.game.GameRulesCheck','gallaemallae.game.FullGameCheck','server.RepositoryIntegrationCheck')) {
        & $java @options $class
        if($LASTEXITCODE -ne 0) { throw "$class 실패" }
    }
}
