@ECHO OFF
SETLOCAL
SET "GRADLE_VERSION=8.7"
SET "GRADLE_HOME_DIR=%USERPROFILE%\.gradle\wrapper\manual"
SET "GRADLE_DIR=%GRADLE_HOME_DIR%\gradle-%GRADLE_VERSION%"
SET "GRADLE_EXE=%GRADLE_DIR%\bin\gradle.bat"
SET "GRADLE_ZIP=%GRADLE_HOME_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
SET "DIST_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

IF NOT EXIST "%GRADLE_EXE%" (
  ECHO [gradlew] Gradle %GRADLE_VERSION% is not cached; downloading it now.
  IF NOT EXIST "%GRADLE_HOME_DIR%" MKDIR "%GRADLE_HOME_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing -Uri '%DIST_URL%' -OutFile '%GRADLE_ZIP%'"
  IF ERRORLEVEL 1 (
    ECHO [gradlew] Error: Gradle download failed.
    EXIT /B 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Expand-Archive -LiteralPath '%GRADLE_ZIP%' -DestinationPath '%GRADLE_HOME_DIR%' -Force"
  IF ERRORLEVEL 1 (
    ECHO [gradlew] Error: Gradle archive extraction failed.
    EXIT /B 1
  )
  DEL /Q "%GRADLE_ZIP%" >NUL 2>NUL
)

IF NOT EXIST "%GRADLE_EXE%" (
  ECHO [gradlew] Error: Gradle was not found at "%GRADLE_EXE%".
  EXIT /B 1
)

CALL "%GRADLE_EXE%" %*
EXIT /B %ERRORLEVEL%
