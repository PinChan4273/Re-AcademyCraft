"""風力発電機の本体のレシピ: 原作のdefault.recipeから生成する（MohistMC版のデータパックとは比較のためだけに照合した）。

原作: LambdaInnovation/AcademyCraft@7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9.
MohistMC版: MohistMC/AcademyCraft@1b83239e6aa1976a73cb35b6142ae00c41c28e9c.
ここで生成するのはレシピとレシピ本のデータだけで、絵とモデルには触れない。
"""
import argparse
import json
from pathlib import Path

RES = Path(__file__).resolve().parents[1] / "src/main/resources/data/academy"
RECIPES = {
    "windgen_base": (["I", "M", "C"], {
        "I": "academy:constraint_ingot", "M": "academy:machine_frame",
        "C": "academy:energy_convert_component"}, "academy:constraint_ingot"),
    "windgen_pillar": (["B", "R", "B"], {
        "B": "minecraft:iron_bars", "R": "minecraft:redstone"}, "minecraft:iron_bars"),
    "windgen_main": ([" M ", "PCP", " M "], {
        "M": "academy:machine_frame", "P": "academy:constraint_plate",
        "C": "academy:energy_convert_component"}, "academy:machine_frame"),
}


def generated_data():
    for name, (pattern, ingredients, unlock) in RECIPES.items():
        recipe_id = "academy:" + name
        yield f"recipes/{name}.json", {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {key: {"item": item} for key, item in ingredients.items()},
            "result": {"item": recipe_id, "count": 1},
        }
        yield f"advancements/recipes/{name}.json", {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_material": {"trigger": "minecraft:inventory_changed",
                                 "conditions": {"items": [{"items": [unlock]}]}},
                "has_recipe": {"trigger": "minecraft:recipe_unlocked",
                               "conditions": {"recipe": recipe_id}},
            },
            "requirements": [["has_material", "has_recipe"]],
            "rewards": {"recipes": [recipe_id]},
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check committed data without writing")
    args = parser.parse_args()
    mismatches = []
    for path, data in generated_data():
        target = RES / path
        expected = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
        if args.check:
            if not target.is_file() or target.read_text(encoding="utf-8") != expected:
                mismatches.append(path)
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(expected, encoding="utf-8")
    if mismatches:
        parser.exit(1, "Wind recipe data differs: " + ", ".join(mismatches) + "\n")
    print("Wind body recipe/book data: 6 files " + ("verified" if args.check else "generated"))


if __name__ == "__main__":
    main()
