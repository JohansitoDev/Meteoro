use std::error::Error;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::thread;
use std::time::{Duration, Instant};

pub fn copy_template(project_dir: &Path) -> Result<(), Box<dyn Error>> {
    let source = Path::new(env!("CARGO_MANIFEST_DIR")).join("meteoro-go");
    if !source.is_dir() {
        return Err(format!(
            "No se encontró la plantilla Android empaquetada en '{}'.",
            source.display()
        )
        .into());
    }
    let destination = project_dir.join("android");
    if destination.exists() {
        return Err(format!(
            "El directorio Android '{}' ya existe; no se sobrescribió.",
            destination.display()
        )
        .into());
    }
    copy_directory(&source, &destination)?;
    Ok(())
}

pub fn build_apk(
    project_dir: &Path,
    source_path: &Path,
    dev_server_url: Option<&str>,
) -> Result<PathBuf, Box<dyn Error>> {
    let android_dir = project_dir.join("android");
    if !android_dir.exists() {
        copy_template(project_dir)?;
    }

    let app_dir = android_dir.join("app");
    let assets_dir = app_dir.join("src/main/assets");
    let drawable_dir = app_dir.join("src/main/res/drawable");
    let logo_path = project_dir.join("assets/meteoro-logo.png");
    if !logo_path.is_file() {
        return Err(format!(
            "No se encontró el logo del proyecto en '{}'.",
            logo_path.display()
        )
        .into());
    }

    let schema = crate::build_meteoro_file(source_path)?;
    fs::create_dir_all(&assets_dir)?;
    fs::write(assets_dir.join("meteoro-app.json"), schema)?;
    copy_project_logo(&logo_path, &assets_dir, &drawable_dir)?;
    copy_project_assets(&project_dir.join("assets"), &assets_dir.join("assets"))?;
    fs::write(
        drawable_dir.join("ic_launcher_background.xml"),
        r##"<?xml version="1.0" encoding="utf-8"?>
<color xmlns:android="http://schemas.android.com/apk/res/android" android:color="#F5F5F5" />
"##,
    )?;

    let sdk_dir = find_android_sdk()?;
    let gradle = find_gradle(&android_dir)?;
    ensure_debug_keystore(&android_dir)?;

    let project_name = project_dir
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("Meteoro App");
    let application_id = format!("dev.meteoro.{}", package_slug(project_name));
    let args = vec![
        "--no-daemon".to_string(),
        ":app:assembleDebug".to_string(),
        "--project-prop".to_string(),
        "meteoroStandalone=true".to_string(),
        "--project-prop".to_string(),
        format!("meteoroProjectName={project_name}"),
        "--project-prop".to_string(),
        format!("meteoroApplicationId={application_id}"),
        "--project-prop".to_string(),
        format!("meteoroDevServerUrl={}", dev_server_url.unwrap_or_default()),
    ];
    println!("Compilando APK nativo para {project_name}...");
    run_gradle(&gradle, &android_dir, &sdk_dir, &args)?;
    let apk_path = app_dir.join("build/outputs/apk/debug/app-debug.apk");
    if !apk_path.is_file() {
        return Err(format!("Gradle terminó, pero no creó '{}'.", apk_path.display()).into());
    }
    println!("APK listo: {}", apk_path.display());
    Ok(apk_path)
}

fn copy_project_logo(
    logo_path: &Path,
    assets_dir: &Path,
    drawable_dir: &Path,
) -> Result<(), Box<dyn Error>> {
    fs::create_dir_all(assets_dir)?;
    fs::create_dir_all(drawable_dir)?;
    fs::copy(logo_path, assets_dir.join("meteoro-logo.png"))?;
    fs::copy(logo_path, drawable_dir.join("ic_meteoro_logo.png"))?;
    fs::copy(logo_path, drawable_dir.join("ic_launcher_fg_logo.png"))?;
    Ok(())
}

fn copy_project_assets(source: &Path, destination: &Path) -> Result<(), Box<dyn Error>> {
    fs::create_dir_all(destination)?;
    for entry in fs::read_dir(source)? {
        let entry = entry?;
        let entry_type = entry.file_type()?;
        let source_path = entry.path();
        let destination_path = destination.join(entry.file_name());
        if entry_type.is_dir() {
            copy_project_assets(&source_path, &destination_path)?;
        } else if entry_type.is_file() {
            fs::copy(source_path, destination_path)?;
        }
    }
    Ok(())
}

