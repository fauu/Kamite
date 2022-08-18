#![windows_subsystem = "windows"]

use std::{
    env,
    process::{Command, Stdio},
};

#[cfg(windows)]
use std::os::windows::process::CommandExt;

#[cfg(windows)]
use winapi::um::winbase;

#[cfg(unix)]
const JAVA_EXE_NAME: &str = "java";
#[cfg(windows)]
const JAVA_EXE_NAME: &str = "java.exe";

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
    java_path.push(JAVA_EXE_NAME);

    let mut jar_path = current_exe_dir.clone();
    jar_path.push("lib");
    jar_path.push("generic");
    jar_path.push("kamite.jar");

    let mut cmd = Command::new(java_path);
    cmd.arg("--enable-preview");
    if cfg!(target_os = "linux") {
        cmd.args(&["--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED"]);
    }
    cmd.arg("-jar").arg(jar_path).args(env::args().skip(1));

    if cfg!(unix) {
        cmd.stdin(Stdio::inherit())
            .stdout(Stdio::inherit())
            .stderr(Stdio::inherit());
    } else {
        cmd.stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null());
    };

    cmd_build_platform_specific(&mut cmd);

    let mut child = cmd.spawn().expect("Failed to launch Kamite");
    if cfg!(unix) {
        child.wait().expect("Process was not running");
    }
}

#[cfg(windows)]
fn cmd_build_platform_specific(cmd: &mut Command) {
    cmd.creation_flags(
        winbase::DETACHED_PROCESS | winbase::CREATE_NEW_PROCESS_GROUP | winbase::CREATE_NO_WINDOW,
    );
}

#[cfg(unix)]
fn cmd_build_platform_specific(_cmd: &mut Command) {}
