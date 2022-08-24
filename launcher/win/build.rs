use std::env;
use std::path::Path;

fn main() {
    let dir = env::var("CARGO_MANIFEST_DIR").unwrap();
    let res_path = Path::new(&dir).join("../../target/launcher/res/resources.o");
    println!(
        "cargo:rustc-link-arg-bin=kamite-launcher-win-main={}",
        res_path.display()
    );
}
