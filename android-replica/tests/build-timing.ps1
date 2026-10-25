$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$root\timing-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\timing-build\dex" -ItemType Directory -Force | Out-Null
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$jar;$root\..\touch-build\classes" -d "$root\timing-build\classes" "$root\TimingTouchChecks.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$root\timing-build\unsigned.apk" -I $jar --manifest "$root\TimingManifest.xml" -A "$root\assets" --min-sdk-version 24 --target-sdk-version 28
& "$bt\d8.bat" --lib $jar --classpath "$root\..\touch-build\classes" --output "$root\timing-build\dex" (Get-ChildItem "$root\timing-build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& "$java\jar.exe" uf "$root\timing-build\unsigned.apk" -C "$root\timing-build\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$root\timing-build\unsigned.apk" "$root\timing-build\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$root\timing-build\checks.apk" "$root\timing-build\aligned.apk"
