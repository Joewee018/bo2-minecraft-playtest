"""Build the downloadable archive from an explicit software-only allowlist."""
from pathlib import Path
import hashlib,json,zipfile
root=Path(__file__).resolve().parents[1]
files=['bo2-minecraft-playtest.jar','bo2-playtest-companion.exe','install.py','Start-Companion.cmd','Start-Companion.ps1','asset-manifest.json','Install-Notes.txt','THIRD-PARTY-NOTICES.txt']
allowed={'fabric.mod.json','bo2beta.mixins.json','bo2-minecraft-beta-1.20.1-refmap.json','META-INF/MANIFEST.MF'}
with zipfile.ZipFile(root/files[0]) as jar:
    assert all(n.endswith('.class') or n in allowed for n in jar.namelist()),'Core contains unapproved resources'
    assert 'bo2-minecraft-beta-1.20.1-refmap.json' in jar.namelist(),'Production mixin mappings missing'
output=root/'docs/downloads/bo2-minecraft-playtest-windows.zip'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
    for name in files:z.write(root/name,name)
with zipfile.ZipFile(output) as z:assert set(z.namelist())==set(files)
report={'status':'passed','no_game_assets_bundled':True,'download_bytes':output.stat().st_size,'download_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'core_sha256':hashlib.sha256((root/files[0]).read_bytes()).hexdigest(),'companion_sha256':hashlib.sha256((root/files[1]).read_bytes()).hexdigest(),'checks':['explicit download allowlist','core contains only compiled classes and mod metadata','production mixin mappings included','no art/audio/animations/worlds in download']}
(root/'distribution-audit.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
