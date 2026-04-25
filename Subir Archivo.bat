@echo off
cd /d "%~dp0"

echo.
set /p comentario="Escribe tu comentario para el commit: "

git add .
git commit -m "%comentario%"
git push origin main

echo.
echo Listo! Cambios subidos correctamente.
pause