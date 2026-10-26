@echo off
setlocal

set "TOOLCHAIN_ROOT=%LOCALAPPDATA%\Programs\Meteoro\toolchains\msys64"
set "MINGW_BIN=%TOOLCHAIN_ROOT%\mingw64\bin"
set "GCC=%MINGW_BIN%\gcc.exe"

if not exist "%GCC%" (
    echo No se encontro MinGW en "%MINGW_BIN%". Instala la toolchain portatil de Meteoro primero.
    exit /b 1
)

set "PATH=%MINGW_BIN%;%PATH%"
set "CARGO_TARGET_X86_64_PC_WINDOWS_GNU_LINKER=%GCC%"

cargo +stable-x86_64-pc-windows-gnu run --manifest-path "%~dp0..\Cargo.toml" -- %*
exit /b %ERRORLEVEL%
