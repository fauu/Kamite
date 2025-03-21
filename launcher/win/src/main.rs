#![windows_subsystem = "windows"]

use std::ffi::{c_void, CString};
use std::os::raw::c_char;
use std::os::windows::ffi::OsStrExt;
use std::path::PathBuf;
use std::{env, ptr};

use jni_sys_dynamic::{
    jclass, jint, jmethodID, jobjectArray, jsize, jstring, jvalue, JNIEnv, JNILibrary, JavaVM,
    JavaVMInitArgs, JavaVMOption, JNI_EDETACHED, JNI_EEXIST, JNI_EINVAL, JNI_ENOMEM, JNI_ERR,
    JNI_EVERSION, JNI_FALSE, JNI_OK, JNI_VERSION_10,
};

use windows_sys::Win32::System::LibraryLoader::SetDllDirectoryW;
use windows_sys::Win32::System::Console::{AttachConsole, ATTACH_PARENT_PROCESS};

const JVM_BIN_DIR_PATH_REL: &[&str] = &["runtime", "bin"];
const JVM_DLL_PATH_REL: &[&str] = &["runtime", "bin", "server", "jvm.dll"];
const JAR_PATH_REL: &[&str] = &["lib", "generic", "kamite.jar"];
const MAIN_CLASS_NAME: &str = "io/github/kamitejp/Main";
const MAIN_METHOD_NAME: &str = "main";
const MAIN_METHOD_SIG: &str = "([Ljava/lang/String;)V";
const STRING_CLASS_NAME: &str = "java/lang/String";

fn main() {
    // POLISH: refactor when https://github.com/rust-lang/rust/issues/53667 lands
    if let Ok(val) = env::var("_started_from_console") {
        if val == "yes" {
            unsafe { AttachConsole(ATTACH_PARENT_PROCESS) };
        }
    }

    let program_dir = {
        let mut p = env::current_exe().expect("Could not determine current executable's path");
        p.pop();
        p
    };
    let jvm_bin_dir_path = make_jvm_bin_dir_path(program_dir.clone());
    let jvm_dll_path = make_jvm_dll_path(program_dir.clone());
    let jar_path = make_jar_path(program_dir);

    let dll_dir: Vec<u16> = jvm_bin_dir_path
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect();
    unsafe {
        SetDllDirectoryW(dll_dir.as_ptr());
    }

    let jni = JNILibrary::new(jvm_dll_path).expect("Could not load jvm.dll");

    let jvm_options = vec![
        "--enable-preview".into(),
        format!("-Djava.class.path={}", jar_path.display()),
        // Fix for https://github.com/fauu/Kamite/issues/9
        "-Djavax.accessibility.assistive_technologies=".into(),
    ];
    let mut jvm: *mut JavaVM = ptr::null_mut();
    must_create_jvm(jni, jvm_options, &mut jvm);

    let env = must_attach_thread_to_jvm(jvm);

    let main_class = must_find_main_class(env);

    let main_method_id = must_find_main_method(env, main_class);

    let args = must_make_java_string_array(env, env::args().skip(1).collect());

    // Call kamite.jar Main.main()
    unsafe {
        (**env).CallStaticVoidMethodA.unwrap()(
            env,
            main_class,
            main_method_id,
            &args as *const jvalue,
        )
    };

    jni_fail_if_error("DestroyJavaVM", unsafe {
        (**jvm).DestroyJavaVM.unwrap()(jvm)
    });
}

fn make_jvm_bin_dir_path(mut program_dir: PathBuf) -> PathBuf {
    JVM_BIN_DIR_PATH_REL
        .iter()
        .for_each(|seg| program_dir.push(seg));
    program_dir
}

fn make_jvm_dll_path(mut program_dir: PathBuf) -> PathBuf {
    JVM_DLL_PATH_REL
        .iter()
        .for_each(|seg| program_dir.push(seg));
    program_dir
}

fn make_jar_path(mut program_dir: PathBuf) -> PathBuf {
    JAR_PATH_REL.iter().for_each(|seg| program_dir.push(seg));
    program_dir
}

