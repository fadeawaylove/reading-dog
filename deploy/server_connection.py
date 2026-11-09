"""Read the local Windows-user-encrypted SSH profile; never print credentials."""
import base64
import ctypes
from ctypes import wintypes
import json
import os
from pathlib import Path


class Blob(ctypes.Structure):
    _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]


def protect(data: bytes, decrypt=False) -> bytes:
    if os.name != 'nt':
        raise RuntimeError('The saved SSH profile requires the same Windows user')
    source = ctypes.create_string_buffer(data)
    incoming = Blob(len(data), ctypes.cast(source, ctypes.POINTER(ctypes.c_ubyte)))
    outgoing = Blob()
    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    fn = crypt.CryptUnprotectData if decrypt else crypt.CryptProtectData
    fn.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p,
                   ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    fn.restype = wintypes.BOOL
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    if not fn(ctypes.byref(incoming), None, None, None, None, 1, ctypes.byref(outgoing)):
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        return ctypes.string_at(outgoing.data, outgoing.size)
    finally:
        kernel.LocalFree(outgoing.data)


def profile_path():
    return Path(os.environ['LOCALAPPDATA']) / 'CodexServerConnections' / 'reading-dog.json'


def load():
    value = json.loads(profile_path().read_text(encoding='utf-8'))
    value['password'] = protect(base64.b64decode(value.pop('password_dpapi')), decrypt=True).decode('utf-8')
    return value


def save(host, username, password):
    value = {'host': host, 'username': username,
             'password_dpapi': base64.b64encode(protect(password.encode('utf-8'))).decode('ascii')}
    path = profile_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    restored = load()
    if restored['password'] != password:
        raise RuntimeError('Encrypted profile verification failed')


if __name__ == '__main__':
    import argparse
    import getpass
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', required=True)
    parser.add_argument('--username', default='root')
    args = parser.parse_args()
    save(args.host, args.username, getpass.getpass('SSH password (encrypted local storage): '))
    print('PASS saved Windows-user-encrypted SSH profile')
