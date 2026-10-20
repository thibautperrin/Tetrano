@echo off
rem Tetrano: night battle small (2048 sims) vs big (512 sims) network on the PC and the adb phones, then training (tools/nn/battle.py).
rem Double-click to start or resume. To stop: close this window, Ctrl+C, or battle-stop.bat.
title Tetrano battle
cd /d "%~dp0"
echo Tetrano battle : pre-entrainement du petit reseau, battle PC + telephones jusqu'a 5h, analyse, entrainement (runs\battle).
echo Pour arreter : fermer cette fenetre ou double-cliquer battle-stop.bat. Relancer reprend la ou ca s'est arrete.
echo Suivi : runs\battle\log.txt, battle.py --status. Resultat : runs\battle\report.md
echo.
".venv\Scripts\python.exe" -u battle.py %*
echo.
echo battle terminee (code %ERRORLEVEL%).
pause
