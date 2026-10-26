pub mod android;
pub mod compiler;
pub mod dev_server;
pub mod parser;

use std::error::Error;
use std::fs::{self, OpenOptions};
use std::io::{self, Write};
use std::path::Path;

use compiler::compile;
use parser::parse;

const DEFAULT_PROJECT: &str = include_str!("../app.mtr");
const DEFAULT_LOGO: &[u8] = include_bytes!("../assets/meteoro-logo.png");

pub fn build_meteoro_code(input: &str) -> Result<String, Box<dyn Error>> {
    let ast = parse(input)?;
    Ok(compile(ast)?)
}

pub fn build_meteoro_file(path: &Path) -> Result<String, Box<dyn Error>> {
    let source = fs::read_to_string(path)?;
    build_meteoro_code(&source)
}

pub fn run_cli(args: impl IntoIterator<Item = String>) -> Result<(), Box<dyn Error>> {
    let mut args = args.into_iter();
    let command = args.next().unwrap_or_else(|| "dev".to_string());

    match command.as_str() {
        "dev" => {
            let source_path = args.next().unwrap_or_else(|| "app.mtr".to_string());
            let port = args
                .next()
                .map(|value| value.parse::<u16>())
                .transpose()?
                .unwrap_or(4173);
            if args.next().is_some() {
                return Err("Uso: meteoro dev [archivo.mtr] [puerto]".into());
            }
            dev_server::run(Path::new(&source_path), port)
        }
        "build" => {
            let target_or_source = args.next();
            if target_or_source.as_deref() == Some("apk") {
                let source_path = args.next().unwrap_or_else(|| "app.mtr".to_string());
                if args.next().is_some() {
                    return Err("Uso: meteoro build apk [archivo.mtr]".into());
                }
                let project_dir = std::env::current_dir()?;
                android::build_apk(&project_dir, Path::new(&source_path), None)?;
            } else {
                let source_path = target_or_source.unwrap_or_else(|| "app.mtr".to_string());
                if args.next().is_some() {
                    return Err(
                        "Uso: meteoro build [archivo.mtr] | meteoro build apk [archivo.mtr]".into(),
                    );
                }
                println!("{}", build_meteoro_file(Path::new(&source_path))?);
            }
            Ok(())
        }
        "run" => {
            let platform = args.next().unwrap_or_default();
            if platform != "android" {
                return Err("Uso: meteoro run android [avd] [archivo.mtr]".into());
            }
            let avd_name = args.next();
            let source_path = args.next().unwrap_or_else(|| "app.mtr".to_string());
            if args.next().is_some() {
                return Err("Uso: meteoro run android [avd] [archivo.mtr]".into());
            }
            let project_dir = std::env::current_dir()?;
            android::run_on_android(&project_dir, Path::new(&source_path), avd_name.as_deref())
        }
        "create" | "new" => {
            let project_path = match args.next() {
                Some(path) => path,
                None => prompt_project_name()?,
            };
            if args.next().is_some() {
                return Err("Uso: meteoro create [nombre-del-proyecto]".into());
            }
            create_project(Path::new(&project_path))
        }
        "apk" => {
            let source_path = args.next().unwrap_or_else(|| "app.mtr".to_string());
            if args.next().is_some() {
                return Err("Uso: meteoro apk [archivo.mtr]".into());
            }
            let project_dir = std::env::current_dir()?;
            android::build_apk(&project_dir, Path::new(&source_path), None)?;
            Ok(())
        }
        "help" | "--help" | "-h" => {
            println!("Meteoro v0.0.1\n\nComandos:\n  meteoro create [nombre-del-proyecto]\n  meteoro dev [archivo.mtr] [puerto]\n  meteoro run android [avd] [archivo.mtr]\n  meteoro apk [archivo.mtr]\n  meteoro build [archivo.mtr]");
            Ok(())
        }
        _ => Err("Comando desconocido. Usa 'meteoro --help' para ver el uso.".into()),
    }
}

