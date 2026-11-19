"""Build the Pixel or emulator engine with Swift 6.4.2 and NDK r30."""
import argparse
import os
from pathlib import Path
import platform
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
NDK = SDK / 'ndk/30.0.16248370'
ABI = 'arm64-v8a'
ARCH = 'aarch64'
BUILD = ROOT / 'native/.build/llama-arm64-v8a'
OUTPUT = ROOT / 'app/src/debug/jniLibs/arm64-v8a'


def run(arguments):
    subprocess.run([str(argument) for argument in arguments], cwd=ROOT, check=True,
                   env={**os.environ, 'ANDROID_NDK_HOME': str(NDK)})


def build_llama():
    cmake = SDK / 'cmake/3.31.6/bin/cmake'
    run([cmake, '-S', 'native/Vendor/llama.cpp', '-B', BUILD,
         f'-DCMAKE_TOOLCHAIN_FILE={NDK}/build/cmake/android.toolchain.cmake',
         f'-DANDROID_ABI={ABI}', '-DANDROID_PLATFORM=android-28',
         '-DCMAKE_BUILD_TYPE=Release', '-DGGML_OPENMP=OFF', '-DGGML_NATIVE=OFF',
         '-DLLAMA_BUILD_TESTS=OFF', '-DLLAMA_BUILD_EXAMPLES=OFF',
         '-DLLAMA_BUILD_SERVER=OFF', '-DLLAMA_CURL=OFF'])
    run([cmake, '--build', BUILD, '--parallel', '6'])


def build_cpu_adapter():
    host = 'darwin-x86_64' if platform.system() == 'Darwin' else 'linux-x86_64'
    compiler = NDK / f'toolchains/llvm/prebuilt/{host}/bin/{ARCH}-linux-android28-clang++'
    run([compiler, '-c', '-fPIC', '-O2', 'native/cpu_model.cpp',
         '-Inative/Vendor/llama.cpp/include', '-Inative/Vendor/llama.cpp/ggml/include',
         '-o', BUILD / 'cpu_model.o'])


def build_swift():
    swift = os.environ.get('TYPING_SWIFT', 'swift')
    arguments = [swift, 'build', '--package-path', 'native', '-c', 'release',
         '--swift-sdk', 'swift-6.4.2-RELEASE_android',
         '--triple', f'{ARCH}-unknown-linux-android28', '--static-swift-stdlib',
         '-Xcc', f'-I{ROOT}/native/Vendor/llama.cpp/ggml/include',
         '-Xlinker', f'-L{BUILD}/bin', '-Xlinker', BUILD / 'cpu_model.o',
         '-Xlinker', '--wrap=llama_model_load_from_file']
    run(arguments)
    binary_directory = subprocess.check_output(
        [str(value) for value in arguments + ['--show-bin-path']], cwd=ROOT, text=True,
        env={**os.environ, 'ANDROID_NDK_HOME': str(NDK)}).strip().splitlines()[-1]
    shutil.copy2(Path(binary_directory) / 'libAndroidTyping.so', OUTPUT)


def build_bridge():
    host = 'darwin-x86_64' if platform.system() == 'Darwin' else 'linux-x86_64'
    toolchain = NDK / f'toolchains/llvm/prebuilt/{host}'
    run([toolchain / f'bin/{ARCH}-linux-android28-clang++', '-shared', '-fPIC',
         '-std=c++17', '-O2', '-Wl,-z,max-page-size=16384', 'native/bridge.cpp',
         f'-L{OUTPUT}', '-lAndroidTyping', '-lllama', '-landroid', f'-I{ROOT}/native/Vendor/llama.cpp/include', f'-I{ROOT}/native/Vendor/llama.cpp/ggml/include', '-o', OUTPUT / 'libtyping_jni.so'])
    shutil.copy2(toolchain / f'sysroot/usr/lib/{ARCH}-linux-android/libc++_shared.so', OUTPUT)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    build_llama()
    build_cpu_adapter()
    build_swift()
    for library in (BUILD / 'bin').glob('*.so'):
        shutil.copy2(library, OUTPUT)
    build_bridge()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], default='arm64-v8a')
    ABI = parser.parse_args().abi
    ARCH = 'aarch64' if ABI == 'arm64-v8a' else 'x86_64'
    BUILD = ROOT / f'native/.build/llama-{ABI}'
    OUTPUT = ROOT / f'app/src/debug/jniLibs/{ABI}'
    main()
