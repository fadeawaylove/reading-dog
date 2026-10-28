$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$root\comment-swipe-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\comment-swipe-build\dex" -ItemType Directory -Force | Out-Null
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$jar;$root\..\touch-build\classes" -d "$root\comment-swipe-build\classes" "$root\DeviceChecks.java" "$root\CommentSwipeChecks.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$root\comment-swipe-build\unsigned.apk" -I $jar --manifest "$root\CommentSwipeManifest.xml" -A "$root\assets" --min-sdk-version 24 --target-sdk-version 28
& "$bt\d8.bat" --lib $jar --classpath "$root\..\touch-build\classes" --output "$root\comment-swipe-build\dex" (Get-ChildItem "$root\comment-swipe-build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& "$java\jar.exe" uf "$root\comment-swipe-build\unsigned.apk" -C "$root\comment-swipe-build\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$root\comment-swipe-build\unsigned.apk" "$root\comment-swipe-build\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$root\comment-swipe-build\checks.apk" "$root\comment-swipe-build\aligned.apk"
