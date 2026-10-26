use std::error::Error;
use std::fs;
use std::io::{BufRead, BufReader, Write};
use std::net::{TcpListener, TcpStream, UdpSocket};
use std::path::Path;
use std::thread::{self, JoinHandle};
use std::time::Duration;

use qrcode::render::unicode::Dense1x2;
use qrcode::QrCode;

const SCHEMA_PATH: &str = "/app/code";
const WEB_PREVIEW: &str = include_str!("../web/preview.html");
const METEORO_LOGO: &[u8] = include_bytes!("../assets/meteoro-logo.png");

pub fn run(source_path: &Path, port: u16) -> Result<(), Box<dyn Error>> {
    let server = spawn(source_path, port)?;
    server
        .join()
        .map_err(|_| "El hilo del servidor de desarrollo terminó inesperadamente".into())
}

pub fn spawn(source_path: &Path, port: u16) -> Result<JoinHandle<()>, Box<dyn Error>> {
    let source_path = fs::canonicalize(source_path)?;
    compile_source(&source_path)?;

    let listener = TcpListener::bind(("0.0.0.0", port))?;
    let local_ip = discover_local_ip()?;
    let preview_url = format!("http://{local_ip}:{port}{SCHEMA_PATH}");
    let browser_url = format!("http://{local_ip}:{port}/");
    let app_qr = QrCode::new(preview_url.as_bytes())?
        .render::<Dense1x2>()
        .build();
    let browser_qr = QrCode::new(browser_url.as_bytes())?
        .render::<Dense1x2>()
        .build();

    println!("Meteoro v0.0.1 — servidor de desarrollo activo");
    println!("QR para escanear desde Meteoro Go:\n{app_qr}");
    println!("Meteoro Go: {preview_url}");
    println!("\nQR para abrir en el navegador:\n{browser_qr}");
    println!("Navegador:   {browser_url}");
    println!("Conecta el telefono y el ordenador a la misma red Wi-Fi.");
    println!("Archivo: {}", source_path.display());
    println!("La app comprueba cambios automáticamente. Pulsa Ctrl+C para detener.");

    Ok(thread::Builder::new()
        .name("meteoro-dev-server".to_string())
        .spawn(move || {
            for incoming in listener.incoming() {
                match incoming {
                    Ok(mut stream) => {
                        if let Err(error) = handle_connection(&mut stream, &source_path) {
                            eprintln!("Error atendiendo una petición: {error}");
                        }
                    }
                    Err(error) => {
                        eprintln!("Error aceptando una conexión: {error}");
                        break;
                    }
                }
            }
        })?)
}

pub fn discover_local_ip() -> Result<String, Box<dyn Error>> {
    let socket = UdpSocket::bind("0.0.0.0:0")?;
    socket.connect("8.8.8.8:80")?;
    Ok(socket.local_addr()?.ip().to_string())
}

fn compile_source(source_path: &Path) -> Result<String, Box<dyn Error>> {
    let source = fs::read_to_string(source_path)?;
    Ok(crate::build_meteoro_code(&source)?)
}

fn handle_connection(stream: &mut TcpStream, source_path: &Path) -> std::io::Result<()> {
    stream.set_read_timeout(Some(Duration::from_secs(5)))?;
    let mut reader = BufReader::new(stream);
    let mut request_line = String::new();
    reader.read_line(&mut request_line)?;
    let stream = reader.get_mut();

    let mut parts = request_line.split_whitespace();
    let method = parts.next().unwrap_or_default();
    let path = parts
        .next()
        .unwrap_or_default()
        .split('?')
        .next()
        .unwrap_or_default();

    let (status, content_type, body) = match (method, path) {
        ("GET", "/" | "/index.html") => (
            "200 OK",
            "text/html; charset=utf-8",
            WEB_PREVIEW.as_bytes().to_vec(),
        ),
        ("GET", "/meteoro-logo.png") => ("200 OK", "image/png", METEORO_LOGO.to_vec()),
        ("GET", asset_path) if asset_path.starts_with("/assets/") => {
            match read_project_asset(source_path, asset_path) {
                Ok(Some((content_type, body))) => ("200 OK", content_type, body),
                Ok(None) => (
                    "404 Not Found",
                    "text/plain; charset=utf-8",
                    "Recurso no encontrado".as_bytes().to_vec(),
                ),
                Err(error) => {
                    eprintln!("No se pudo leer el recurso {asset_path}: {error}");
                    (
                        "500 Internal Server Error",
                        "text/plain; charset=utf-8",
                        "No se pudo leer el recurso".as_bytes().to_vec(),
                    )
                }
            }
        }
        ("GET", SCHEMA_PATH) => match compile_source(source_path) {
            Ok(schema) => (
                "200 OK",
                "application/json; charset=utf-8",
                schema.into_bytes(),
            ),
            Err(error) => {
                eprintln!("No se pudo compilar {}: {error}", source_path.display());
                (
                    "500 Internal Server Error",
                    "text/plain; charset=utf-8",
                    format!("Error al compilar {}: {error}", source_path.display()).into_bytes(),
                )
            }
        },
        ("GET", "/health") => ("200 OK", "text/plain; charset=utf-8", b"ok".to_vec()),
        ("GET", _) => (
            "404 Not Found",
            "text/plain; charset=utf-8",
            "Ruta no encontrada".as_bytes().to_vec(),
        ),
        _ => (
            "405 Method Not Allowed",
            "text/plain; charset=utf-8",
            "Método no permitido".as_bytes().to_vec(),
        ),
    };

    write!(
        stream,
        "HTTP/1.1 {status}\r\nContent-Type: {content_type}\r\nContent-Length: {}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n",
        body.len()
    )?;
    stream.write_all(&body)
}

