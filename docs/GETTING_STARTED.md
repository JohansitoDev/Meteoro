# Crear y ejecutar tu primera app

## 1. Crear el proyecto

Desde la carpeta donde quieres guardar el proyecto, ejecuta el CLI instalado o
el helper del repositorio:

```powershell
meteoro create
```

Escribe el nombre cuando se solicite, o indícalo en el comando:

```powershell
meteoro create MiPrimeraApp
cd MiPrimeraApp
```

El scaffold incluye:

- `app.mtr`: una app de ejemplo con tres tarjetas, tres pantallas y menú de
  navegación inferior.
- `assets\meteoro-logo.png`: logo inicial que se usa en el splash y el icono
  del APK.
- `android\`: proyecto Android con el renderer nativo de Meteoro.

Si ejecutas desde el repositorio en vez de instalar el CLI, usa
`..\scripts\meteoro.cmd` en los siguientes ejemplos.

## 2. Probar en la web

```powershell
meteoro dev
```

Abre la URL `Navegador` que imprime el CLI. La página se actualiza mientras
editas `app.mtr`.

## 3. Ejecutar en Android con recarga en vivo

Crea un emulador en Android Studio > **Device Manager** una sola vez. Después,
desde el directorio del proyecto, ejecuta:

```powershell
meteoro run android
```

El comando inicia el emulador disponible si no hay un dispositivo conectado,
compila e instala el APK, abre la app y deja activo el servidor de desarrollo.
Al guardar `app.mtr`, el renderer Android actualiza la pantalla
automáticamente. Deja abierta la terminal mientras desarrollas y pulsa
`Ctrl+C` para detener el servidor. También puedes conectar un teléfono a la
misma Wi-Fi y habilitar la depuración USB.

## 4. Generar un APK

```powershell
meteoro apk
```

Este comando genera el APK instalable **de tu proyecto**, con su propio nombre,
icono, splash y paquete Android; no es la app de preview Meteoro Go. El APK de
depuración queda en
`android\app\build\outputs\apk\debug\app-debug.apk`. Este APK abre la app
empaquetada y puede funcionar sin el servidor de desarrollo.

Con el teléfono conectado por USB y la depuración autorizada, `meteoro run
android` compila, instala y abre la app con recarga en vivo. También puedes
instalar el APK manualmente:

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

El APK toma su nombre del directorio del proyecto. Para cambiar el splash y el
icono, reemplaza `assets\meteoro-logo.png` antes de volver a compilar. No
modifiques el archivo adjunto original; usa el recurso del proyecto.

## 5. Editar la app

Edita `app.mtr` y guarda el archivo. Para compilar solo el esquema JSON, usa:

```powershell
meteoro build
```

Consulta la [referencia de componentes](./COMPONENTS.md) para la sintaxis.