pub fn run_on_android(
    project_dir: &Path,
    source_path: &Path,
    avd_name: Option<&str>,
) -> Result<(), Box<dyn Error>> {
    let sdk_dir = find_android_sdk()?;
    let adb = sdk_dir.join("platform-tools/adb.exe");
    let emulator = sdk_dir.join("emulator/emulator.exe");
    let device = match connected_devices(&adb)?.into_iter().next() {
        Some(device) => device,
        None => {
            let avd = avd_name
                .map(str::to_string)
                .or(find_first_avd(&emulator)?)
                .ok_or(
                    "No hay un dispositivo conectado ni un AVD creado. Crea un emulador Android en Android Studio > Device Manager.",
                )?;
            println!("Iniciando el emulador {avd}...");
            let _emulator_process = Command::new(&emulator).arg("-avd").arg(&avd).spawn()?;
            wait_for_android_device(&adb)?
        }
    };

    let is_emulator = device.starts_with("emulator-");
    let server_host = if is_emulator {
        "10.0.2.2".to_string()
    } else {
        crate::dev_server::discover_local_ip()?
    };
    let dev_url = format!("http://{server_host}:4173/app/code");
    let _server = crate::dev_server::spawn(source_path, 4173)?;
    let apk_path = build_apk(project_dir, source_path, Some(&dev_url))?;

    run_adb(&adb, &device, &["install", "-r"], Some(&apk_path))?;
    let project_name = project_dir
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("Meteoro App");
    let application_id = format!("dev.meteoro.{}", package_slug(project_name));
    let activity_name = format!("{application_id}/com.example.MainActivity");
    run_adb(
        &adb,
        &device,
        &["shell", "am", "start", "-n", &activity_name],
        None,
    )?;
    println!(
        "Meteoro está activo en {device}. Edita '{}' para ver los cambios en vivo; Ctrl+C detiene el servidor.",
        source_path.display()
    );
    loop {
        thread::sleep(Duration::from_secs(3600));
    }
}

fn copy_directory(source: &Path, destination: &Path) -> Result<(), Box<dyn Error>> {
    fs::create_dir_all(destination)?;
    for entry in fs::read_dir(source)? {
        let entry = entry?;
        let name = entry.file_name();
        let name = name.to_string_lossy();
        if should_skip(&name) {
            continue;
        }
        let source_path = entry.path();
        let destination_path = destination.join(name.as_ref());
        if source_path.is_dir() {
            copy_directory(&source_path, &destination_path)?;
        } else if source_path.is_file() {
            if let Some(parent) = destination_path.parent() {
                fs::create_dir_all(parent)?;
            }
            fs::copy(source_path, destination_path)?;
        }
    }
    Ok(())
}

fn should_skip(name: &str) -> bool {
    matches!(
        name,
        ".gradle"
            | ".git"
            | ".idea"
            | ".kotlin"
            | "build"
            | "debug.keystore"
            | "google-services.json"
            | "local.properties"
            | ".env"
            | ".DS_Store"
    )
}

fn package_slug(name: &str) -> String {
    let slug = name
        .chars()
        .map(|character| {
            if character.is_ascii_alphanumeric() {
                character.to_ascii_lowercase()
            } else {
                '_'
            }
        })
        .collect::<String>()
        .trim_matches('_')
        .to_string();
    if slug.is_empty() || slug.as_bytes()[0].is_ascii_digit() {
        format!("app_{slug}")
    } else {
        slug
    }
}

fn find_android_sdk() -> Result<PathBuf, Box<dyn Error>> {
    let candidates = [
        std::env::var_os("ANDROID_HOME").map(PathBuf::from),
        std::env::var_os("ANDROID_SDK_ROOT").map(PathBuf::from),
        std::env::var_os("LOCALAPPDATA").map(|path| PathBuf::from(path).join("Android/Sdk")),
    ];
    for candidate in candidates.into_iter().flatten() {
        if candidate.join("platform-tools/adb.exe").is_file() {
            return Ok(candidate);
        }
    }
    Err("No se encontró Android SDK. Define ANDROID_HOME o instala Android Studio con Platform Tools.".into())
}