fn prompt_project_name() -> Result<String, Box<dyn Error>> {
    print!("Nombre de tu proyecto Meteoro: ");
    io::stdout().flush()?;
    let mut name = String::new();
    io::stdin().read_line(&mut name)?;
    let name = name.trim();
    if name.is_empty() {
        return Err("El nombre del proyecto no puede estar vacío.".into());
    }
    Ok(name.to_string())
}

fn create_project(project_path: &Path) -> Result<(), Box<dyn Error>> {
    let app_path = project_path.join("app.mtr");
    let logo_path = project_path.join("assets").join("meteoro-logo.png");
    if app_path.exists() || logo_path.exists() || project_path.join("android").exists() {
        return Err(format!(
            "El proyecto ya contiene archivos Meteoro en '{}'; no se sobrescribieron.",
            project_path.display()
        )
        .into());
    }

    fs::create_dir_all(project_path.join("assets"))?;
    let mut app_file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&app_path)?;
    app_file.write_all(DEFAULT_PROJECT.as_bytes())?;
    let mut logo_file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&logo_path)?;
    logo_file.write_all(DEFAULT_LOGO)?;
    android::copy_template(project_path)?;

    println!("Proyecto Meteoro creado en {}", project_path.display());
    println!(
        "Siguiente paso: cd \"{}\" y ejecuta 'meteoro dev'.",
        project_path.display()
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hello_meteoro() {
        let code = "text \"Hola Meteoro\"\nbutton \"Prende\"";
        let result = build_meteoro_code(code).unwrap();
        let schema: serde_json::Value = serde_json::from_str(&result).unwrap();
        assert_eq!(schema["appName"], "Meteoro App");
        assert_eq!(schema["version"], "0.0.1");
        assert_eq!(schema["screens"][0]["type"], "text");
        assert_eq!(schema["screens"][0]["value"], "Hola Meteoro");
        assert_eq!(schema["screens"][1]["type"], "button");
        assert_eq!(schema["screens"][1]["value"], "Prende");
    }

    #[test]
    fn compiles_routes_cards_inputs_and_navigation_for_both_previews() {
        let code = "screen \"home\" \"Inicio\"\ncard \"Perfil\"\ninput \"name\" \"Tu nombre\"\nend\nbutton \"Abrir\" navigate \"profile\"\nscreen \"profile\" \"Perfil\"\ntext \"Hola\"";
        let result = build_meteoro_code(code).unwrap();
        let schema: serde_json::Value = serde_json::from_str(&result).unwrap();

        assert_eq!(schema["initialRoute"], "home");
        assert_eq!(schema["routes"][0]["title"], "Inicio");
        assert_eq!(schema["routes"][0]["components"][0]["type"], "card");
        assert_eq!(
            schema["routes"][0]["components"][0]["props"]["children"][0]["id"],
            "name"
        );
        assert_eq!(
            schema["routes"][0]["components"][1]["props"]["action"],
            "navigate:profile"
        );
        assert_eq!(schema["routes"][1]["components"][0]["value"], "Hola");
    }

    #[test]
    fn default_project_has_three_cards_and_a_bottom_navigation_bar() {
        let schema: serde_json::Value =
            serde_json::from_str(&build_meteoro_code(DEFAULT_PROJECT).unwrap()).unwrap();
        let cards = schema["routes"][0]["components"]
            .as_array()
            .unwrap()
            .iter()
            .filter(|component| component["type"] == "card")
            .count();

        assert_eq!(cards, 3);
        assert_eq!(schema["navigationBar"].as_array().unwrap().len(), 3);
        assert_eq!(schema["navigationBar"][0]["route"], "inicio");
    }

    #[test]
    fn create_command_writes_the_starter_and_logo_without_overwriting() {
        let project_path = std::env::temp_dir().join(format!(
            "meteoro-create-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        create_project(&project_path).unwrap();

        assert_eq!(
            fs::read_to_string(project_path.join("app.mtr")).unwrap(),
            DEFAULT_PROJECT
        );
        assert_eq!(
            fs::read(project_path.join("assets/meteoro-logo.png")).unwrap(),
            DEFAULT_LOGO
        );
        assert!(project_path.join("android/app/build.gradle.kts").is_file());
        assert!(create_project(&project_path).is_err());
        fs::remove_dir_all(project_path).unwrap();
    }
}
