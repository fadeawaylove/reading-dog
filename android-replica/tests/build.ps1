$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$root\build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\build\dex" -ItemType Directory -Force | Out-Null
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$jar;$root\..\build\classes" -d "$root\build\classes" "$root\DeviceChecks.java" "$root\TurnChecks.java" "$root\ShelfChecks.java" "$root\ParityChecks.java" "$root\ReferenceCapture.java" "$root\MatchedCapture.java" "$root\RestoreTurnState.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$root\build\unsigned.apk" -I $jar --manifest "$root\AndroidManifest.xml" -A "$root\assets" --min-sdk-version 24 --target-sdk-version 28
& "$bt\d8.bat" --lib $jar --classpath "$root\..\build\classes" --output "$root\build\dex" (Get-ChildItem "$root\build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& "$java\jar.exe" uf "$root\build\unsigned.apk" -C "$root\build\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$root\build\unsigned.apk" "$root\build\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$root\build\checks.apk" "$root\build\aligned.apk"