fn find_gradle(android_dir: &Path) -> Result<PathBuf, Box<dyn Error>> {
    if let Some(distribution) = gradle_distribution_name(android_dir) {
        if let Some(profile) = std::env::var_os("USERPROFILE") {
            let cached_distribution = PathBuf::from(profile)
                .join(".gradle/wrapper/dists")
                .join(distribution);
            let mut pending = vec![(cached_distribution, 0)];
            while let Some((directory, depth)) = pending.pop() {
                if depth > 5 {
                    continue;
                }
                let entries = match fs::read_dir(directory) {
                    Ok(entries) => entries,
                    Err(_) => continue,
                };
                for entry in entries.flatten() {
                    let path = entry.path();
                    if path.file_name().is_some_and(|name| name == "gradle.bat") {
                        return Ok(path);
                    }
                    if path.is_dir() {
                        pending.push((path, depth + 1));
                    }
                }
            }
        }
    }

    for path in std::env::split_paths(&std::env::var_os("PATH").unwrap_or_default()) {
        for name in ["gradle.bat", "gradle.cmd", "gradle.exe"] {
            let candidate = path.join(name);
            if candidate.is_file() {
                return Ok(candidate);
            }
        }
    }

    if let Some(gradle_home) = std::env::var_os("GRADLE_HOME") {
        let candidate = PathBuf::from(gradle_home).join("bin/gradle.bat");
        if candidate.is_file() {
            return Ok(candidate);
        }
    }

    let required_version = gradle_distribution_name(android_dir)
        .unwrap_or_else(|| "Gradle de la versión configurada".to_string());
    Err(format!(
        "No se encontró {required_version}. Instala esa versión o agrega gradle.bat al PATH."
    )
    .into())
}

fn gradle_distribution_name(android_dir: &Path) -> Option<String> {
    let properties =
        fs::read_to_string(android_dir.join("gradle/wrapper/gradle-wrapper.properties")).ok()?;
    let distribution_url = properties
        .lines()
        .find_map(|line| line.strip_prefix("distributionUrl="))?
        .replace("\\:", ":");
    let filename = distribution_url.rsplit('/').next()?;
    Some(filename.strip_suffix(".zip")?.to_string())
}

fn find_first_avd(emulator: &Path) -> Result<Option<String>, Box<dyn Error>> {
    if !emulator.is_file() {
        return Err("No se encontró Android Emulator en el SDK.".into());
    }
    let output = Command::new(emulator).arg("-list-avds").output()?;
    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr)
            .trim()
            .to_string()
            .into());
    }
    Ok(String::from_utf8_lossy(&output.stdout)
        .lines()
        .map(str::trim)
        .find(|line| !line.is_empty())
        .map(str::to_string))
}

