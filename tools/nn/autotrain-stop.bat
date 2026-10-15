@echo off
rem Stops a running autotrain (python, its children and java) from its PID file; harmless if nothing runs.
title Tetrano autotrain - arret
cd /d "%~dp0"
".venv\Scripts\python.exe" autotrain.py --stop %*
echo.
".venv\Scripts\python.exe" autotrain.py --status %*
echo.
pause
