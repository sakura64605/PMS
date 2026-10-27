"""Format tracked source files without touching build output, backups or local secrets.
Prerequisites and pinned tool versions: docs/development/code-formatting.md.
"""

from __future__ import annotations
import argparse
import ast
import json
import os
from pathlib import Path
import subprocess
import sys
from xml.dom import minidom
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "target/format-tools"
EXCLUDED = {
    "PMS.swagger.json",
    "docs/architecture/module-internal-dependencies.json",
    # Mixed SQL/XML content and CDATA should not be rewritten by a generic XML pretty-printer.
    "apps/pms-app/src/main/resources/mapper/community/feed/UserInboxMapper.xml",
}


def run(command, env=None):
    subprocess.run(command, cwd=ROOT, env=env, check=True)


def tracked():
    paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode("utf-8").split("\0")
    paths += ["scripts/format_code.py", ".prettierrc.json"]
    return sorted(
        {
            p
            for p in paths
            if p
            and (ROOT / p).is_file()
            and p not in EXCLUDED
            and not p.endswith("package-lock.json")
        }
    )


def xml_value(element):
    return (
        element.tag,
        sorted(element.attrib.items()),
        (element.text or "").strip(),
        [(xml_value(child), (child.tail or "").strip()) for child in element],
    )


def structure(path):
    text = path.read_text(encoding="utf-8-sig")
    if path.suffix == ".py":
        tree = ast.parse(text, type_comments=True)
        for node in ast.walk(tree):
            if isinstance(node, ast.TypeIgnore):
                node.lineno = 0
        return ast.dump(tree, include_attributes=False)
    if path.suffix == ".json":
        return json.dumps(json.loads(text), sort_keys=True, ensure_ascii=False)
    if path.suffix in {".yaml", ".yml"}:
        import yaml

        return repr(yaml.safe_load(text))
    if path.suffix == ".xml":
        return repr(xml_value(ElementTree.fromstring(text)))
    return None


def clean_xml(node):
    # Only remove indentation nodes; SQL and other nonempty text is preserved verbatim.
    for child in list(node.childNodes):
        if child.nodeType == child.TEXT_NODE and not child.data.strip():
            node.removeChild(child)
            child.unlink()
        elif child.hasChildNodes():
            clean_xml(child)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--scope", choices=["all", "frontend", "backend"], default="all")
    parser.add_argument(
        "--java-formatter", type=Path, default=TOOLS / "google-java-format-1.22.0-all-deps.jar"
    )
    args = parser.parse_args()
    paths = tracked()
    if args.scope == "frontend":
        paths = [p for p in paths if p.startswith("PMS-react/") or p == ".prettierrc.json"]
    elif args.scope == "backend":
        paths = [p for p in paths if not p.startswith("PMS-react/")]
    text_suffixes = {
        ".java",
        ".py",
        ".xml",
        ".yaml",
        ".yml",
        ".json",
        ".ts",
        ".tsx",
        ".js",
        ".mjs",
        ".css",
        ".html",
    }
    for name in paths:
        path = ROOT / name
        if path.suffix in text_suffixes and b"\r\n" in path.read_bytes():
            if args.check:
                raise SystemExit("Expected LF line endings: " + name)
            path.write_bytes(path.read_bytes().replace(b"\r\n", b"\n"))
    semantic_paths = [
        p for p in paths if Path(p).suffix in {".py", ".json", ".yaml", ".yml", ".xml"}
    ]
    baseline = {p: structure(ROOT / p) for p in semantic_paths}
    java = [p for p in paths if p.endswith(".java")]
    if java:
        if not args.java_formatter.is_file():
            parser.error("Java formatter missing; see docs/development/code-formatting.md")
        TOOLS.mkdir(parents=True, exist_ok=True)
        listing = TOOLS / "java-files.txt"
        listing.write_text("\n".join(java) + "\n", encoding="utf-8")
        run(
            [
                "java",
                "-jar",
                str(args.java_formatter),
                "--aosp",
                "--skip-sorting-imports",
                "--skip-removing-unused-imports",
                "--skip-reflowing-long-strings",
                "--skip-javadoc-formatting",
                *(["--dry-run", "--set-exit-if-changed"] if args.check else ["--replace"]),
                "@" + str(listing),
            ]
        )
    python = [p for p in paths if p.endswith(".py")]
    if python:
        env = dict(os.environ)
        if (TOOLS / "python").is_dir():
            env["PYTHONPATH"] = str(TOOLS / "python") + os.pathsep + env.get("PYTHONPATH", "")
        run(
            [
                sys.executable,
                "-m",
                "black",
                "--workers",
                "1",
                "--line-length",
                "100",
                *(["--check"] if args.check else []),
                *python,
            ],
            env,
        )
    prettier = [
        p
        for p in paths
        if Path(p).suffix
        in {".ts", ".tsx", ".mjs", ".js", ".css", ".html", ".json", ".yaml", ".yml"}
    ]
    if prettier:
        run(
            [
                "node",
                str(ROOT / "PMS-react/node_modules/prettier/bin/prettier.cjs"),
                "--check" if args.check else "--write",
                *prettier,
            ]
        )
    for p in [p for p in paths if p.endswith(".xml")]:
        path = ROOT / p
        dom = minidom.parseString(path.read_bytes())
        clean_xml(dom)
        formatted = dom.toprettyxml(indent="    ", encoding="UTF-8").decode("utf-8")
        if path.read_text(encoding="utf-8") != formatted:
            if args.check:
                raise SystemExit("XML formatting differs: " + p)
            path.write_text(formatted, encoding="utf-8", newline="\n")
    changed = [p for p, before in baseline.items() if structure(ROOT / p) != before]
    if changed:
        raise SystemExit("Unexpected structural change: " + ", ".join(changed))
    print(
        f"Format {'check' if args.check else 'apply'} complete; Python/configuration structure preserved for {len(baseline)} files"
    )


if __name__ == "__main__":
    main()
