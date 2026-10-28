param([string]$AppBuildDirectory="$(Join-Path $PSScriptRoot '..\typography-app-build')")
$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$appClasses=Join-Path $AppBuildDirectory 'classes'
if(!(Test-Path $appClasses)){throw 'Build the app first, or specify -AppBuildDirectory'}
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$root\typography-integration-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\typography-integration-build\dex" -ItemType Directory -Force | Out-Null
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$jar;$appClasses" -d "$root\typography-integration-build\classes" "$root\DeviceChecks.java" "$root\TypographyIntegrationChecks.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$root\typography-integration-build\unsigned.apk" -I $jar --manifest "$root\TypographyIntegrationManifest.xml" -A "$root\assets" --min-sdk-version 24 --target-sdk-version 28
& "$bt\d8.bat" --lib $jar --classpath "$appClasses" --output "$root\typography-integration-build\dex" (Get-ChildItem "$root\typography-integration-build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& "$java\jar.exe" uf "$root\typography-integration-build\unsigned.apk" -C "$root\typography-integration-build\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$root\typography-integration-build\unsigned.apk" "$root\typography-integration-build\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$root\typography-integration-build\checks.apk" "$root\typography-integration-build\aligned.apk"
