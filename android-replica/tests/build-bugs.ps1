$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$root\bugs-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\bugs-build\dex" -ItemType Directory -Force | Out-Null
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$jar;$root\..\comments-build\classes" -d "$root\bugs-build\classes" "$root\DeviceChecks.java" "$root\TurnChecks.java" "$root\ParityChecks.java" "$root\PageChecks.java" "$root\ReaderBugChecks.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$root\bugs-build\unsigned.apk" -I $jar --manifest "$root\BugsManifest.xml" -A "$root\assets" --min-sdk-version 24 --target-sdk-version 28
& "$bt\d8.bat" --lib $jar --classpath "$root\..\comments-build\classes" --output "$root\bugs-build\dex" (Get-ChildItem "$root\bugs-build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& "$java\jar.exe" uf "$root\bugs-build\unsigned.apk" -C "$root\bugs-build\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$root\bugs-build\unsigned.apk" "$root\bugs-build\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$root\bugs-build\checks.apk" "$root\bugs-build\aligned.apk"


