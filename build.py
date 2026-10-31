import os
import sys
import subprocess
import zipfile
import shutil

# Try to find Android SDK and Java automatically
def find_android_sdk():
    env_sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if env_sdk and os.path.exists(env_sdk):
        return env_sdk
    user_sdk = os.path.expanduser(r"~\AppData\Local\Android\Sdk")
    if os.path.exists(user_sdk):
        return user_sdk
    return None

def find_java():
    if os.environ.get("JAVA_HOME") and os.path.exists(os.environ["JAVA_HOME"]):
        return os.environ["JAVA_HOME"]
    # Check default Android Studio JBR
    as_jbr = r"C:\Program Files\Android\Android Studio\jbr"
    if os.path.exists(as_jbr):
        return as_jbr
    return None

SDK_DIR = find_android_sdk()
if not SDK_DIR:
    print("[ERROR] Android SDK not found! Please set ANDROID_HOME environment variable.")
    sys.exit(1)

JAVA_HOME = find_java()
if JAVA_HOME:
    os.environ["JAVA_HOME"] = JAVA_HOME
    os.environ["PATH"] = os.path.join(JAVA_HOME, "bin") + os.pathsep + os.environ.get("PATH", "")

# Locate build tools
build_tools_root = os.path.join(SDK_DIR, "build-tools")
available_bt = sorted(os.listdir(build_tools_root), reverse=True) if os.path.exists(build_tools_root) else []
if not available_bt:
    print("[ERROR] No build-tools found in Android SDK.")
    sys.exit(1)

BUILD_TOOLS = os.path.join(build_tools_root, available_bt[0])
print(f"Using Android Build Tools: {available_bt[0]}")

PLATFORM_JAR = os.path.join(SDK_DIR, "platforms", "android-34", "android.jar")
if not os.path.exists(PLATFORM_JAR):
    # fallback to any available platform
    platforms_root = os.path.join(SDK_DIR, "platforms")
    platforms = sorted(os.listdir(platforms_root), reverse=True)
    if platforms:
        PLATFORM_JAR = os.path.join(platforms_root, platforms[0], "android.jar")
        print(f"Using platform jar: {platforms[0]}")
    else:
        print("[ERROR] No Android platform found.")
        sys.exit(1)

AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe" if os.name == "nt" else "aapt2")
D8 = os.path.join(BUILD_TOOLS, "d8.bat" if os.name == "nt" else "d8")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe" if os.name == "nt" else "zipalign")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat" if os.name == "nt" else "apksigner")
JAVAC = os.path.join(JAVA_HOME, "bin", "javac.exe" if os.name == "nt" else "javac") if JAVA_HOME else "javac"
KEYSTORE = os.path.expanduser(r"~/.android/debug.keystore")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
APP_DIR = os.path.join(BASE_DIR, "app")
OUT_DIR = os.path.join(BASE_DIR, "build_out")

def run(cmd, cwd=None):
    res = subprocess.run(cmd, cwd=cwd, shell=isinstance(cmd, str), capture_output=True, text=True)
    if res.returncode != 0:
        print("STDOUT:", res.stdout)
        print("STDERR:", res.stderr)
        raise RuntimeError(f"Command failed with code {res.returncode}")
    return res.stdout

def build():
    if os.path.exists(OUT_DIR):
        shutil.rmtree(OUT_DIR)
    os.makedirs(OUT_DIR, exist_ok=True)

    res_dir = os.path.join(APP_DIR, "src", "main", "res")
    manifest = os.path.join(APP_DIR, "src", "main", "AndroidManifest.xml")
    compiled_res = os.path.join(OUT_DIR, "compiled_res.zip")
    gen_dir = os.path.join(OUT_DIR, "gen")
    classes_dir = os.path.join(OUT_DIR, "classes")
    dex_dir = os.path.join(OUT_DIR, "dex")
    os.makedirs(gen_dir, exist_ok=True)
    os.makedirs(classes_dir, exist_ok=True)
    os.makedirs(dex_dir, exist_ok=True)

    raw_apk = os.path.join(OUT_DIR, "thermometer-raw.apk")
    aligned_apk = os.path.join(OUT_DIR, "thermometer-aligned.apk")
    final_apk = os.path.join(BASE_DIR, "release", "WatchThermometer.apk")

    print("[1/6] Compiling resources (aapt2)...")
    run([AAPT2, "compile", "--dir", res_dir, "-o", compiled_res])

    print("[2/6] Linking resources...")
    run([
        AAPT2, "link",
        "-I", PLATFORM_JAR,
        "--manifest", manifest,
        "--min-sdk-version", "26",
        "--target-sdk-version", "34",
        "--java", gen_dir,
        "-o", raw_apk,
        compiled_res
    ])

    src_files = []
    for root, _, files in os.walk(os.path.join(APP_DIR, "src", "main", "java")):
        for f in files:
            if f.endswith(".java"):
                src_files.append(os.path.join(root, f))
    for root, _, files in os.walk(gen_dir):
        for f in files:
            if f.endswith(".java"):
                src_files.append(os.path.join(root, f))

    print(f"[3/6] Compiling {len(src_files)} Java source files...")
    run([
        JAVAC,
        "-g",
        "-source", "8",
        "-target", "8",
        "-encoding", "UTF-8",
        "-cp", PLATFORM_JAR,
        "-d", classes_dir
    ] + src_files)

    print("[4/6] Packaging classes and generating classes.dex (d8)...")
    classes_jar = os.path.join(OUT_DIR, "classes.jar")
    with zipfile.ZipFile(classes_jar, "w") as z:
        for root, _, files in os.walk(classes_dir):
            for f in files:
                if f.endswith('.class'):
                    full_p = os.path.join(root, f)
                    rel_p = os.path.relpath(full_p, classes_dir).replace("\\", "/")
                    z.write(full_p, rel_p)

    d8_cmd = f'"{D8}" --min-api 26 --output "{dex_dir}" --lib "{PLATFORM_JAR}" "{classes_jar}"'
    run(d8_cmd)

    print("[5/6] Aligning package...")
    classes_dex = os.path.join(dex_dir, "classes.dex")
    with zipfile.ZipFile(raw_apk, "a") as z:
        z.write(classes_dex, "classes.dex")

    if os.path.exists(aligned_apk):
        os.remove(aligned_apk)
    run([ZIPALIGN, "-p", "-f", "4", raw_apk, aligned_apk])

    print("[6/6] Signing APK with debug key...")
    os.makedirs(os.path.join(BASE_DIR, "release"), exist_ok=True)
    if not os.path.exists(KEYSTORE):
        # Generate debug keystore if missing
        keytool = os.path.join(JAVA_HOME, "bin", "keytool.exe" if os.name == "nt" else "keytool") if JAVA_HOME else "keytool"
        os.makedirs(os.path.dirname(KEYSTORE), exist_ok=True)
        run(f'"{keytool}" -genkey -v -keystore "{KEYSTORE}" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"')

    sign_cmd = f'"{APKSIGNER}" sign --ks "{KEYSTORE}" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out "{final_apk}" "{aligned_apk}"'
    run(sign_cmd)

    size = os.path.getsize(final_apk)
    print(f"\n[SUCCESS] Built release APK: {final_apk} ({size:,} bytes)")

if __name__ == "__main__":
    build()
