param([switch]$SkipBuild,[switch]$SplitSearch)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runRoot = Join-Path $projectRoot 'target/local-validation'
New-Item -ItemType Directory -Force $runRoot | Out-Null
$ports = 8080,5174,13306,16379,19200,16333,19876,10911,10909
if ($SplitSearch) { $ports += 8081 }
$occupied = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object LocalPort -in $ports
if ($occupied) { throw 'Local ports are already in use. Check the existing local instance before starting another one.' }
Push-Location $projectRoot
$started = @()
try {
    if (-not $SkipBuild) {
        $settings = Join-Path $projectRoot 'target/report-build-settings.xml'
        if (Test-Path -LiteralPath $settings) { & mvn -s $settings -DskipTests package }
        else { & mvn -DskipTests package }
        if ($LASTEXITCODE -ne 0) { throw 'Backend package failed' }
    }
    $jar = Join-Path $runRoot 'pms-local.jar'
    Copy-Item -LiteralPath (Join-Path $projectRoot 'apps/pms-app/target/PMS-0.0.1-SNAPSHOT.jar') -Destination $jar -Force
    $tunnelArguments = @('-N','-o','BatchMode=yes','-o','ExitOnForwardFailure=yes','-o','ServerAliveInterval=30')
    foreach ($forward in @('13306:3306','16379:6379','19200:9200','16333:6333','19876:9876','10911:10911','10909:10909')) {
        $pair = $forward.Split(':')
        $tunnelArguments += @('-L',('127.0.0.1:' + $pair[0] + ':127.0.0.1:' + $pair[1]))
    }
    $tunnelArguments += 'ubuntu@111.229.254.252'
    $tunnel = Start-Process ssh.exe -ArgumentList $tunnelArguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runRoot 'ssh.out.log') -RedirectStandardError (Join-Path $runRoot 'ssh.err.log')
    $started += $tunnel
    $tunnel.Id | Set-Content (Join-Path $runRoot 'ssh.pid')
    Start-Sleep -Milliseconds 800
    if ($tunnel.HasExited) { throw 'SSH forwarding failed; inspect ssh.err.log' }
    $backendArguments = @('-Dfile.encoding=UTF-8','-Duser.timezone=Asia/Shanghai','-jar',('"' + $jar + '"'),
        '--spring.profiles.active=local-validation',
        '--spring.datasource.url=jdbc:mysql://127.0.0.1:13306/pet_system?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true&rewriteBatchedStatements=true',
        '--spring.data.redis.host=127.0.0.1','--spring.data.redis.port=16379',
        '--elasticsearch.host=127.0.0.1','--elasticsearch.port=19200',
        '--spring.elasticsearch.uris=http://127.0.0.1:19200','--rocketmq.name-server=127.0.0.1:19876')
    $previousSearchToken=$env:PMS_SEARCH_SERVICE_TOKEN
    $previousMainToken=$env:PMS_SEARCH_TOKEN
    try {
        if ($SplitSearch) {
            $env:PMS_SEARCH_SERVICE_TOKEN=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
            $env:PMS_SEARCH_TOKEN=$env:PMS_SEARCH_SERVICE_TOKEN
            $searchJar=Join-Path $runRoot 'pms-search-service.jar'
            Copy-Item -LiteralPath (Join-Path $projectRoot 'services/pms-search-service/target/pms-search-service-0.0.1-SNAPSHOT.jar') -Destination $searchJar -Force
            $searchArgs=@('-Dfile.encoding=UTF-8','-jar',('"'+$searchJar+'"'),'--pms.search-service.elasticsearch-url=http://127.0.0.1:19200','--spring.elasticsearch.uris=http://127.0.0.1:19200')
            $search=Start-Process java.exe -ArgumentList $searchArgs -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runRoot 'search.out.log') -RedirectStandardError (Join-Path $runRoot 'search.err.log')
            $started += $search
            $search.Id | Set-Content (Join-Path $runRoot 'search.pid')
            $backendArguments += @('--pms.search.mode=remote','--pms.search.url=http://127.0.0.1:8081')
        }
        $backend = Start-Process java.exe -ArgumentList $backendArguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runRoot 'backend.out.log') -RedirectStandardError (Join-Path $runRoot 'backend.err.log')
    } finally {
        $env:PMS_SEARCH_SERVICE_TOKEN=$previousSearchToken
        $env:PMS_SEARCH_TOKEN=$previousMainToken
    }
    $started += $backend
    $backend.Id | Set-Content (Join-Path $runRoot 'backend.pid')
    $previousTarget = $env:PMS_API_TARGET
    try {
        $env:PMS_API_TARGET = 'http://127.0.0.1:8080'
        $frontend = Start-Process node.exe -ArgumentList @('node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','5174','--strictPort') -WorkingDirectory (Join-Path $projectRoot 'PMS-react') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runRoot 'frontend.out.log') -RedirectStandardError (Join-Path $runRoot 'frontend.err.log')
    } finally { $env:PMS_API_TARGET = $previousTarget }
    $started += $frontend
    $frontend.Id | Set-Content (Join-Path $runRoot 'frontend.pid')
    [pscustomobject]@{Frontend='http://127.0.0.1:5174';Backend='http://127.0.0.1:8080';SearchService=$(if($SplitSearch){'http://127.0.0.1:8081'}else{$null});BackendPid=$backend.Id;FrontendPid=$frontend.Id;TunnelPid=$tunnel.Id;Logs=$runRoot}
} catch {
    foreach ($process in $started) { if (-not $process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue } }
    throw
} finally { Pop-Location }
