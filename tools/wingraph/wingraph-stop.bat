@echo off
rem Pauses a running wingraph: it saves everything, then stops. Harmless if nothing runs.
title Tetrano wingraph - pause
chcp 65001 >nul
cd /d "%~dp0"
set "RUN=runs\main"
if not exist "%RUN%\app\bin\wingraph.bat" (
    echo Aucun calcul dans tools\wingraph\%RUN%.
    pause
    exit /b 0
)
set "WINGRAPH_OPTS=-Xmx256m -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8"
call "%RUN%\app\bin\wingraph.bat" stop dir=%RUN%
echo.
call "%RUN%\app\bin\wingraph.bat" status dir=%RUN%
echo.
pause
