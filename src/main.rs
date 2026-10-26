fn main() {
    if let Err(error) = meteoro::run_cli(std::env::args().skip(1)) {
        eprintln!("Error: {error}");
        std::process::exit(1);
    }
}
