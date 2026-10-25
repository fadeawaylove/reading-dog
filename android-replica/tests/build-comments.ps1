$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$root\comments-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\comments-build\dex" -ItemType Directory -Force | Out-Null
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$jar;$root\..\build\classes" -d "$root\comments-build\classes" "$root\DeviceChecks.java" "$root\CommentChecks.java" "$root\AlignmentChecks.java" "$root\ReaderUpdateChecks.java" "$root\ReaderProgressUpdateChecks.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$root\comments-build\unsigned.apk" -I $jar --manifest "$root\CommentsManifest.xml" -A "$root\assets" --min-sdk-version 24 --target-sdk-version 28
& "$bt\d8.bat" --lib $jar --classpath "$root\..\build\classes" --output "$root\comments-build\dex" (Get-ChildItem "$root\comments-build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& "$java\jar.exe" uf "$root\comments-build\unsigned.apk" -C "$root\comments-build\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$root\comments-build\unsigned.apk" "$root\comments-build\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$root\comments-build\checks.apk" "$root\comments-build\aligned.apk"
