$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$true
$root=$PSScriptRoot
$app=Split-Path -Parent $root
$out="$root/comment-prefetch-build"
$bt='C:/Android/Sdk/build-tools/35.0.0'
$android='C:/Android/Sdk/platforms/android-35/android.jar'
New-Item -ItemType Directory -Force "$out/classes","$out/dex" | Out-Null
& javac -encoding UTF-8 -source 8 -target 8 -cp "$android;$app/touch-build/classes" -d "$out/classes" "$root/CommentPrefetchChecks.java" "$root/CommentLiveChecks.java"
& "$bt/aapt2.exe" link -o "$out/unsigned.apk" -I $android --manifest "$root/CommentPrefetchManifest.xml" --min-sdk-version 24 --target-sdk-version 28
& jar cf "$out/classes.jar" -C "$out/classes" .
& "$bt/d8.bat" --lib $android --classpath "$app/touch-build/classes.jar" --output "$out/dex" "$out/classes.jar"
& jar uf "$out/unsigned.apk" -C "$out/dex" classes.dex
& "$bt/zipalign.exe" -f 4 "$out/unsigned.apk" "$out/aligned.apk"
$env:JAVA_HOME=Split-Path -Parent (Split-Path -Parent (Get-Command javac).Source)
& "$bt/apksigner.bat" sign --ks "$app/signing/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$out/checks.apk" "$out/aligned.apk"
