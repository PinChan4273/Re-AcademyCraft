"""原作のdefault.recipe/ACBlocks/ACWorldGenから、1.20.1の素材データを決定的に生成する。
1.20.1のデータパックの形で書く（1.21のresult.idフィールドは使わない）。
アイテム・鉱石のテクスチャは固定した原作のもの。リポジトリのルートで実行する。
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]


def write(path, data):
    target = ROOT / 'src/main/resources' / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def tag(kind, name, values):
    path = f'data/{name.split(":")[0]}/tags/{kind}/{name.split(":")[1]}.json'
    target = ROOT / 'src/main/resources' / path
    old = json.loads(target.read_text(encoding='utf-8')) if target.exists() else {'replace': False, 'values': []}
    old['values'] = list(dict.fromkeys(old['values'] + values))
    write(path, old)


def ingredient(name):
    return {'item': name if ':' in name else 'academy:' + name}


def recipe(name, data, unlock):
    write(f'data/academy/recipes/{name}.json', data)
    write(f'data/academy/advancements/recipes/{name}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': [unlock]}]}},
            'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': 'academy:' + name}}},
        'requirements': [['has_material', 'has_the_recipe']], 'rewards': {'recipes': ['academy:' + name]}})


def shaped(name, result, pattern, key, count=1):
    recipe(name, {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
                 'key': {symbol: ingredient(item) for symbol, item in key.items()},
                 'result': {'item': 'academy:' + result, 'count': count}}, ingredient(next(iter(key.values())))['item'])


def shapeless(name, result, inputs, count=1):
    recipe(name, {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                 'ingredients': [ingredient(item) for item in inputs], 'result': {'item': 'academy:' + result, 'count': count}}, ingredient(inputs[0])['item'])


materials = {
    'constraint_ingot': ('Constraint Metal Ingot', '束能メタルインゴット', 'iron_ingot'),
    'constraint_plate': ('Constraint Metal Plate', '束能メタルプレート', 'iron_nugget'),
    'imag_silicon_ingot': ('Imag Silicon Ingot', '虚能シリコンインゴット', 'quartz'),
    'wafer': ('Imag Silicon Wafer', '虚能シリコンウェハー', 'paper'),
    'imag_silicon_piece': ('Imag Silicon Piece', '虚能シリコンピース', 'flint'),
    'crystal_low': ('Low-purity Imag Crystal', '低純度虚能クリスタル', 'amethyst_shard'),
    'reso_crystal': ('Resonant Crystal', '共振クリスタル', 'prismarine_crystals'),
    # Clockはフレームごとのテクスチャを持つモデルで、item/clock.pngの1枚の絵ではない。
    'brain_component': ('Brainwave Analyzer', '脳波分析装置', 'clock_00'),
    'info_component': ('Information Processor', '情報処理装置', 'repeater'),
    'energy_unit': ('Energy Unit', 'エネルギーユニット', 'echo_shard'),
    'energy_convert_component': ('Energy Converter', 'エネルギーコンバータ', 'comparator'),
}
ores = {
    'constraint_metal': ('Constraint Metal Ore', '束能メタル鉱石', 'iron_ore', 12, 8),
    'crystal_ore': ('Imag Crystal Ore', '虚能クリスタル鉱石', 'diamond_ore', 12, 12),
    'imagsil_ore': ('Imag Silicon Ore', '虚能シリコン鉱石', 'redstone_ore', 11, 8),
    'reso_ore': ('Resonant Crystal Ore', '共振クリスタル鉱石', 'emerald_ore', 9, 8),
}
for lang, index in [('en_us', 0), ('ja_jp', 1)]:
    path = f'assets/academy/lang/{lang}.json'
    data = json.loads((ROOT / 'src/main/resources' / path).read_text(encoding='utf-8'))
    for name, values in materials.items():
        data[f'item.academy.{name}'] = values[index]
    for name, values in ores.items():
        data[f'block.academy.{name}'] = values[index]
    write(path, data)
for name, values in materials.items():
    texture = 'energy_unit_empty' if name == 'energy_unit' else name
    model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'academy:item/' + texture}}
    if name == 'energy_unit':
        model['overrides'] = [
            {'predicate': {'academy:energy': .5}, 'model': 'academy:item/energy_unit_half'},
            {'predicate': {'academy:energy': 1}, 'model': 'academy:item/energy_unit_full'}]
    write(f'assets/academy/models/item/{name}.json', model)
for state in ('half', 'full'):
    write(f'assets/academy/models/item/energy_unit_{state}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': 'academy:item/energy_unit_' + state}})

for name in ('data_chip', 'calc_chip', 'reinforced_iron_plate', 'factor_electromaster'):
    write(f'assets/academy/models/item/{name}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': 'academy:item/' + name}})
# 原作ItemDeveloperは手と地面でdeveloper_portable.objを描き（TEISR）、充電量に応じた平たいアイコンはGUIでだけ使う。
# そのため各状態を、GUI用の平たいアイコンと、それ以外の独自描画に分ける。
def portable(texture, extra=None):
    model = {'loader': 'forge:separate_transforms', 'gui_light': 'front',
             'textures': {'particle': 'academy:item/' + texture},
             'base': {'parent': 'minecraft:builtin/entity', 'textures': {'particle': 'academy:item/' + texture}},
             'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'academy:item/' + texture}}}}
    if extra: model.update(extra)
    return model
write('assets/academy/models/item/developer_portable.json', portable('developer_portable_empty', {
    'overrides': [{'predicate': {'academy:energy': value}, 'model': 'academy:item/developer_portable_' + state}
                  for state, value in (('half', .5), ('full', 1))]}))
for state in ('half', 'full'):
    write(f'assets/academy/models/item/developer_portable_{state}.json', portable('developer_portable_' + state))

for name, (_, _, model, size, density) in ores.items():
    write(f'assets/academy/blockstates/{name}.json', {'variants': {'': {'model': 'academy:block/' + name}}})
    write(f'assets/academy/models/block/{name}.json', {
        'parent': 'minecraft:block/cube_all', 'textures': {'all': 'academy:block/' + name}})
    write(f'assets/academy/models/item/{name}.json', {'parent': 'academy:block/' + name})
    own = {'type': 'minecraft:item', 'name': 'academy:' + name}
    entry = own
    if name in ('crystal_ore', 'reso_ore'):
        # RandUtils.rangei(from,to)はtoを含まない: 結晶は1〜2、共振結晶はちょうど1。
        silk = dict(own, conditions=[{'condition': 'minecraft:match_tool', 'predicate': {'enchantments': [{'enchantment': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}])
        functions = ([{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}}] if name == 'crystal_ore' else [])
        functions += [{'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'}]
        normal = {'type': 'minecraft:item', 'name': 'academy:' + ('crystal_low' if name == 'crystal_ore' else 'reso_crystal'), 'functions': functions}
        entry = {'type': 'minecraft:alternatives', 'children': [silk, normal]}
    write(f'data/academy/loot_tables/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [entry], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(f'data/academy/worldgen/configured_feature/{name}.json', {
        'type': 'minecraft:ore', 'config': {'size': size, 'discard_chance_on_air_exposure': 0,
        'targets': [{'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'}, 'state': {'Name': 'academy:' + name}}]}})
    write(f'data/academy/worldgen/placed_feature/{name}.json', {'feature': 'academy:' + name, 'placement': [
        {'type': 'academy:ore_generation_enabled'},
        {'type': 'minecraft:count', 'count': density}, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': 0}, 'max_inclusive': {'absolute': 59}}},
        {'type': 'minecraft:biome'}]})
    tag('blocks', 'forge:ores/' + name, ['academy:' + name])
    tag('items', 'forge:ores/' + name, ['academy:' + name])

ore_ids = ['academy:' + name for name in ores]
tag('blocks', 'minecraft:mineable/pickaxe', ore_ids)
tag('blocks', 'minecraft:needs_stone_tool', ['academy:constraint_metal'])
# 原作blocks.confのmachine_frameはつるはしの採掘レベル1（石のつるはし以上）。
tag('blocks', 'minecraft:needs_stone_tool', ['academy:machine_frame'])
tag('blocks', 'minecraft:needs_iron_tool', [name for name in ore_ids if name != 'academy:constraint_metal'])
tag('blocks', 'forge:ores', ore_ids)
tag('items', 'forge:ores', ore_ids)
write('data/academy/forge/biome_modifier/ores.json', {'type': 'forge:add_features', 'biomes': '#minecraft:is_overworld', 'features': ore_ids, 'step': 'underground_ores'})
tag('items', 'forge:ingots/constraint', ['academy:constraint_ingot'])
tag('items', 'forge:plates/constraint', ['academy:constraint_plate'])

for source, result, xp in [('constraint_metal', 'constraint_ingot', .7), ('imagsil_ore', 'imag_silicon_ingot', .8), ('crystal_ore', 'crystal_low', .8)]:
    recipe(result + '_from_smelting', {'type': 'minecraft:smelting', 'category': 'misc', 'ingredient': ingredient(source), 'result': 'academy:' + result, 'experience': xp, 'cookingtime': 200}, 'academy:' + source)
shaped('constraint_plate', 'constraint_plate', ['III'], {'I': 'constraint_ingot'}, 2)
shapeless('wafer', 'wafer', ['imag_silicon_ingot'])
shapeless('imag_silicon_piece', 'imag_silicon_piece', ['wafer'], 2)
shaped('data_chip_silicon', 'data_chip', ['RRR', ' S '], {'R': 'minecraft:redstone', 'S': 'imag_silicon_piece'})
shapeless('calc_chip_resonance', 'calc_chip', ['data_chip', 'reso_crystal'])
shaped('info_component', 'info_component', ['G', 'D'], {'G': 'minecraft:glowstone_dust', 'D': 'data_chip'})
shaped('brain_component', 'brain_component', [' G ', 'RCR', ' G '], {'G': 'minecraft:gold_nugget', 'R': 'minecraft:redstone', 'C': 'calc_chip'})
shaped('energy_unit', 'energy_unit', [' P ', 'PCP', ' D '], {'P': 'constraint_plate', 'C': 'crystal_low', 'D': 'data_chip'})
shaped('energy_convert_component', 'energy_convert_component', ['C', 'E', 'R'], {'C': 'calc_chip', 'E': 'energy_unit', 'R': 'reso_crystal'})
shaped('developer_portable', 'developer_portable', ['DGC', 'BIE', 'PLP'], {
    'D': 'data_chip', 'G': 'minecraft:glass_pane', 'C': 'calc_chip', 'B': 'brain_component', 'I': 'info_component',
    'E': 'energy_convert_component', 'P': 'constraint_plate', 'L': 'crystal_low'})

# 原作の太陽光発電機のレシピ。
shaped('solar_gen', 'solar_gen', ['GGG', ' W ', 'EME'], {
    'G': 'minecraft:glass_pane', 'W': 'wafer', 'E': 'energy_convert_component', 'M': 'machine_frame'})
write('assets/academy/blockstates/solar_gen.json', {'variants': {
    'facing=' + face: {'model': 'academy:block/solar_gen', 'y': angle}
    for face, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
# 原作はブロックを見えなくし、solar.objをSolarGeneratorRendererで描く。この立方体は粒子のためだけにあり、
# アイテムは原作の平たいアイコン。どちらも原作のblocks/solar_genを使う。
write('assets/academy/models/block/solar_gen.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'academy:block/solar_gen'}})
write('assets/academy/models/item/solar_gen.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'academy:block/solar_gen'}})
write('data/academy/loot_tables/blocks/solar_gen.json', {'type': 'minecraft:block', 'pools': [{
    'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'academy:solar_gen'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
tag('blocks', 'minecraft:mineable/pickaxe', ['academy:solar_gen'])
tag('blocks', 'minecraft:needs_stone_tool', ['academy:solar_gen'])
for lang, title, stopped, weak, strong in [
    ('en_us', 'Solar Generator', 'Stopped', 'Rain: 0.6 IF/t', 'Clear: 3.0 IF/t'),
    ('ja_jp', '太陽光発電機', '発電停止', '雨天: 0.6 IF/t', '晴天: 3.0 IF/t')]:
    path = f'assets/academy/lang/{lang}.json'; data = json.loads((ROOT / 'src/main/resources' / path).read_text(encoding='utf-8'))
    data.update({'block.academy.solar_gen': title, 'container.academy.solar_gen': title,
                 'academy.solar.status.0': stopped, 'academy.solar.status.1': weak, 'academy.solar.status.2': strong})
    write(path, data)
