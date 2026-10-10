#!/usr/bin/env python3
"""金属成形機のレシピJSONを生成する。値は原作のMFIFRecipesとVanillaCategoriesの登録に従う。
JSONの形はこのmod独自: operation（モード）、ingredient（材料）、amount（必要な個数）、result（出力）。"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/data/academy/recipes/metal_forming"


def recipe(name, operation, ingredient, result, amount=1, conditions=None):
    data = {"type": "academy:metal_forming"}
    if conditions:
        data["conditions"] = conditions
    data.update(operation=operation, ingredient=ingredient, amount=amount, result=result)
    (ROOT / (name + ".json")).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def item(name, count=1):
    return {"item": name if ":" in name else "academy:" + name, "count": count}


def tag_present(tag):
    return {"type": "forge:not", "value": {"type": "forge:tag_empty", "tag": tag}}


if ROOT.exists():
    shutil.rmtree(ROOT)
ROOT.mkdir(parents=True)

# MFIFRecipes.init: mfr.add(入力, 出力, モード)
for name, operation, source, target, count in [
    ("incise_wafer", "incise", "imag_silicon_ingot", "wafer", 2),
    ("incise_piece", "incise", "wafer", "imag_silicon_piece", 4),
    ("etch_calc_chip", "etch", "data_chip", "calc_chip", 1),
    ("plate_constraint_plate", "plate", "constraint_ingot", "constraint_plate", 1),
    ("refine_imag_silicon_ingot", "refine", "imagsil_ore", "imag_silicon_ingot", 4),
    ("refine_constraint_ingot", "refine", "constraint_metal", "constraint_ingot", 2),
    ("refine_reso_crystal", "refine", "reso_ore", "reso_crystal", 3),
    ("refine_crystal_low", "refine", "crystal_ore", "crystal_low", 4),
]:
    recipe(name, operation, {"item": "academy:" + source}, item(target, count))
# 原作は鉄インゴット1個。1.20ではforgeの鉄インゴットのタグで受ける。
recipe("plate_reinforced_iron", "plate", {"tag": "forge:ingots/iron"}, item("reinforced_iron_plate"))

# VanillaCategories.init（原作のVanillaCategories.java 38〜44行）
recipe("incise_needle", "incise", {"item": "academy:reinforced_iron_plate"}, item("needle", 6))
recipe("incise_needle_from_rail", "incise", {"item": "minecraft:rail"}, item("needle", 2))
recipe("plate_coin", "plate", {"item": "academy:reinforced_iron_plate"}, item("coin", 3), amount=2)
recipe("etch_silbarn", "etch", {"item": "academy:wafer"}, item("silbarn"))

# MFIFRecipes.addOreDictRefineRecipe(鉱石, 出力): 原作の鉱石辞書の名前は、forgeの鉱石のタグで受ける。
for ore, output, count in [("gold", "gold_ingot", 2), ("iron", "iron_ingot", 2), ("emerald", "emerald", 2),
                           ("quartz", "quartz", 2), ("diamond", "diamond", 2), ("redstone", "redstone_block", 1),
                           ("lapis", "lapis_lazuli", 12), ("coal", "coal", 2), ("copper", "copper_ingot", 2)]:
    recipe("refine_" + ore, "refine", {"tag": "forge:ores/" + ore}, item("minecraft:" + output, count))

# MFIFRecipes.addDefaultOreDictRefineRecipe(金属): 鉱石とインゴットを他modが用意したときだけ。出力はインゴットのタグの最初のもので、
# 原作はかまどの結果の2倍（インゴット1個なら2個）。
for metal in ["tin", "silver", "lead", "aluminum", "nickel", "platinum", "iridium", "mithril"]:
    ore, ingot = "forge:ores/" + metal, "forge:ingots/" + metal
    recipe("refine_" + metal, "refine", {"tag": ore}, {"tag": ingot, "count": 2},
           conditions=[tag_present(ore), tag_present(ingot)])
