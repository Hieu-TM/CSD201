@echo off
setlocal EnableDelayedExpansion
echo ======================================================================
echo   RBL BENCHMARK: Hash Table vs Bloom Filter
echo   CSD201 — Summer 2026
echo   Memory Cross-over Analysis
echo ======================================================================
echo.

REM Use one matching JDK for BOTH javac and java. This machine's PATH resolves
REM `javac` to a JDK 25 but bare `java` to a JRE 8, which yields
REM UnsupportedClassVersionError at runtime. Prefer JAVA_HOME if set, else fall
REM back to the known Adoptium JDK 25 install.
if defined JAVA_HOME (
    set "JDKBIN=%JAVA_HOME%\bin"
) else (
    set "JDKBIN=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin"
)

REM Create output directories
mkdir results 2>nul
mkdir out 2>nul

REM Clean old results before appending
echo [1/3] Cleaning old CSV files...
del /q results\*.csv 2>nul

REM Compile all Java source files
echo.
echo [2/3] Compiling Java sources...
"%JDKBIN%\javac" -d out src\*.java
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

REM One FRESH JVM per (budget, size). Running all sizes in a single -Xmx16m
REM JVM let heap fragmentation/garbage from earlier sizes' OOMs starve a later
REM size's allocation (e.g. the Bloom Filter would spuriously OOM at N=1M even
REM though it needs only ~2 MB), and a crash in one size lost the whole tier.
REM Isolating each run keeps every measurement independent and realistic.
for %%M in (16 32 64 128 256) do (
    echo =======================================================
    echo   RUNNING WITH %%M MB MEMORY BUDGET
    echo =======================================================
    for %%N in (1000 5000 10000 25000 50000 100000 250000 500000 1000000 2000000) do (
        "%JDKBIN%\java" -cp out -Xmx%%Mm BenchmarkRunner --budget %%M %%N
    )
    echo.
)

echo.
echo ======================================================================
echo   DONE! Check the 'results' folder for aggregated CSV output.
echo ======================================================================
