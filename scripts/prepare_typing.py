"""Download pinned, unchanged upstream sources and model. Run from any directory."""
import hashlib
from pathlib import Path
import shutil
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
VENDOR = ROOT / 'native/Vendor'
ASSETS = ROOT / 'app/src/debug/assets/typing'
MODEL_SHA = '501f605d088f5b988791a00ae19ed46985ed7c48144f364b2f3f1f951c9b2083'


def checkout(name, url, revision, submodules=False):
    directory = VENDOR / name
    if not directory.exists():
        subprocess.run(['git', 'clone', url, str(directory)], check=True)
    subprocess.run(['git', 'checkout', '--detach', revision], cwd=directory, check=True)
    if submodules:
        subprocess.run(['git', 'submodule', 'update', '--init', '--recursive'], cwd=directory, check=True)
    return directory


def prepare_model():
    destination = ASSETS / 'model.gguf'
    if not destination.exists():
        url = ('https://huggingface.co/Miwa-Keita/zenz-v3-small-gguf/resolve/'
               'd48369e21adb9f49903eb7c54be1a1d9723eb805/ggml-model-Q5_K_M.gguf')
        temporary = destination.with_suffix('.download')
        urllib.request.urlretrieve(url, temporary)
        temporary.replace(destination)
    if hashlib.sha256(destination.read_bytes()).hexdigest() != MODEL_SHA:
        raise RuntimeError('Model checksum mismatch; remove the damaged model and retry.')


def main():
    VENDOR.mkdir(parents=True, exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)
    source = checkout('AzooKey', 'https://github.com/azooKey/AzooKeyKanaKanjiConverter.git',
                      '80b8204f1cdfb364bb2ed355cf52c7ebb2519a0c', submodules=True)
    checkout('llama.cpp', 'https://github.com/azooKey/llama.cpp.git',
             '10131b23ee0dbd9d03dfc618cec9fe3402919277')
    dictionary = source / 'Sources/KanaKanjiConverterModuleWithDefaultDictionary/azooKey_dictionary_storage/Dictionary'
    shutil.copytree(dictionary, ASSETS / 'Dictionary', dirs_exist_ok=True)
    prepare_model()


if __name__ == '__main__':
    main()
