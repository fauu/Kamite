use std::{
    env,
    process::{Command, Stdio},
};

fn main() {
    let mut current_exe_dir =
        env::current_exe().expect("Could not determine current executable's path");
    current_exe_dir.pop();

    if cfg!(unix) {
        current_exe_dir.pop();
    }

    let mut java_path = current_exe_dir.clone();
    java_path.push("runtime");
    java_path.push("bin");
    java_path.push(get_java_exe_name());

    let mut jar_path = current_exe_dir.clone();
    jar_path.push("lib");
    jar_path.push("generic");
    jar_path.push("kamite.jar");

    let mut cmd = Command::new(java_path);
    cmd.arg("--enable-preview");
    if cfg!(target_os = "linux") {
        cmd.arg("--add-opens")
            .arg("java.desktop/sun.awt.X11=ALL-UNNAMED");
    }
    cmd.arg("-jar")
        .arg(jar_path)
        .args(env::args())
        .stdin(Stdio::inherit())
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());

    let mut child = cmd.spawn().expect("Failed to launch Kamite");
    child.wait().expect("Process was not running");
}

#[cfg(unix)]
fn get_java_exe_name() -> &'static str {
    "java"
}

#[cfg(windows)]
fn get_java_exe_name() -> &'static str {
    "java.exe"
}
