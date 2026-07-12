@echo off
setlocal EnableDelayedExpansion
echo ======================================================================
echo   RBL BENCHMARK: Hash Table vs Bloom Filter
echo   CSD201 — Summer 2026
echo   Memory Cross-over Analysis
echo ======================================================================
echo.

REM Create output directories
mkdir results 2>nul
mkdir out 2>nul

REM Clean old results before appending
echo [1/3] Cleaning old CSV files...
del /q results\*.csv 2>nul

REM Compile all Java source files
echo.
echo [2/3] Compiling Java sources...
javac -d out src\*.java
if errorlevel 1 (
    echo.
    echo *** COMPILATION FAILED ***
    pause
    exit /b 1
)
echo   Compilation successful!
echo.

REM Run benchmark across multiple memory budgets
echo [3/3] Running benchmarks across different memory tiers...
echo.

for %%M in (16 32 64 128 256) do (
    echo =======================================================
    echo   RUNNING WITH %%M MB MEMORY BUDGET
    echo =======================================================
    java -cp out -Xmx%%Mm BenchmarkRunner --budget %%M
    echo.
)

echo.
echo ======================================================================
echo   DONE! Check the 'results' folder for aggregated CSV output.
echo ======================================================================
