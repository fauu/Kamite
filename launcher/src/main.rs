use std::{
    env,
    process::{Command, Stdio},
};

#[cfg(windows)]
use std::os::windows::process::CommandExt;

#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x08000000;

#[cfg(unix)]
const JAVA_EXE_NAME: &str = "java";

#[cfg(windows)]
const JAVA_EXE_NAME: &str = "java.exe";

fn main() {
    let args: Vec<String> = env::args().skip(1).collect();

    let mut current_exe_dir =
        env::current_exe().expect("Could not determine current executable's path");
    current_exe_dir.pop();

    if cfg!(unix) {
        current_exe_dir.pop();
    }

    let mut java_path = current_exe_dir.clone();
    java_path.push("runtime");
    java_path.push("bin");
    java_path.push(JAVA_EXE_NAME);
    let java_path_str = java_path
        .into_os_string()
        .into_string()
        .expect("Could not convert java path to string");

    let mut jar_path = current_exe_dir.clone();
    jar_path.push("lib");
    jar_path.push("generic");
    jar_path.push("kamite.jar");

    let mut cmd = Command::new(if cfg!(windows) {
        "cmd".to_owned()
    } else {
        java_path_str.clone()
    });
    if cfg!(windows) {
        cmd.arg("/C");
        cmd.arg(java_path_str);
    }
    cmd.arg("--enable-preview");
    if cfg!(target_os = "linux") {
        cmd.args(&["--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED"]);
    }
    cmd.arg("-jar")
        .arg(jar_path)
        .args(&args)
        .stdin(Stdio::inherit())
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());

    cmd_build_platform_specific(&mut cmd, &args);

    let mut child = cmd.spawn().expect("Failed to launch Kamite");
    child.wait().expect("Process was not running");
}

#[cfg(windows)]
fn cmd_build_platform_specific(cmd: &mut Command, args: &[String]) {
    if args.iter().all(|arg| !arg.starts_with("--debug")) {
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
}

#[cfg(unix)]
fn cmd_build_platform_specific(_cmd: &mut Command, _args: &[String]) {}
