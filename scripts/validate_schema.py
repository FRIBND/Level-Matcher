"""
Schema Validator for Level Matcher Datasets
===========================================

# High-level Structure and Workflow Explanation:
======================================

Workflow Diagram:

  [--schema_dir: ENSDF JSON Schema 1.0.0 files]      [--json_dir: parser output *.json]
            |                                                   |
            v                                                   v
  [Registry: every schema file keyed by its $id]      [Collect files, sorted by name]
            |                                                   |
            v                                                   |
  [Wrapper schema: levelsTable -> levels-table.json,            |
   gammasTable -> gammas-table.json]                            |
            |                                                   |
            +---------------------------+-----------------------+
                                        |
                                        v
                      [Validate each file, collect every violation]
                                        |
                                        v
                 [Print one line per violation + summary; exit code 0 = valid, 1 = invalid]

# Technical Steps:
# 1. Registry: load every .json file under --schema_dir; the schemas reference each other by name (for example level.json).
# 2. Wrapper schema: validate only the two tables the parser writes (levelsTable required, gammasTable optional).
# 3. Validation: Draft 2020-12 validator; all violations are listed, not only the first.
# 4. Report: file name, location inside the file, and message; exit code 1 if any violation exists.
#
# Usage:
#   python scripts/validate_schema.py --json_dir D:\\X\\ND\\ENSDF\\A34\\S34\\json
#   (defaults: --json_dir data/json, --schema_dir data/ensdf_schema_1.0.0, relative to the repository root)
#
# Limitation: the schema checks structure and types, not physics (for example, whether a normalized intensity is correct).
"""

import argparse
import glob
import json
import os
import sys

from jsonschema import Draft202012Validator
from referencing import Registry, Resource

repository_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

argument_parser = argparse.ArgumentParser(description="Validate parser output JSON files against ENSDF JSON Schema 1.0.0.")
argument_parser.add_argument("--json_dir", default=os.path.join(repository_root, "data", "json"),
                             help="Directory with the JSON files to validate (default: data/json)")
argument_parser.add_argument("--schema_dir", default=os.path.join(repository_root, "data", "ensdf_schema_1.0.0"),
                             help="Directory with the schema files (default: data/ensdf_schema_1.0.0)")
arguments = argument_parser.parse_args()

# 1. Registry of all schema files, keyed by their $id
registry = Registry()
for schema_path in glob.glob(os.path.join(arguments.schema_dir, "**", "*.json"), recursive=True):
    with open(schema_path, "r", encoding="utf-8") as schema_file:
        schema_contents = json.load(schema_file)
    registry = registry.with_resource(schema_contents["$id"], Resource.from_contents(schema_contents))

# 2. Wrapper schema for the two tables written by Dataset_Parser.py
wrapper_schema = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "properties": {
        "levelsTable": {"$ref": "levels-table.json"},
        "gammasTable": {"$ref": "gammas-table.json"},
    },
    "required": ["levelsTable"],
}
validator = Draft202012Validator(wrapper_schema, registry=registry)

# 3. Validate every file and collect all violations
json_paths = sorted(path for path in glob.glob(os.path.join(arguments.json_dir, "*.json"))
                    if os.path.basename(path) != "dataset_index.json")  # index maps letters to files; it is not a dataset
if not json_paths:
    print(f"No .json files found in '{arguments.json_dir}'.")
    sys.exit(1)

total_violations = 0
for json_path in json_paths:
    with open(json_path, "r", encoding="utf-8") as json_file:
        dataset = json.load(json_file)
    for violation in validator.iter_errors(dataset):
        total_violations += 1
        location = "/".join(str(part) for part in violation.absolute_path)
        print(f"{os.path.basename(json_path)} [{location}]: {violation.message[:200]}")

# 4. Summary and exit code
print(f"Files checked: {len(json_paths)}; violations: {total_violations}")
sys.exit(1 if total_violations else 0)
