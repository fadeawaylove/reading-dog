"""Expand the captured TTText oracle and copy the production reader resources."""
from pathlib import Path
import json, shutil, zipfile

root = Path(__file__).resolve().parent
dest = root / 'typography-assets'
dest.mkdir(exist_ok=True)
with zipfile.ZipFile(root / 'typography-goldens.zip') as archive:
    names = json.loads(archive.read('index.json'))
    for name in ['index.json', *names]:
        if Path(name).name != name:
            raise ValueError('Fixture names must be flat asset names')
        (dest / name).write_bytes(archive.read(name))
for path in (root.parent / 'assets').glob('reader-*'):
    shutil.copyfile(path, dest / path.name)
required = {'index.json', *names, *(p.name for p in (root.parent / 'assets').glob('reader-*'))}
# This directory is generated exclusively by this typography suite.
for path in dest.iterdir():
    if path.name not in required:
        if path.is_dir():
            shutil.rmtree(path)
        else:
            path.unlink()
print(f'Prepared {len(names)} captured reference configurations')
