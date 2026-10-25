#!/usr/bin/env python3
"""build_apk.py - build a signed APK for Android 5.1 (API 22) without Gradle.

Two paths:
- project with libs/*.aar (support libraries): aapt v1 packages all res dirs
  with --extra-packages so every library R.java is generated, then javac/d8.
- plain project: aapt2 compile/link.

Requirements:
  - JDK 8+ (javac / keytool)
  - Android SDK with build-tools (aapt2, zipalign, d8) and platform android-22
  Resolution order: env vars KKTV_BUILD_ENV / KKTV_JDK / KKTV_BUILD_TOOLS /
  ANDROID_SDK_ROOT > local build-env/ > PATH.
  A debug signing keystore is auto-generated if missing (override path with
  APK_KEYSTORE).

Usage: build_apk.py <project-dir> <output.apk>
Project dir must contain: AndroidManifest.xml, res/, java/ (or src/),
optional assets/, optional libs/*.aar|*.jar
"""
import glob
import os
import subprocess
import sys
import zipfile
import shutil

ROOT = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.dirname(ROOT)


def _first_env(*names):
    for n in names:
        v = os.environ.get(n)
        if v:
            return v
    return None


def _pick_dir(path, patterns):
    """在 path 下按 patterns 依次找目录（忽略文件），返回第一个命中的。"""
    for pat in patterns:
        for hit in sorted(glob.glob(os.path.join(path, pat))):
            if os.path.isdir(hit):
                return hit
    return None


# ---- 构建环境解析 ----
# 优先级：环境变量（KKTV_BUILD_ENV / KKTV_JDK / KKTV_BUILD_TOOLS /
# ANDROID_SDK_ROOT / APK_KEYSTORE）> 项目内 build-env/ > 系统 PATH
# 换一台没有 build-env/ 的机器，用自己的 JDK + Android SDK 也能构建。
ENV = _first_env("KKTV_BUILD_ENV") or os.path.join(PROJ, "build-env")
JDK = _first_env("KKTV_JDK")
if not JDK:
    local_jdk = _pick_dir(ENV, ["jdk-*"])
    if local_jdk:
        JDK = os.path.join(local_jdk, "bin")
SDK = _first_env("ANDROID_SDK_ROOT", "ANDROID_HOME")
if not SDK:
    local_sdk = os.path.join(ENV, "sdk")
    if os.path.isdir(local_sdk):
        SDK = local_sdk


def _jdk_tool(name):
    """JDK 工具：本地 JDK/bin 优先，找不到就退回 PATH。"""
    if JDK and os.path.exists(os.path.join(JDK, name)):
        return os.path.join(JDK, name)
    return name


def _require_sdk():
    if not SDK:
        raise SystemExit(
            "找不到 Android SDK。请设置环境变量 ANDROID_SDK_ROOT，\n"
            "或把 JDK + Android SDK 放到项目内的 build-env/ 目录。")
    return SDK


def _build_tools():
    bt = _first_env("KKTV_BUILD_TOOLS")
    if bt and os.path.isdir(bt):
        return bt
    bt = _pick_dir(os.path.join(_require_sdk(), "build-tools"), ["30.0.3", "*"])
    if not bt:
        raise SystemExit("Android SDK 里没有 build-tools，请先安装。")
    return bt


def _platform_jar(api):
    """按 API 级别取 android.jar；指定版本不存在时退回已有的最高版本。"""
    platforms = os.path.join(_require_sdk(), "platforms")
    exact = os.path.join(platforms, "android-%d" % api)
    if os.path.isdir(exact):
        return os.path.join(exact, "android.jar")
    any_api = _pick_dir(platforms, ["android-*"])
    if not any_api:
        raise SystemExit("Android SDK 里没有 platforms/android-*，请先安装。")
    print("警告：找不到 android-%d，改用 %s" % (api, os.path.basename(any_api)))
    return os.path.join(any_api, "android.jar")


# support-compat 28 references API 24+ attrs (android:attr/offset): link and
# compile against a newer platform (compileSdk 28) while keeping minSdk 22
ANDROID_JAR = _platform_jar(22)
COMPILE_JAR = _platform_jar(28)
BT = _build_tools()

# 签名密钥：APK_KEYSTORE 可指定路径；缺失时自动生成一个 debug 密钥
# （注意：换了密钥重装同一包名应用需先卸载，签名不一致会被拒）
KEYSTORE = _first_env("APK_KEYSTORE") or os.path.join(ROOT, "debug.keystore")


