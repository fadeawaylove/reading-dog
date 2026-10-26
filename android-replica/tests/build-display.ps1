$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$root\display-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\display-build\dex" -ItemType Directory -Force | Out-Null
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$jar;$root\..\touch-build\classes" -d "$root\display-build\classes" "$root\DisplayChecks.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$root\display-build\unsigned.apk" -I $jar --manifest "$root\DisplayManifest.xml" -A "$root\assets" --min-sdk-version 24 --target-sdk-version 28
& "$bt\d8.bat" --lib $jar --classpath "$root\..\touch-build\classes" --output "$root\display-build\dex" (Get-ChildItem "$root\display-build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& "$java\jar.exe" uf "$root\display-build\unsigned.apk" -C "$root\display-build\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$root\display-build\unsigned.apk" "$root\display-build\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$root\display-build\checks.apk" "$root\display-build\aligned.apk"
