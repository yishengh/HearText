param([switch]$Connected, [string]$Serial, [switch]$OfflineFixture)

$ErrorActionPreference = 'Stop'
if ($OfflineFixture -and -not $Connected) { throw '-OfflineFixture requires -Connected and an emulator serial.' }
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $env:JAVA_HOME) {
    $bundledJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath (Join-Path $bundledJdk 'bin\java.exe')) {
        $env:JAVA_HOME = $bundledJdk
    }
}
$tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
if ($Connected) {
    if (-not $Serial) { throw 'Use -Connected -Serial <adb-device-serial> to select the test emulator explicitly.' }
    $tasks += ':app:connectedDebugAndroidTest'
}
$previousSerial = $env:ANDROID_SERIAL
if ($Connected) { $env:ANDROID_SERIAL = $Serial }
Push-Location $projectRoot
$fixtureServer = $null
try {
    $extraArguments = @()
    if ($OfflineFixture) {
        & python tools/prepare-offline-fixture.py
        if ($LASTEXITCODE -ne 0) { throw 'Unable to prepare the official offline voice fixture.' }
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
        $listener.Start()
        $fixturePort = $listener.LocalEndpoint.Port
        $listener.Stop()
        $serverDir = Join-Path $projectRoot 'build/local-validation/voice-fixtures/server'
        $fixtureServer = Start-Process -FilePath (Get-Command python).Source -ArgumentList @(
            '-m', 'http.server', "$fixturePort", '--bind', '127.0.0.1', '--directory', "`"$serverDir`""
        ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $projectRoot 'build/local-validation/voice-server.out.log') -RedirectStandardError (Join-Path $projectRoot 'build/local-validation/voice-server.err.log')
        $ready = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            if ($fixtureServer.HasExited) { throw 'Local fixture server exited.' }
            try {
                $null = Invoke-WebRequest "http://127.0.0.1:$fixturePort/checksum.txt" -TimeoutSec 1
                $ready = $true
                break
            } catch { Start-Sleep -Milliseconds 100 }
        }
        if (-not $ready) { throw 'Local fixture server did not become ready.' }
        $extraArguments += "-Pandroid.testInstrumentationRunnerArguments.offlineFixtureEndpoint=http://10.0.2.2:$fixturePort"
    }
    & .\gradlew.bat '-PheartextValidation=true' @tasks @extraArguments '--continue' '--console=plain' '--no-daemon'
    if ($LASTEXITCODE -ne 0) { throw "Local verification failed (exit $LASTEXITCODE). See build/validation-app/reports." }
} finally {
    if ($fixtureServer -and -not $fixtureServer.HasExited) { Stop-Process -Id $fixtureServer.Id }
    Pop-Location
    $env:ANDROID_SERIAL = $previousSerial
}
