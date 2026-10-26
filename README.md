# Meteoro v0.0.1

Meteoro v0.0.1 compiles a small `.mtr` description into the native component
schema consumed by Meteoro Go. The development server exposes the same app to
Meteoro Go and to a browser on the local network. Both previews refresh when
the source file changes.

Start with [installation](./docs/INSTALLATION.md), then follow the
[getting-started guide](./docs/GETTING_STARTED.md). Available syntax is in the
[component reference](./docs/COMPONENTS.md).

## Create a starter project

From the Meteoro directory, run:

```powershell
.\scripts\meteoro.cmd create
```

Escribe el nombre cuando se solicite, o pásalo como argumento:

```powershell
.\scripts\meteoro.cmd create MiPrimeraApp
cd MiPrimeraApp
..\scripts\meteoro.cmd dev app.mtr
```

The new project includes an `app.mtr` with three example cards, three screens,
a working bottom navigation bar, and the Meteoro logo at
`assets\meteoro-logo.png`. It also contains the native Android renderer. The
root `app.mtr` is the same starter app.

## Preview on Android

1. Install and open Meteoro Go on an Android phone connected to the same Wi-Fi
   network as the computer.
2. From this project directory, run:

   ```powershell
   .\scripts\meteoro.cmd dev
   ```

3. Scan the terminal QR code with the scanner inside Meteoro Go.
4. To preview in a browser, open the `Navegador` URL printed by the CLI.
5. Edit `app.mtr`; both previews refresh automatically.

You can choose another source file or port with
`.\scripts\meteoro.cmd dev path\to\app.mtr 4173`. If Windows Firewall prompts you, allow
the development server on the private network. The phone and computer must be
able to reach each other on that network. Use a trusted private network: the
preview server is unauthenticated and uses HTTP without encryption. Cleartext
traffic is enabled only in Meteoro Go's debug build for this local preview.

To print the compiled schema without starting the preview server, run
`.\scripts\meteoro.cmd build`.

To build an installable debug APK for **the app in this project** (with its
own Android package, name, icon, and splash), run `..\scripts\meteoro.cmd apk`
from the project directory. It is not the Meteoro Go preview app. The APK is
written to `android\app\build\outputs\apk\debug\app-debug.apk`. Replace
`assets\meteoro-logo.png` to customize the icon and splash before building.
To build, install, and open the app with live reload, use
`..\scripts\meteoro.cmd run android`. Signed release APKs for app-store
publication are not supported yet.

To build, install, and open the app in an Android emulator with live reload,
run `..\scripts\meteoro.cmd run android`. Android Studio, Android SDK Platform
Tools, an installed Gradle 9 distribution, and at least one AVD are required.
If no device is connected, the command starts the first AVD it finds. Keep the
terminal open while developing; press Ctrl+C to stop the preview server.

The Windows helper selects Meteoro's per-user GNU Rust toolchain and MinGW
linker, so this development preview does not require administrator access,
PowerShell script policy changes, or Visual Studio Build Tools.

## Syntax

Strings use JSON quoting and escaping. A screen starts with `screen "id"`
and an optional title. Buttons navigate with `navigate "screen-id"`.
Cards contain child components and close with `end`. Inputs take an ID and a
placeholder:

```text
screen "inicio" "Mi primera app"
text "Hola desde Meteoro"
card "Tu perfil"
input "nombre" "Escribe tu nombre"
end
button "Ver bienvenida" navigate "bienvenida"

screen "bienvenida" "Bienvenida"
text "Llegaste a otra pantalla"
button "Volver al inicio" navigate "inicio"

navigation "inicio" "Inicio"
navigation "bienvenida" "Bienvenida"
```

Available components include `text`, `button`, `input`, `image`, `card`,
`row`, `column`, horizontal and vertical `list`, and `scroll`. Apply visual
properties with `style "property" "value"`. Routes are validated at compile
time; navigation to an undeclared screen is an error. Declare bottom
navigation items with `navigation "screen-id" "Label"` (or `tab`) after their
screens. See [component syntax](./docs/COMPONENTS.md) for details.

The development server is unauthenticated and uses HTTP without encryption.
Use a trusted private network. Cleartext traffic is enabled only in the debug
build for local preview. `meteoro apk` produces a debug APK; a signed
release APK is not yet supported.
