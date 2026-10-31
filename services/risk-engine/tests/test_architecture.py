"""The dependency rule is executable, not just a folder naming convention."""

import ast
from pathlib import Path
import sys

import pytest


ROOT = Path(__file__).resolve().parents[1] / "risk_engine"


@pytest.mark.parametrize("layer", ["domain", "application"])
def test_core_imports_only_standard_library_and_allowed_inner_layers(layer):
    for source in (ROOT / layer).rglob("*.py"):
        tree = ast.parse(source.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imports = [item.name for item in node.names]
            elif isinstance(node, ast.ImportFrom):
                assert node.level == 0, f"Use explicit absolute imports in {source}"
                imports = [node.module or ""]
            else:
                continue
            for imported in imports:
                root = imported.split(".", 1)[0]
                allowed = root in sys.stdlib_module_names or imported.startswith("risk_engine.domain")
                if layer == "application":
                    allowed |= imported.startswith("risk_engine.application")
                assert allowed, f"{source}: {layer} must not import {imported}"
