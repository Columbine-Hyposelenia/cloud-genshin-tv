#!/usr/bin/env python3
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
HOME = os.path.dirname(ROOT)
JAVA_HOME = os.path.join(HOME, "jdk-17.0.12+7")
ANDROID_HOME = os.path.join(HOME, "android-sdk")
GRADLE = os.path.join(HOME, "gradle-8.11.1", "bin", "gradle")
BAKSMALI = os.path.join(HOME, "research", "baksmali.jar")
SMALI = os.path.join(HOME, "research", "smali.jar")
BT = os.path.join(ANDROID_HOME, "build-tools", "34.0.0")
KEYSTORE = os.path.join(ROOT, "debug.keystore")
WORK = os.path.join(ROOT, "build_patched")

PATCH1_OLD = """    iget-object p2, p0, Lorg/webrtc/AndroidVideoDecoder;->sharedContext:Lorg/webrtc/EglBase$Context;

    if-eqz p2, :cond_25

    .line 142
"""

PATCH1_NEW = """    iget-object p2, p0, Lorg/webrtc/AndroidVideoDecoder;->sharedContext:Lorg/webrtc/EglBase$Context;

    if-eqz p2, :cond_25

    invoke-static {}, Lcom/cloudgenshin/tv/DecoderMode;->isByteBuffer()Z

    move-result p2

    if-nez p2, :cond_25

    .line 142
"""

PATCH2_OLD = """    iget-object p2, p0, Lorg/webrtc/AndroidVideoDecoder;->sharedContext:Lorg/webrtc/EglBase$Context;

    if-nez p2, :cond_77

    .line 179
"""

PATCH2_NEW = """    iget-object p2, p0, Lorg/webrtc/AndroidVideoDecoder;->sharedContext:Lorg/webrtc/EglBase$Context;

    if-eqz p2, :cond_bbuf

    invoke-static {}, Lcom/cloudgenshin/tv/DecoderMode;->isByteBuffer()Z

    move-result p2

    if-eqz p2, :cond_77

    :cond_bbuf
    .line 179
"""


def run(cmd, **kwargs):
    env = dict(os.environ)
    env["JAVA_HOME"] = JAVA_HOME
    env["ANDROID_HOME"] = ANDROID_HOME
    env["PATH"] = os.path.join(JAVA_HOME, "bin") + os.pathsep + env["PATH"]
    print("+", " ".join(cmd) if isinstance(cmd, list) else cmd)
    subprocess.check_call(cmd, env=env, **kwargs)


def main():
    gradle_apk = os.path.join(ROOT, "app", "build", "outputs", "apk", "release",
                              "app-release.apk")
    installer_apk = os.path.join(ROOT, "installer", "build", "outputs", "apk", "release",
                                 "installer-release.apk")

    run([GRADLE, ":app:assembleRelease", ":installer:assembleRelease", "--no-daemon"])

    if os.path.exists(WORK):
        shutil.rmtree(WORK)
    os.makedirs(WORK)
    apk_dir = os.path.join(WORK, "apk")
    os.makedirs(apk_dir)

    with zipfile.ZipFile(gradle_apk) as zf:
        zf.extractall(apk_dir)

    smali_dir = os.path.join(WORK, "smali")
    java = os.path.join(JAVA_HOME, "bin", "java")
    run([java, "-jar", BAKSMALI, "disassemble", os.path.join(apk_dir, "classes.dex"),
         "-o", smali_dir])

    target = os.path.join(smali_dir, "org", "webrtc", "AndroidVideoDecoder.smali")
    with open(target, "r") as handle:
        content = handle.read()
    assert PATCH1_OLD in content, "patch1 anchor not found"
    assert PATCH2_OLD in content, "patch2 anchor not found"
    content = content.replace(PATCH1_OLD, PATCH1_NEW).replace(PATCH2_OLD, PATCH2_NEW)
    with open(target, "w") as handle:
        handle.write(content)

    new_dex = os.path.join(WORK, "classes.dex")
    run([java, "-jar", SMALI, "assemble", smali_dir, "-o", new_dex])

    shutil.copyfile(new_dex, os.path.join(apk_dir, "classes.dex"))

    unsigned = os.path.join(WORK, "unsigned.apk")
    with zipfile.ZipFile(unsigned, "w") as zf:
        for base, _, files in os.walk(apk_dir):
            for name in files:
                full = os.path.join(base, name)
                arc = os.path.relpath(full, apk_dir)
                method = zipfile.ZIP_STORED if name == "resources.arsc" \
                    else zipfile.ZIP_DEFLATED
                zf.write(full, arc, method)

    aligned = os.path.join(WORK, "aligned.apk")
    run([os.path.join(BT, "zipalign"), "-p", "-f", "4", unsigned, aligned])

    out_apk = os.path.join(ROOT, "cloud-genshin.apk")
    if os.path.exists(out_apk):
        os.remove(out_apk)
    run([os.path.join(BT, "apksigner"), "sign",
         "--ks", KEYSTORE, "--ks-pass", "pass:android",
         "--ks-key-alias", "androiddebugkey", "--key-pass", "pass:android",
         "--out", out_apk, aligned])
    run([os.path.join(BT, "apksigner"), "verify", out_apk])

    out_installer = os.path.join(ROOT, "cloud-genshin-installer.apk")
    shutil.copyfile(installer_apk, out_installer)

    print("main:", out_apk, os.path.getsize(out_apk))
    print("installer:", out_installer, os.path.getsize(out_installer))


if __name__ == "__main__":
    main()
