@echo off
rem Tetrano wingraph: computes the winning graph of the Unbeatable level (see README.md).
rem Double-click to start or resume. To pause: wingraph-stop.bat (saves, then stops).
rem Extra arguments are passed on, e.g.  wingraph.bat threads=8
title Tetrano wingraph
chcp 65001 >nul
cd /d "%~dp0"
set "ROOT=%~dp0..\.."
set "RUN=runs\main"
if not exist "%ROOT%\wingraph\build\install\wingraph\lib" (
    echo Programme absent : depuis la racine du depot, lancer d'abord
    echo     gradlew :wingraph:installDist
    pause
    exit /b 1
)
if not exist "%RUN%" mkdir "%RUN%"
rem The run uses its own copy of the program: rebuilding while it runs is safe.
robocopy "%ROOT%\wingraph\build\install\wingraph" "%RUN%\app" /MIR /NFL /NDL /NJH /NJS /NP >nul
if not defined WINGRAPH_XMX set "WINGRAPH_XMX=16g"
set "WINGRAPH_OPTS=-Xmx%WINGRAPH_XMX% -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8"
echo Tetrano wingraph : graphe de gain force (niveau Imbattable), dossier tools\wingraph\%RUN%
echo Suivi : wingraph-status.bat (ou %RUN%\status.txt). Pause : wingraph-stop.bat.
echo Relancer ce fichier reprend la ou le calcul s'est arrete.
echo.
start "" /B /BELOWNORMAL /WAIT cmd /c ""%RUN%\app\bin\wingraph.bat" run dir=%RUN% model="%ROOT%\app\src\main\res\raw\tetrano_nn.bin" %*"
echo.
echo wingraph arrete. Relancer wingraph.bat pour reprendre.
pause