def ensure_keystore():
    if os.path.exists(KEYSTORE):
        return
    os.makedirs(os.path.dirname(os.path.abspath(KEYSTORE)), exist_ok=True)
    keytool = _jdk_tool("keytool.exe" if os.name == "nt" else "keytool")
    print("生成 debug 签名密钥:", KEYSTORE)
    subprocess.run([keytool, "-genkeypair", "-keystore", KEYSTORE,
                    "-storetype", "PKCS12", "-storepass", "android",
                    "-keypass", "android", "-alias", "androiddebugkey",
                    "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                    "-dname", "CN=Android Debug,O=Android,C=US"],
                   check=True)

# R packages referenced by the support-library jars (scanned from bytecode).
# 23.4.0 era: recyclerview references its own R; support-v4 references none.
EXTRA_R_PACKAGES = [
    "android.support.v7.recyclerview",
    "android.support.v4",
]
# aapt2 --extra-packages takes ONE package per flag -> repeat the flag
EXTRA_FLAGS = []
for _p in EXTRA_R_PACKAGES:
    EXTRA_FLAGS += ["--extra-packages", _p]


def run(cmd, **kw):
    print("+", " ".join(cmd[:4]), "...")
    p = subprocess.run(cmd, capture_output=True, text=True, errors="replace", **kw)
    if p.returncode != 0:
        print(p.stdout[-4000:])
        print(p.stderr[-4000:])
        raise SystemExit(f"FAILED: {' '.join(cmd)}")


def prepare_libs(proj):
    """Extract classes.jar + res/ from every .aar in <proj>/libs.
    Also extracts bundled libs/*.jar (e.g. support-v4 23.4.0 ships
    libs/internal_impl-23.4.0.jar with package-private impl classes such as
    ScrollerCompatGingerbread - missing it causes NoClassDefFoundError at
    runtime because ScrollerCompatImplIcs inherits createScroller from
    ScrollerCompatImplGingerbread).
    Returns (jar_list, res_dir_list)."""
    libsdir = os.path.join(proj, "libs")
    jars, resdirs = [], []
    if not os.path.isdir(libsdir):
        return jars, resdirs
    os.makedirs(os.path.join(libsdir, "jars"), exist_ok=True)
    for f in sorted(os.listdir(libsdir)):
        p = os.path.join(libsdir, f)
        if f.endswith(".aar"):
            jarpath = os.path.join(libsdir, "jars", f[:-4] + ".jar")
            resdir = os.path.join(libsdir, "res", f[:-4])
            if not os.path.exists(jarpath) or not os.path.isdir(resdir):
                with zipfile.ZipFile(p) as z:
                    with open(jarpath, "wb") as w:
                        w.write(z.read("classes.jar"))
                    shutil.rmtree(resdir, ignore_errors=True)
                    os.makedirs(resdir, exist_ok=True)
                    for n in z.namelist():
                        if n.startswith("res/") and not n.endswith("/"):
                            out = os.path.join(resdir, n[len("res/"):])
                            os.makedirs(os.path.dirname(out), exist_ok=True)
                            with open(out, "wb") as w:
                                w.write(z.read(n))
            jars.append(jarpath)
            # bundled jars inside the aar (support-v4: libs/internal_impl-*.jar)
            with zipfile.ZipFile(p) as z:
                for n in z.namelist():
                    if n.startswith("libs/") and n.endswith(".jar"):
                        dest = os.path.join(libsdir, "jars", os.path.basename(n))
                        if not os.path.exists(dest):
                            with open(dest, "wb") as w:
                                w.write(z.read(n))
                        if dest not in jars:
                            jars.append(dest)
            if os.path.isdir(resdir) and os.listdir(resdir):
                resdirs.append(resdir)
        elif f.endswith(".jar"):
            jars.append(p)
    return jars, resdirs


def main():
    proj, out = os.path.abspath(sys.argv[1]), os.path.abspath(sys.argv[2])
    work = os.path.join(proj, "build")
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(os.path.join(work, "gen"), exist_ok=True)
    os.makedirs(os.path.join(work, "classes"), exist_ok=True)

    jars, resdirs = prepare_libs(proj)
    assets = os.path.join(proj, "assets")
    srcdir = os.path.join(proj, "java")
    if not os.path.isdir(srcdir):
        srcdir = os.path.join(proj, "src")

    if resdirs:
        # ---- aapt2 path with library resources: compile each res dir
        # separately (aapt2 merges libraries correctly), then link with
        # --extra-packages so every library R.java is generated ----
        flats = []
        for i, d in enumerate([os.path.join(proj, "res")] + resdirs):
            fo = os.path.join(work, "compiled", f"lib{i}.zip")
            os.makedirs(os.path.dirname(fo), exist_ok=True)
            run([os.path.join(BT, "aapt2.exe"), "compile", "--dir", d, "-o", fo])
            flats.append(fo)
        link_cmd = [os.path.join(BT, "aapt2.exe"), "link", "-o",
             os.path.join(work, "base.apk"),
             "--manifest", os.path.join(proj, "AndroidManifest.xml"),
             "-I", COMPILE_JAR if resdirs else ANDROID_JAR,
             "--java", os.path.join(work, "gen"),
             "--min-sdk-version", "22", "--target-sdk-version", "22",
             "--version-code", "1", "--version-name", "1.0"] \
            + EXTRA_FLAGS
        if os.path.isdir(assets):
            link_cmd += ["-A", assets]
        link_cmd += flats
        run(link_cmd)
    else:
        # ---- aapt2 path (no library resources) ----
        os.makedirs(os.path.join(work, "compiled"), exist_ok=True)
        run([os.path.join(BT, "aapt2.exe"), "compile", "--dir",
             os.path.join(proj, "res"), "-o",
             os.path.join(work, "compiled", "res.zip")])
        link_cmd = [os.path.join(BT, "aapt2.exe"), "link", "-o",
             os.path.join(work, "base.apk"),
             "--manifest", os.path.join(proj, "AndroidManifest.xml"),
             "-I", COMPILE_JAR if resdirs else ANDROID_JAR,
             "--java", os.path.join(work, "gen"),
             "--min-sdk-version", "22", "--target-sdk-version", "22",
             "--version-code", "1", "--version-name", "1.0"]
        if os.path.isdir(assets):
            link_cmd += ["-A", assets]
        link_cmd.append(os.path.join(work, "compiled", "res.zip"))
        run(link_cmd)

    # ---- javac ----
    srcs = []
    for base in (srcdir, os.path.join(work, "gen")):
        for dirpath, _, files in os.walk(base):
            for f in files:
                if f.endswith(".java"):
                    srcs.append(os.path.join(dirpath, f))
    cp = ";".join([COMPILE_JAR if jars else ANDROID_JAR] + jars)
    run([os.path.join(JDK, "javac.exe"), "-encoding", "UTF-8",
         "-source", "8", "-target", "8", "-nowarn",
         "-classpath", cp, "-d",
         os.path.join(work, "classes")] + srcs)

    # ---- d8 -> classes.dex (java directly: d8.bat needs find_java.bat) ----
    inputs = []
    for dirpath, _, files in os.walk(os.path.join(work, "classes")):
        for f in files:
            if f.endswith(".class"):
                inputs.append(os.path.join(dirpath, f))
    run([os.path.join(JDK, "java.exe"), "-cp",
         os.path.join(BT, "lib", "d8.jar"), "com.android.tools.r8.D8",
         "--lib", COMPILE_JAR if jars else ANDROID_JAR,
         "--release", "--min-api", "22",
         "--output", os.path.join(work, "dex-out.zip")] + inputs + jars)

    # ---- inject ALL dex files (stored, uncompressed) ----
    # d8 splits output into classes.dex, classes2.dex, ... when the method
    # count exceeds 64K (the support-v4 umbrella easily does). Dropping the
    # extra files leaves class DEFINITIONS behind while references remain ->
    # "ClassNotFoundException ... on path: DexPathList" at runtime.
    # minSdk 22 >= 21 -> native multidex, no MultiDexApplication needed.
    dexdir = os.path.join(work, "dex")
    os.makedirs(dexdir, exist_ok=True)
    with zipfile.ZipFile(os.path.join(work, "dex-out.zip")) as z:
        z.extractall(dexdir)
    dex_files = sorted(f for f in os.listdir(dexdir) if f.endswith(".dex"))
    if not dex_files:
        raise SystemExit("d8 produced no classes.dex")
    print("dex files:", dex_files)
    with zipfile.ZipFile(os.path.join(work, "base.apk"), "a") as apk:
        for name in dex_files:
            apk.write(os.path.join(dexdir, name), name,
                      compress_type=zipfile.ZIP_STORED)

    # ---- zipalign + sign ----
    ensure_keystore()
    shutil.copyfile(os.path.join(work, "base.apk"), out)
    run([os.path.join(BT, "zipalign.exe"), "-f", "4", out, out + ".aligned"])
    shutil.move(out + ".aligned", out)
    run([os.path.join(JDK, "java.exe"), "-jar",
         os.path.join(BT, "lib", "apksigner.jar"), "sign",
         "--ks", KEYSTORE,
         "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android",
         "--key-pass", "pass:android", out])
    print("APK READY:", out)


if __name__ == "__main__":
    main()
