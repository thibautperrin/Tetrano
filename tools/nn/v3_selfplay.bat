@echo off
rem Tetrano: self-play training of the v3 network (tools/nn/v3_selfplay.py), from the pre-trained one.
rem Double-click to start or resume. To stop: close this window or Ctrl+C (resumes at the next launch).
rem Extra arguments are passed on, e.g.  v3_selfplay.bat --until 07:30
title Tetrano v3 self-play
cd /d "%~dp0"
echo Tetrano v3 : auto-jeu du reseau v3 (runs\v3\selfplay).
echo Pour arreter : fermer cette fenetre ou Ctrl+C. Relancer reprend la ou ca s'est arrete.
echo Suivi : runs\v3\selfplay\log.txt et metrics.csv
echo.
".venv\Scripts\python.exe" -u v3_selfplay.py %*
echo.
echo v3_selfplay termine (code %ERRORLEVEL%). Relancer v3_selfplay.bat pour reprendre.
pause
