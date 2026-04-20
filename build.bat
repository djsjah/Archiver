@echo off
cd /d %~dp0

if exist out rmdir /s /q out
mkdir out

javac -d out ^
src\common\ArchiveConstants.java ^
src\common\io\BitOutput.java ^
src\common\io\BitInput.java ^
src\common\arithmetic\ArithmeticEncoder.java ^
src\common\arithmetic\ArithmeticDecoder.java ^
src\common\model\FenwickTree.java ^
src\common\model\CompositionCoder.java ^
src\common\model\SparseHeaderCoder.java ^
src\encoder\NumerationEncoder.java ^
src\encoder\Main.java ^
src\decoder\NumerationDecoder.java ^
src\decoder\Main.java ^
src\stats\StatsConstants.java ^
src\stats\EncodeStats.java ^
src\stats\Main.java ^
src\stats\StatsRunner.java

if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)

echo Build completed successfully.
pause