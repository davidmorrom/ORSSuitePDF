@echo off
REM ============================================================================
REM  Genera el instalador Windows (.exe) de ORS Suite PDF con jpackage + WiX.
REM
REM  Uso:  scripts\package-windows.cmd [version]
REM        (version por defecto: 1.0.0)
REM
REM  El UpgradeCode es FIJO: gracias a el, cada instalador con una version
REM  superior ACTUALIZA en su sitio a la version anterior (no hace falta
REM  desinstalar). Sube la version en cada release para que el reemplazo
REM  automatico funcione.
REM ============================================================================
setlocal

set VERSION=%1
if "%VERSION%"=="" set VERSION=1.0.0

REM UpgradeCode permanente del producto (no cambiar nunca).
set UPGRADE_UUID=b23ad378-22ac-4b04-a9b4-fb3fd3f74db0

REM Runtime completo: incluye jdk.crypto.mscapi (almacen de Windows) y
REM jdk.crypto.cryptoki (DNIe/PKCS#11), que la firma necesita. Ajusta la ruta
REM si usas otro JDK.
set RUNTIME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot

REM WiX Toolset 3.x en el PATH (requerido por jpackage para --type exe).
set PATH=%PATH%;C:\Program Files (x86)\WiX Toolset v3.14\bin

echo [1/3] Compilando el fat jar...
call mvnw.cmd -q -DskipTests package
if errorlevel 1 exit /b 1

echo [2/3] Preparando staging (jar + tessdata)...
rmdir /s /q target\stage 2>nul
mkdir target\stage
copy /y target\ors-suite-pdf-0.1.0.jar target\stage\ >nul
if exist tessdata xcopy /e /i /y tessdata target\stage\tessdata >nul

echo [3/3] Generando instalador (version %VERSION%)...
rmdir /s /q target\installer 2>nul
jpackage --type exe ^
  --name "ORS Suite PDF" ^
  --input target\stage ^
  --main-jar ors-suite-pdf-0.1.0.jar ^
  --main-class com.orsconsulting.orssuitepdf.core.Launcher ^
  --runtime-image "%RUNTIME%" ^
  --app-version %VERSION% ^
  --vendor "ORS Consulting" ^
  --description "Editor de PDF profesional offline-first" ^
  --dest target\installer ^
  --win-upgrade-uuid %UPGRADE_UUID% ^
  --java-options "--enable-native-access=ALL-UNNAMED" ^
  --java-options "-Dtessdata.dir=$APPDIR\tessdata" ^
  --win-shortcut --win-menu --win-dir-chooser
if errorlevel 1 exit /b 1

echo.
echo Instalador generado en: target\installer\ORS Suite PDF-%VERSION%.exe
endlocal
