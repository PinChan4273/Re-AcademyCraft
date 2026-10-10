"""原作の風車のレシピと、固定した原作のアイコンを参照するアイテムモデルを生成する。"""
import json
from pathlib import Path

RES = Path(__file__).resolve().parents[1] / 'src/main/resources'


def write(path, value):
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


for name in ('windgen_base', 'windgen_main', 'windgen_pillar', 'windgen_fan'):
    write(f'assets/academy/models/item/{name}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'academy:item/{name}'}})
write('data/academy/recipes/windgen_fan.json', {
    'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': [' P ', 'PBP', ' P '],
    'key': {'P': {'tag': 'forge:plates/iron'}, 'B': {'item': 'minecraft:iron_bars'}},
    'result': {'item': 'academy:windgen_fan', 'count': 1}})
write('data/academy/advancements/recipes/windgen_fan.json', {
    'parent': 'minecraft:recipes/root',
    'criteria': {
        'has_material': {'trigger': 'minecraft:inventory_changed',
                         'conditions': {'items': [{'tag': 'forge:plates/iron'}]}},
        'has_recipe': {'trigger': 'minecraft:recipe_unlocked',
                       'conditions': {'recipe': 'academy:windgen_fan'}}},
    'requirements': [['has_material', 'has_recipe']], 'rewards': {'recipes': ['academy:windgen_fan']}})
for lang, name in [('en_us', 'Wind Generator - Fan'), ('ja_jp', '風力発電機 - ファン')]:
    path = f'assets/academy/lang/{lang}.json'
    data = json.loads((RES / path).read_text(encoding='utf-8'))
    data['item.academy.windgen_fan'] = name
    write(path, data)
