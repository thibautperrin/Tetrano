@echo off
rem Stops the battle (python, java and the phones' runs); battle.bat resumes it.
title Tetrano battle - arret
cd /d "%~dp0"
".venv\Scripts\python.exe" battle.py --stop %*
echo.
".venv\Scripts\python.exe" battle.py --status %*
echo.
pause
