@echo off
rem Tetrano: pre-training + self-play training of the 64 x 10 network (tools/nn/autotrain.py).
rem Double-click to start or resume. To stop: close this window, Ctrl+C, or autotrain-stop.bat.
rem Extra arguments are passed on, e.g.  autotrain.bat --until 07:30
title Tetrano autotrain
cd /d "%~dp0"
echo Tetrano autotrain: pre-entrainement puis auto-jeu du reseau 64 x 10 (runs\big).
echo Pour arreter : fermer cette fenetre, Ctrl+C, ou double-cliquer autotrain-stop.bat.
echo Relancer reprend la ou ca s'est arrete. Suivi : runs\big\log.txt, autotrain.py --status
echo.
".venv\Scripts\python.exe" -u autotrain.py %*
echo.
echo autotrain termine (code %ERRORLEVEL%). Relancer autotrain.bat pour reprendre.
pause
