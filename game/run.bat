@echo off
rem Compila y ejecuta SIN Maven (solo necesitas un JDK 17 o superior)
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b src\main\java\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 exit /b 1
xcopy /s /e /y /q src\main\resources\* out\ > nul
java -Xmx1g -cp out com.pacman3d.Main
