#!/usr/bin/env python3
import os
import shutil
import subprocess

ROOT = os.path.dirname(os.path.abspath(__file__))
HOME = os.path.dirname(ROOT)
JAVA_HOME = os.path.join(HOME, "jdk-17")
ANDROID_HOME = os.path.join(HOME, "android-sdk")
GRADLE = os.path.join(HOME, "gradle-8.11.1", "bin", "gradle")
DIST = os.path.join(ROOT, "dist")


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

    os.makedirs(DIST, exist_ok=True)
    out_apk = os.path.join(DIST, "cloud-genshin.apk")
    out_installer = os.path.join(DIST, "cloud-genshin-installer.apk")
    shutil.copyfile(gradle_apk, out_apk)
    shutil.copyfile(installer_apk, out_installer)

    print("main:", out_apk, os.path.getsize(out_apk))
    print("installer:", out_installer, os.path.getsize(out_installer))


if __name__ == "__main__":
    main()
