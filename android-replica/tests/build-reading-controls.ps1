$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$root\reading-controls-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\reading-controls-build\dex" -ItemType Directory -Force | Out-Null
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$jar;$root\..\controls-build\classes" -d "$root\reading-controls-build\classes" "$root\DeviceChecks.java" "$root\ReadingControlsChecks.java"
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$root\reading-controls-build\unsigned.apk" -I $jar --manifest "$root\ReadingControlsManifest.xml" -A "$root\assets" --min-sdk-version 24 --target-sdk-version 28
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
& "$bt\d8.bat" --lib $jar --classpath "$root\..\controls-build\classes" --output "$root\reading-controls-build\dex" (Get-ChildItem "$root\reading-controls-build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
& "$java\jar.exe" uf "$root\reading-controls-build\unsigned.apk" -C "$root\reading-controls-build\dex" classes.dex
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
& "$bt\zipalign.exe" -f 4 "$root\reading-controls-build\unsigned.apk" "$root\reading-controls-build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$root\reading-controls-build\checks.apk" "$root\reading-controls-build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
