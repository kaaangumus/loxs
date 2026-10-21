$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root
$Jar = Join-Path $Root "build\libs\loxs-java-2.1.1-all.jar"
if (-not (Test-Path $Jar)) {
    $Gradle = Join-Path $Root "gradlew.bat"
    if (Test-Path $Gradle) {
        & $Gradle fatJar --console=plain
    } else {
        gradle fatJar --console=plain
    }
}
java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar $Jar






