@echo off
set VERSION=9.5.0
set ROOT=%~dp0
set CACHE=%USERPROFILE%\.gradle\gnomes-bootstrap\%VERSION%
set GRADLE=%CACHE%\gradle-%VERSION%\bin\gradle.bat
if not exist "%GRADLE%" (
  if not exist "%CACHE%" mkdir "%CACHE%"
  powershell -NoProfile -Command "$u='https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip';$z='%CACHE%\\gradle-%VERSION%-bin.zip';Invoke-WebRequest -Uri $u -OutFile $z;Expand-Archive -Force $z '%CACHE%'"
)
call "%GRADLE%" -p "%ROOT%" %*
