#!/usr/bin/env python3
"""Write the wind generator's OBJ models and model JSONs from the original's OBJ files.

The original (LambdaInnovation/AcademyCraft at 7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9,
src/main/resources/assets/academy/models/windgen_{base,fan,main,pillar}.obj) drew them with its own OBJ renderer and
bound the textures itself. Forge's OBJ loader needs a material, so the only change to each file is:
  - the original's mtllib lines (naming files the original never shipped) are replaced by windgen.mtl;
  - "usemtl windgen" follows each group line. Its texture is the model JSON's "texture".
Vertices, texture coordinates, normals, smoothing groups and faces are the original's, in the original's coordinates.
The renderer applies the original renderers' transforms (RenderWindGenBase/Main: the block origin and the pivot;
RenderWindGenPillar: glTranslated(.5, 0, .5), which the pillar's block model JSON gives as its root transform).

Usage: generate_wind_models.py --legacy <original assets/academy/models> [--check]
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/academy/models'
PARTS = ('base', 'fan', 'main', 'pillar')
MATERIAL = 'windgen'
HEADER = ('# Re:AcademyCraft: the original model, unchanged but for mtllib and usemtl (tools/generate_wind_models.py).\n'
          '# The original renderer bound its texture itself; here the model JSON names it as "texture".\n')
MTL = ('# Re:AcademyCraft: the one material of the wind generator models. Each model JSON names its texture as "texture".\n'
       'newmtl ' + MATERIAL + '\n'
       'map_Kd #texture\n')


def obj(source):
    lines = source.replace('\r\n', '\n').replace('\r', '\n').split('\n')
    if lines and lines[-1] == '':
        lines.pop()
    head = 0
    while head < len(lines) and (lines[head].startswith('#') or not lines[head].strip()):
        head += 1
    out = lines[:head] + [HEADER.rstrip('\n'), 'mtllib ' + MATERIAL + '.mtl']
    for line in lines[head:]:
        word = line.split(maxsplit=1)[0] if line.strip() else ''
        if word in ('mtllib', 'usemtl'):
            continue
        out.append(line)
        if word == 'g':
            out.append('usemtl ' + MATERIAL)
    return '\n'.join(out) + '\n'


def model(part, texture, transform=None):
    data = {'loader': 'forge:obj', 'model': 'academy:models/windgen_%s.obj' % part,
            'automatic_culling': False, 'shade_quads': True, 'flip_v': True}
    if transform:
        data['transform'] = transform
    data.update(textures={'texture': 'academy:block/' + texture, 'particle': 'academy:block/' + texture},
                render_type='minecraft:cutout')
    return json.dumps(data, indent=2) + '\n'


def outputs(legacy):
    files = {ROOT / ('windgen_%s.obj' % part): obj((legacy / ('windgen_%s.obj' % part)).read_text(encoding='latin-1'))
             for part in PARTS}
    files[ROOT / (MATERIAL + '.mtl')] = MTL
    block = ROOT / 'block'
    files[block / 'windgen_base_render.json'] = model('base', 'windgen_base')
    # RenderWindGenBase: the same model with textures/models/windgen_base_disabled.png while the structure is incomplete.
    files[block / 'windgen_base_disabled_render.json'] = model('base', 'windgen_base_disable')
    files[block / 'windgen_main_render.json'] = model('main', 'windgen_main')
    files[block / 'windgen_fan_render.json'] = model('fan', 'windgen_fan')
    files[block / 'windgen_pillar.json'] = model('pillar', 'windgen_pillar', {'translation': [0.5, 0, 0.5]})
    return files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--legacy', required=True, type=Path, help="the original's assets/academy/models directory")
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    stale = []
    for path, text in outputs(args.legacy).items():
        if args.check:
            if not path.exists() or path.read_text(encoding='utf-8').replace('\r\n', '\n') != text:
                stale.append(path)
        else:
            path.write_text(text, encoding='utf-8', newline='\n')
    if stale:
        sys.exit('Stale wind models: ' + ', '.join(str(p) for p in stale))


if __name__ == '__main__':
    main()
