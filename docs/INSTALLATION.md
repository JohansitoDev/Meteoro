# Instalar Meteoro en Windows

Meteoro y sus herramientas se instalan en el perfil del usuario; no se
necesitan permisos de administrador.

## Requisitos

- Windows 10 o posterior.
- Android Studio con Android SDK Platform Tools y Android Emulator.
- JDK incluido con Android Studio.
- Gradle 9.3.1. Meteoro usa la distribución indicada por la plantilla Android;
  puede estar en el caché de Gradle o disponible como `gradle.bat` en `PATH`.
- Para compilar el CLI desde el código fuente: Rust y la toolchain GNU de
  Windows. El helper `scripts\meteoro.cmd` configura la toolchain portátil de
  Meteoro que se instaló en `%LOCALAPPDATA%\Programs\Meteoro\toolchains`.

En Android Studio abre **SDK Manager** y comprueba que estén instalados Android
SDK Platform, Platform Tools y Android Emulator. En **Device Manager**, crea
al menos un emulador virtual. También puedes conectar un dispositivo Android
con la depuración USB habilitada.

## Usar el CLI desde este repositorio

Abre PowerShell en la carpeta del repositorio:

```powershell
.\scripts\meteoro.cmd --help
```

El helper evita cambiar la política de ejecución de PowerShell. Para compilar
el CLI e instalarlo en el perfil del usuario:

```powershell
$env:PATH = "$env:LOCALAPPDATA\Programs\Meteoro\toolchains\msys64\mingw64\bin;$env:PATH"
$env:CARGO_TARGET_X86_64_PC_WINDOWS_GNU_LINKER = "$env:LOCALAPPDATA\Programs\Meteoro\toolchains\msys64\mingw64\bin\gcc.exe"
cargo +stable-x86_64-pc-windows-gnu install --path .
```

La instalación incluye los comandos `meteoro` y `meteoro-cli`; usa `meteoro`
para crear y trabajar en proyectos. Para actualizar el CLI, repite
`cargo install`.

## Android SDK y Gradle

Meteoro busca el SDK en `ANDROID_HOME`, `ANDROID_SDK_ROOT` o en
`%LOCALAPPDATA%\Android\Sdk`. Si Android Studio instaló el SDK en otra ruta,
define `ANDROID_HOME` en PowerShell:

```powershell
$env:ANDROID_HOME = "C:\ruta\a\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

Gradle debe ser la versión indicada en
`android\gradle\wrapper\gradle-wrapper.properties` del proyecto. Si no está en
el caché del usuario, instala Gradle 9.3.1 y agrega su carpeta `bin` al `PATH`.
El `keytool` del JDK de Android Studio se usa para crear una clave de depuración
local.
