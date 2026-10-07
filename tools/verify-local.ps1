param([switch]$Connected, [string]$Serial)

$ErrorActionPreference = 'Stop'
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
try {
    & .\gradlew.bat '-PheartextValidation=true' @tasks '--continue' '--console=plain' '--no-daemon'
    if ($LASTEXITCODE -ne 0) { throw "Local verification failed (exit $LASTEXITCODE). See build/validation-app/reports." }
} finally {
    Pop-Location
    $env:ANDROID_SERIAL = $previousSerial
}