fn read_project_asset(
    source_path: &Path,
    request_path: &str,
) -> std::io::Result<Option<(&'static str, Vec<u8>)>> {
    let Some(relative_path) = request_path.strip_prefix("/assets/") else {
        return Ok(None);
    };
    let relative_path = Path::new(relative_path);
    if relative_path.as_os_str().is_empty()
        || relative_path
            .components()
            .any(|component| !matches!(component, std::path::Component::Normal(_)))
    {
        return Ok(None);
    }

    let assets_dir = source_path
        .parent()
        .unwrap_or_else(|| Path::new("."))
        .join("assets");
    let Ok(assets_dir) = fs::canonicalize(assets_dir) else {
        return Ok(None);
    };
    let Ok(asset_path) = fs::canonicalize(assets_dir.join(relative_path)) else {
        return Ok(None);
    };
    if !asset_path.starts_with(&assets_dir) || !asset_path.is_file() {
        return Ok(None);
    }
    let content_type = match asset_path
        .extension()
        .and_then(|extension| extension.to_str())
        .map(str::to_ascii_lowercase)
        .as_deref()
    {
        Some("png") => "image/png",
        Some("jpg" | "jpeg") => "image/jpeg",
        Some("gif") => "image/gif",
        Some("webp") => "image/webp",
        Some("svg") => "image/svg+xml",
        Some("avif") => "image/avif",
        _ => "application/octet-stream",
    };
    Ok(Some((content_type, fs::read(asset_path)?)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Read;

    #[test]
    fn serves_native_app_schema_from_preview_route() {
        let source_path =
            std::env::temp_dir().join(format!("meteoro-preview-{}.mtr", std::process::id()));
        fs::write(
            &source_path,
            "text \"Preview nativa\"\nbutton \"Continuar\"",
        )
        .unwrap();

        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let address = listener.local_addr().unwrap();
        let server_source_path = source_path.clone();
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            handle_connection(&mut stream, &server_source_path).unwrap();
        });

        let mut client = TcpStream::connect(address).unwrap();
        write!(
            client,
            "GET {SCHEMA_PATH} HTTP/1.1\r\nHost: localhost\r\n\r\n"
        )
        .unwrap();
        let mut response = String::new();
        client.read_to_string(&mut response).unwrap();
        server.join().unwrap();
        fs::remove_file(source_path).unwrap();

        assert!(response.starts_with("HTTP/1.1 200 OK"));
        assert!(response.contains("\"appName\": \"Meteoro App\""));
        assert!(response.contains("\"type\": \"text\""));
        assert!(response.contains("\"value\": \"Preview nativa\""));
    }

    #[test]
    fn serves_browser_preview_page() {
        let source_path =
            std::env::temp_dir().join(format!("meteoro-browser-{}.mtr", std::process::id()));
        fs::write(&source_path, "text \"Vista web\"").unwrap();

        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let address = listener.local_addr().unwrap();
        let server_source_path = source_path.clone();
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            handle_connection(&mut stream, &server_source_path).unwrap();
        });

        let mut client = TcpStream::connect(address).unwrap();
        write!(client, "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n").unwrap();
        let mut response = String::new();
        client.read_to_string(&mut response).unwrap();
        server.join().unwrap();
        fs::remove_file(source_path).unwrap();

        assert!(response.starts_with("HTTP/1.1 200 OK"));
        assert!(response.contains("text/html; charset=utf-8"));
        assert!(response.contains("Meteoro Web Preview"));
    }

    #[test]
    fn serves_the_default_project_logo() {
        let source_path =
            std::env::temp_dir().join(format!("meteoro-logo-{}.mtr", std::process::id()));
        fs::write(&source_path, "text \"Logo\"").unwrap();

        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let address = listener.local_addr().unwrap();
        let server_source_path = source_path.clone();
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            handle_connection(&mut stream, &server_source_path).unwrap();
        });

        let mut client = TcpStream::connect(address).unwrap();
        write!(
            client,
            "GET /meteoro-logo.png HTTP/1.1\r\nHost: localhost\r\n\r\n"
        )
        .unwrap();
        let mut response = Vec::new();
        client.read_to_end(&mut response).unwrap();
        server.join().unwrap();
        fs::remove_file(source_path).unwrap();

        assert!(response.starts_with(b"HTTP/1.1 200 OK\r\n"));
        assert!(response
            .windows(b"Content-Type: image/png".len())
            .any(|window| window == b"Content-Type: image/png"));
        assert!(response.ends_with(METEORO_LOGO));
    }

    #[test]
    fn serves_project_images_from_assets_and_rejects_parent_paths() {
        let project_dir =
            std::env::temp_dir().join(format!("meteoro-assets-{}", std::process::id()));
        let source_path = project_dir.join("app.mtr");
        fs::create_dir_all(project_dir.join("assets")).unwrap();
        fs::write(&source_path, "text \"Assets\"").unwrap();
        fs::write(project_dir.join("assets/photo.png"), b"project image").unwrap();

        assert!(read_project_asset(&source_path, "/assets/photo.png")
            .unwrap()
            .is_some());
        assert!(read_project_asset(&source_path, "/assets/../app.mtr")
            .unwrap()
            .is_none());

        fs::remove_dir_all(project_dir).unwrap();
    }
}
