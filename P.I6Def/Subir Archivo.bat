@echo off
title Subir Cambios a GitHub
cd /d "%~dp0"
cls

:: Activar colores ANSI
for /F "delims=" %%e in ('echo prompt $E^| cmd') do set "ESC=%%e"

echo %ESC%[97m=========================================
echo %ESC%[91m        SUBIR CAMBIOS AL REPOSITORIO
echo %ESC%[97m=========================================
echo %ESC%[95mby:
echo %ESC%[92m   - Miguel Angel Parra Gutierrez
echo %ESC%[96m   - Sergio Penaranda Agudelo
echo %ESC%[94m   - David Ortega Ruiz
echo %ESC%[95m   - Juan Fernando Urriago Hernandez
echo %ESC%[0m

set /p COMENTARIO=Escribe tu comentario para el commit: 

if "%COMENTARIO%"=="" (
    echo %ESC%[93mDebes escribir un comentario.%ESC%[0m
    pause
    exit /b
)

echo.
echo %ESC%[93mAgregando archivos...%ESC%[0m
git add .

echo %ESC%[93mCreando commit...%ESC%[0m
git commit -m "%COMENTARIO%"

echo %ESC%[93mSubiendo cambios...%ESC%[0m
git push

echo.
echo %ESC%[92m=========================================
echo %ESC%[92m   LISTO! Cambios subidos correctamente
echo %ESC%[92m=========================================
echo %ESC%[0m

pause