use std::process::{Command, Stdio};
use std::{env, path::PathBuf};

const JAVA_EXE_PATH_REL: &[&str] = &["runtime", "bin", "java"];
const JAR_PATH_REL: &[&str] = &["lib", "generic", "kamite.jar"];

fn main() {
    let program_dir = {
        let mut p = env::current_exe().expect("Could not determine current executable's path");
        p.pop();
        p.pop();
        p
    };

    let java_exe_path = make_java_exe_path(program_dir.clone());
    let jar_path = make_jar_path(program_dir);

    let mut cmd = Command::new(java_exe_path);
    cmd.arg("--enable-preview")
        .args(&[
            "--add-opens",
            "java.desktop/sun.awt.X11=ALL-UNNAMED",
            "--add-opens",
            "java.desktop/sun.swing=ALL-UNNAMED", // DarculaLaf
        ])
        .arg("-jar")
        .arg(jar_path)
        .args(env::args().skip(1))
        .stdin(Stdio::inherit())
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());

    let mut child = cmd.spawn().expect("Failed to launch Kamite");
    child.wait().expect("Process was not running");
}

fn make_java_exe_path(mut program_dir: PathBuf) -> PathBuf {
    JAVA_EXE_PATH_REL
        .iter()
        .for_each(|seg| program_dir.push(seg));
    program_dir
}

fn make_jar_path(mut program_dir: PathBuf) -> PathBuf {
    JAR_PATH_REL.iter().for_each(|seg| program_dir.push(seg));
    program_dir
}
