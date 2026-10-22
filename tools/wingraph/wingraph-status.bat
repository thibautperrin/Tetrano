@echo off
rem Shows where wingraph stands (status.txt, last log lines).
title Tetrano wingraph - suivi
chcp 65001 >nul
cd /d "%~dp0"
set "RUN=runs\main"
if not exist "%RUN%\app\bin\wingraph.bat" (
    echo Aucun calcul dans tools\wingraph\%RUN% : lancer wingraph.bat.
    pause
    exit /b 0
)
set "WINGRAPH_OPTS=-Xmx256m -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8"
call "%RUN%\app\bin\wingraph.bat" status dir=%RUN%
echo.
pause
