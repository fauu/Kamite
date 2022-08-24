#![windows_subsystem = "console"]

use std::env;
use std::process::Command;
use std::process::Stdio;

// A wrapper is needed because cmd.exe proceeds without waiting for the main launcher to exit
// (because its subsystem is "windows"), resulting in mangled console output
fn main() {
    let main_exe_path = {
        let mut p = env::current_exe().expect("Could not determine current executable's path");
        p.set_extension("exe");
        p
    };

    // Tell the main launcher whether to attach to console
    env::set_var("_started_from_console", "yes");

    let mut child = Command::new(main_exe_path)
        .stdin(Stdio::null())
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit())
        .spawn()
        .expect("Failed to launch Kamite");
    child.wait().expect("Process was not running");
    println!(); // Otherwise ENTER press is needed to show console prompt again
}
