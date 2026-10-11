"""Install only into an explicitly selected instance, after validating local converted assets."""
import argparse, hashlib, json, shutil, zipfile
from pathlib import Path

def validate(base, rows):
    checked=[]; errors=[]
    for row in rows:
        file=(base/row['path']).resolve()
        if not file.is_relative_to(base): raise ValueError('Asset path escapes selected directory')
        if not file.is_file():
            if row.get('optional'): continue
            errors.append('Missing '+row['path']);continue
        if file.stat().st_size!=row['size'] or hashlib.sha256(file.read_bytes()).hexdigest()!=row['sha256']:
            errors.append('Different converted version: '+row['path']);continue
        checked.append((file,row['path']))
    if errors: raise ValueError('Asset validation failed before installation:\n'+'\n'.join(errors[:20]))
    return checked

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--instance',required=True,type=Path);ap.add_argument('--resources',required=True,type=Path);ap.add_argument('--runtime',required=True,type=Path)
    args=ap.parse_args();root=Path(__file__).resolve().parent
    manifest=json.loads((root/'asset-manifest.json').read_text())
    resource_files=validate(args.resources.resolve(),manifest['resources']);runtime_files=validate(args.runtime.resolve(),manifest['runtime'])
    instance=args.instance.resolve();mods=instance/'mods';private=root/'private';mods.mkdir(parents=True,exist_ok=True);private.mkdir(exist_ok=True)
    destination=mods/'bo2beta-local-assets.jar';temporary=mods/'bo2beta-local-assets.tmp'
    with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'bo2beta_local_assets','version':'0.1.0','name':'Private local BO2 resources','environment':'*'}))
        for file,name in resource_files:archive.write(file,name)
    temporary.replace(destination)
    shutil.copyfile(root/'bo2-minecraft-playtest.jar',mods/'bo2-minecraft-playtest.jar')
    for file,name in runtime_files:
        target=private/'runtime'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,target)
    print('Installed into '+str(instance)+'; locally generated asset mod stays private. Install Fabric API and your compatible Nuketown world independently, then run Start-Companion.cmd.')
if __name__=='__main__':main()