fn connected_devices(adb: &Path) -> Result<Vec<String>, Box<dyn Error>> {
    let output = Command::new(adb).args(["devices"]).output()?;
    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr)
            .trim()
            .to_string()
            .into());
    }
    let output = String::from_utf8_lossy(&output.stdout);
    let mut devices = Vec::new();
    for line in output.lines() {
        let mut fields = line.split_whitespace();
        let Some(serial) = fields.next() else {
            continue;
        };
        let Some(state) = fields.next() else {
            continue;
        };
        if state == "unauthorized" {
            return Err(format!(
                "El dispositivo {serial} no está autorizado por ADB. Acepta el aviso de depuración USB en Android y vuelve a ejecutar el comando."
            )
            .into());
        }
        if state == "offline" {
            return Err(format!(
                "El dispositivo {serial} está fuera de línea. Espera a que termine de iniciar y vuelve a ejecutar el comando."
            )
            .into());
        }
        if state == "device" {
            devices.push(serial.to_string());
        }
    }
    devices.sort_by_key(|serial| !serial.starts_with("emulator-"));
    Ok(devices)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn copies_project_logo_to_splash_and_launcher_resources() {
        let project_dir = std::env::temp_dir().join(format!(
            "meteoro-android-logo-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        let logo_path = project_dir.join("source-logo.png");
        let assets_dir = project_dir.join("app/src/main/assets");
        let drawable_dir = project_dir.join("app/src/main/res/drawable");
        let logo_bytes = include_bytes!("../assets/meteoro-logo.png");

        fs::create_dir_all(&project_dir).unwrap();
        fs::write(&logo_path, logo_bytes).unwrap();
        copy_project_logo(&logo_path, &assets_dir, &drawable_dir).unwrap();

        assert_eq!(
            fs::read(assets_dir.join("meteoro-logo.png")).unwrap(),
            logo_bytes
        );
        assert_eq!(
            fs::read(drawable_dir.join("ic_meteoro_logo.png")).unwrap(),
            logo_bytes
        );
        assert_eq!(
            fs::read(drawable_dir.join("ic_launcher_fg_logo.png")).unwrap(),
            logo_bytes
        );
        fs::remove_dir_all(project_dir).unwrap();
    }

    #[test]
    fn copies_nested_project_assets_into_the_standalone_apk_assets() {
        let project_dir = std::env::temp_dir().join(format!(
            "meteoro-android-assets-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        let source_dir = project_dir.join("assets");
        let destination_dir = project_dir.join("android/app/src/main/assets/assets");
        fs::create_dir_all(source_dir.join("images")).unwrap();
        fs::write(source_dir.join("images/card.png"), b"image bytes").unwrap();

        copy_project_assets(&source_dir, &destination_dir).unwrap();

        assert_eq!(
            fs::read(destination_dir.join("images/card.png")).unwrap(),
            b"image bytes"
        );
        fs::remove_dir_all(project_dir).unwrap();
    }
}

fn wait_for_android_device(adb: &Path) -> Result<String, Box<dyn Error>> {
    let deadline = Instant::now() + Duration::from_secs(180);
    loop {
        if let Some(device) = connected_devices(adb)?.into_iter().next() {
            let output = Command::new(adb)
                .args(["-s", &device, "shell", "getprop", "sys.boot_completed"])
                .output()?;
            if output.status.success() && String::from_utf8_lossy(&output.stdout).trim() == "1" {
                return Ok(device);
            }
        }
        if Instant::now() >= deadline {
            return Err("Android no terminó de iniciar en 180 segundos.".into());
        }
        thread::sleep(Duration::from_secs(2));
    }
}

fn ensure_debug_keystore(android_dir: &Path) -> Result<(), Box<dyn Error>> {
    let keystore = android_dir.join("debug.keystore");
    if keystore.is_file() {
        return Ok(());
    }
    let keytool = find_keytool()?;
    let output = Command::new(keytool)
        .args([
            "-genkeypair",
            "-v",
            "-keystore",
            keystore.to_string_lossy().as_ref(),
            "-storepass",
            "android",
            "-alias",
            "androiddebugkey",
            "-keypass",
            "android",
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "10000",
            "-dname",
            "CN=Android Debug,O=Android,C=US",
            "-noprompt",
        ])
        .output()?;
    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr)
            .trim()
            .to_string()
            .into());
    }
    Ok(())
}

fn find_keytool() -> Result<PathBuf, Box<dyn Error>> {
    if let Some(java_home) = std::env::var_os("JAVA_HOME") {
        for name in ["keytool.exe", "keytool"] {
            let candidate = PathBuf::from(&java_home).join("bin").join(name);
            if candidate.is_file() {
                return Ok(candidate);
            }
        }
    }
    for path in std::env::split_paths(&std::env::var_os("PATH").unwrap_or_default()) {
        for name in ["keytool.exe", "keytool"] {
            let candidate = path.join(name);
            if candidate.is_file() {
                return Ok(candidate);
            }
        }
    }
    Err("No se encontró keytool. Instala un JDK o configura JAVA_HOME.".into())
}

fn run_gradle(
    gradle: &Path,
    project_dir: &Path,
    sdk_dir: &Path,
    args: &[String],
) -> Result<(), Box<dyn Error>> {
    let mut command = if gradle.extension().is_some_and(|extension| {
        extension.eq_ignore_ascii_case("bat") || extension.eq_ignore_ascii_case("cmd")
    }) {
        let mut command = Command::new("cmd.exe");
        command.arg("/D").arg("/C").arg(gradle);
        command
    } else {
        Command::new(gradle)
    };
    let status = command
        .args(args)
        .current_dir(project_dir)
        .env("ANDROID_HOME", sdk_dir)
        .env("ANDROID_SDK_ROOT", sdk_dir)
        .status()?;
    if !status.success() {
        return Err(format!("Gradle terminó con el código {status}.").into());
    }
    Ok(())
}

fn run_adb(
    adb: &Path,
    device: &str,
    args: &[&str],
    apk_path: Option<&Path>,
) -> Result<(), Box<dyn Error>> {
    let mut command = Command::new(adb);
    command.args(["-s", device]).args(args);
    if let Some(apk_path) = apk_path {
        command.arg(apk_path);
    }
    let status = command.status()?;
    if !status.success() {
        return Err(format!("ADB terminó con el código {status}.").into());
    }
    Ok(())
}
