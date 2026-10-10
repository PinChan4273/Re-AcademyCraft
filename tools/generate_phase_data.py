"""原作の物質ユニット・機械のレシピと、原作の容器の絵を生成する。ワールド上の液体の見た目は別途描画する。"""
import json
from pathlib import Path

RES = Path(__file__).resolve().parents[1] / 'src/main/resources'


def write(path, value):
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


write('assets/academy/models/item/matter_unit.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'academy:item/matter_unit'}})
write('assets/academy/models/item/matter_unit_phase_liquid.json', {
    'parent': 'minecraft:item/generated', 'textures': {'layer0': 'academy:item/matter_unit_phase_liquid_0'},
    'overrides': [{'predicate': {'academy:frame': i / 4}, 'model': f'academy:item/matter_unit_phase_liquid_{i}'} for i in range(1, 4)]})
for i in range(1, 4):
    write(f'assets/academy/models/item/matter_unit_phase_liquid_{i}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'academy:item/matter_unit_phase_liquid_{i}'}})
write('assets/academy/blockstates/phase_liquid.json', {'variants': {'': {'model': 'academy:block/phase_liquid'}}})
# 原作ACFluidsは液体の全面にacademy:blocks/blackを使う。粒子もそれに従う。
write('assets/academy/models/block/phase_liquid.json', {'textures': {'particle': 'academy:block/black'}})
for lang, names in [('en_us', ['Empty Unit', 'Imag Phase Liquid Unit', 'Imag Phase Liquid']), ('ja_jp', ['エンプティユニット', '虚相位液体ユニット', '虚相位液体'])]:
    path = f'assets/academy/lang/{lang}.json'
    data = json.loads((RES / path).read_text(encoding='utf-8'))
    data.update(dict(zip(['item.academy.matter_unit', 'item.academy.matter_unit_phase_liquid', 'block.academy.phase_liquid'], names)))
    write(path, data)
recipes = {
    'matter_unit': ([' P ', 'PGP', ' P '], {'P': {'item': 'academy:constraint_plate'}, 'G': {'item': 'minecraft:glass'}}, 4, 'academy:constraint_plate'),
    'metal_former': ([' S ', 'CFC', 'PMP'], {'S': {'item': 'minecraft:shears'}, 'C': {'item': 'academy:calc_chip'}, 'F': {'item': 'academy:machine_frame'}, 'P': {'item': 'academy:constraint_plate'}, 'M': {'item': 'academy:matter_unit'}}, 1, 'academy:matter_unit'),
}
for name, (pattern, keys, count, trigger) in recipes.items():
    write(f'data/academy/recipes/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': keys, 'result': {'item': f'academy:{name}', 'count': count}})
    write(f'data/academy/advancements/recipes/{name}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': [trigger]}]}},
                     'has_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': f'academy:{name}'}}},
        'requirements': [['has_material', 'has_recipe']], 'rewards': {'recipes': [f'academy:{name}']}})
