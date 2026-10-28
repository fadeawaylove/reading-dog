param([string]$AppBuildDirectory="$(Join-Path $PSScriptRoot '..\build')")
$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$appClasses=Join-Path $AppBuildDirectory 'classes'
if(!(Test-Path $appClasses)){throw 'Build the app first, or specify -AppBuildDirectory'}
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$root\readability-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\readability-build\dex" -ItemType Directory -Force | Out-Null
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$jar;$appClasses" -d "$root\readability-build\classes" "$root\DeviceChecks.java" "$root\ReadabilityChecks.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$root\readability-build\unsigned.apk" -I $jar --manifest "$root\ReadabilityManifest.xml" -A "$root\assets" --min-sdk-version 24 --target-sdk-version 28
& "$bt\d8.bat" --lib $jar --classpath "$appClasses" --output "$root\readability-build\dex" (Get-ChildItem "$root\readability-build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& "$java\jar.exe" uf "$root\readability-build\unsigned.apk" -C "$root\readability-build\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$root\readability-build\unsigned.apk" "$root\readability-build\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$root\readability-build\checks.apk" "$root\readability-build\aligned.apk"
