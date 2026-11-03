param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$OutputDirectory)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $projectRoot 'build/rule-tests' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$mainSource = Join-Path $projectRoot 'app/src/main/java'
$testSource = Join-Path $projectRoot 'app/src/test/java'
if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_SDK_ROOT }
if (-not $AndroidSdk) { throw 'Set ANDROID_HOME to the Android SDK directory.' }
$androidJar = Join-Path $AndroidSdk 'platforms/android-35/android.jar'
if (-not (Test-Path -LiteralPath $androidJar)) { throw 'Install the Android SDK 35 platform first.' }
$tests = @(Get-ChildItem -LiteralPath $testSource -Recurse -Filter '*SelfTest.java' | Sort-Object FullName)
if ($tests.Count -eq 0) { throw 'No rule tests found.' }
$javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { '' }
$javac = if ($javaBin) { Join-Path $javaBin 'javac' } else { 'javac' }
$java = if ($javaBin) { Join-Path $javaBin 'java' } else { 'java' }
& $javac -encoding UTF-8 -cp $androidJar -d $OutputDirectory -sourcepath ($mainSource + [IO.Path]::PathSeparator + $testSource) @($tests.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Rule test compilation failed.' }
foreach ($test in $tests) {
    & $java -cp ($OutputDirectory + [IO.Path]::PathSeparator + $androidJar) ('tw.linedraw.quick.' + $test.BaseName)
    if ($LASTEXITCODE -ne 0) { throw "Rule test failed: $($test.BaseName)" }
}
Write-Output "All $($tests.Count) rule test suites passed."
