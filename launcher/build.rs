fn main() {
    let is_release = std::env::var("PROFILE").unwrap() == "release";
    if !is_release {
        return;
    }
    let is_windows = std::env::var("CARGO_CFG_WINDOWS").is_ok();
    if !is_windows {
        return;
    }
    embed_resource::compile("res/resources.rc");
}