fn must_create_jvm(jni: JNILibrary, options: Vec<String>, jvm: *mut *mut JavaVM) {
    let option_cstrings: Vec<CString> = options
        .iter()
        .map(|s| CString::new(&**s).unwrap())
        .collect();
    let mut jvm_options: Vec<JavaVMOption> = option_cstrings
        .iter()
        .map(|cstr| JavaVMOption {
            optionString: cstr.as_ptr() as *mut c_char,
            extraInfo: ptr::null_mut() as *mut c_void,
        })
        .collect();

    let mut init_args = JavaVMInitArgs {
        version: JNI_VERSION_10,
        options: jvm_options.as_mut_ptr(),
        nOptions: jvm_options.len() as jint,
        ignoreUnrecognized: JNI_FALSE,
    };
    let mut init_env: *mut JNIEnv = ptr::null_mut();
    jni_fail_if_error("CreateJavaVM", unsafe {
        jni.create_java_vm(
            jvm,
            (&mut init_env as *mut *mut JNIEnv) as *mut *mut c_void,
            (&mut init_args as *mut JavaVMInitArgs) as *mut c_void,
        )
        .expect("Could not create Java VM")
    });
}

fn must_attach_thread_to_jvm(jvm: *mut JavaVM) -> *mut JNIEnv {
    let mut env: *mut JNIEnv = ptr::null_mut();
    jni_fail_if_error("AttachCurrentThread", unsafe {
        (**jvm).AttachCurrentThread.unwrap()(
            jvm,
            (&mut env as *mut *mut JNIEnv) as *mut *mut c_void, // or new
            ptr::null_mut(),
        )
    });
    env
}

fn must_find_main_class(env: *mut JNIEnv) -> jclass {
    let name = CString::new(MAIN_CLASS_NAME).unwrap();
    let class: jclass = unsafe { (**env).FindClass.unwrap()(env, name.as_ptr() as *mut c_char) };
    if class.is_null() {
        panic!("Could not find main class");
    }
    class
}

fn must_find_main_method(env: *mut JNIEnv, main_class: jclass) -> jmethodID {
    let name = CString::new(MAIN_METHOD_NAME).unwrap();
    let sig = CString::new(MAIN_METHOD_SIG).unwrap();
    let id: jmethodID = unsafe {
        (**env).GetStaticMethodID.unwrap()(
            env,
            main_class,
            name.as_ptr() as *mut c_char,
            sig.as_ptr() as *mut c_char,
        )
    };
    if id.is_null() {
        panic!("Could not find main()");
    }
    id
}

fn jni_fail_if_error(fn_name: &str, res: jint) {
    if res != JNI_OK {
        let msg = match res {
            JNI_EDETACHED => "thread detached from JVM",
            JNI_EEXIST => "JVM exists already",
            JNI_EINVAL => "invalid arguments",
            JNI_ENOMEM => "not enough memory",
            JNI_ERR => "unknown error",
            JNI_EVERSION => "JNI version error",
            _ => "unknown JNI error value",
        };
        panic!("`{}()` signaled an error: {}", fn_name, msg);
    }
}

fn must_make_java_string_array(env: *mut JNIEnv, strings: Vec<String>) -> jvalue {
    let string_class_name = CString::new(STRING_CLASS_NAME).unwrap();
    let string_class: jclass =
        unsafe { (**env).FindClass.unwrap()(env, string_class_name.as_ptr() as *mut c_char) };
    let arg_arr: jobjectArray = unsafe {
        (**env).NewObjectArray.unwrap()(env, strings.len() as jsize, string_class, ptr::null_mut())
    };
    strings.into_iter().enumerate().for_each(|(i, arg)| {
        let arg_bytes = arg.into_bytes();
        let arg_jstr: jstring = unsafe {
            let cstr = CString::from_vec_unchecked(arg_bytes);
            (**env).NewStringUTF.unwrap()(env, cstr.as_ptr())
        };
        unsafe {
            (**env).SetObjectArrayElement.unwrap()(env, arg_arr, i as jsize, arg_jstr);
            if (**env).ExceptionCheck.unwrap()(env) != 0 {
                panic!("SetObjectArrayElement has thrown an exception");
            }
            (**env).DeleteLocalRef.unwrap()(env, arg_jstr);
        }
    });
    jvalue { l: arg_arr }
}
