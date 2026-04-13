# VigileX build helper — sets JAVA_HOME to Android Studio's bundled JDK
# Run from the vigilex project root: .\build.ps1

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "Using Java: $(& java -version 2>&1 | Select-Object -First 1)" -ForegroundColor Cyan
Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Cyan

# Stop any stuck daemons first
Write-Host "`nStopping existing Gradle daemons..." -ForegroundColor Yellow
& .\gradlew.bat --stop

# Clean + assemble debug
Write-Host "`nBuilding debug APK..." -ForegroundColor Yellow
& .\gradlew.bat assembleDebug --info --stacktrace 2>&1 | Tee-Object -FilePath build_output.txt

Write-Host "`nBuild complete. Output saved to build_output.txt" -ForegroundColor Green
