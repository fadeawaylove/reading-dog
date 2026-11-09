param([switch]$NativeVectors)
$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$true
$root=$PSScriptRoot
& "$root/build.ps1"
New-Item -ItemType Directory -Force "$root/build/test-classes" | Out-Null
$separator=[IO.Path]::PathSeparator
$compilePath="$root/build/classes$separator$root/build/lib/*"
$testPath="$root/build/test-classes$separator$compilePath"
& javac -encoding UTF-8 -cp $compilePath -d "$root/build/test-classes" "$root/tests/com/example/yuejie/BackendChecks.java" "$root/tests/com/example/yuejie/BackendRecoveryChecks.java"
& java -cp $testPath com.example.yuejie.BackendChecks
& java -cp $testPath com.example.yuejie.BackendRecoveryChecks
& javac -encoding UTF-8 -cp $compilePath -d "$root/build/test-classes" "$root/tests/com/example/yuejie/LaneChecks.java"
& java -cp $testPath com.example.yuejie.LaneChecks
if($NativeVectors){
 $app=Join-Path (Split-Path -Parent $root) android-replica
 & javac -encoding UTF-8 -cp $compilePath -d "$root/build/test-classes" "$app/tests/ProtocolCheck.java" "$root/tests/com/example/yuejie/TextParityVectors.java"
 & java -cp $testPath com.example.yuejie.ProtocolCheck "$app/tests/vectors.tsv"
 New-Item -ItemType Directory -Force "$app/tests/sync-build/assets" | Out-Null
 & java -cp $testPath com.example.yuejie.TextParityVectors "$app/tests/vectors.tsv" "$app/tests/sync-build/assets/text-parity.json"
}
