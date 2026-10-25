$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$root\style-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\style-build\dex" -ItemType Directory -Force | Out-Null
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$jar;$root\..\comment-style-build\classes" -d "$root\style-build\classes" "$root\DeviceChecks.java" "$root\CommentChecks.java" "$root\AlignmentChecks.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$root\style-build\unsigned.apk" -I $jar --manifest "$root\CommentsManifest.xml" -A "$root\assets" --min-sdk-version 24 --target-sdk-version 28
& "$bt\d8.bat" --lib $jar --classpath "$root\..\comment-style-build\classes" --output "$root\style-build\dex" (Get-ChildItem "$root\style-build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& "$java\jar.exe" uf "$root\style-build\unsigned.apk" -C "$root\style-build\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$root\style-build\unsigned.apk" "$root\style-build\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$root\style-build\checks.apk" "$root\style-build\aligned.apk"
