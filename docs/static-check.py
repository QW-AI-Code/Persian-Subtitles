#!/usr/bin/env python3
"""Fast, dependency-free checks for the Persian Subtitles repository.

This deliberately checks source/configuration invariants only. Build outputs are
validated separately after assembleRelease, because a clean checkout does not
contain APKs.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def require(path: str) -> Path:
    value = ROOT / path
    if not value.exists():
        errors.append(f"missing: {path}")
    return value


def contains(path: Path, needle: str, label: str) -> None:
    if path.exists() and needle not in path.read_text(encoding="utf-8"):
        errors.append(f"{label}: {needle!r} not found")

# Core build and release files.
for path in (
    "gradlew",
    "settings.gradle.kts",
    "build.gradle.kts",
    "app/build.gradle.kts",
    ".github/workflows/android_build.yml",
    "app/src/main/AndroidManifest.xml",
):
    require(path)

wrapper = ROOT / "gradlew"
if wrapper.exists() and not (wrapper.stat().st_mode & 0o111):
    errors.append("gradlew is not executable")

build = ROOT / "app/build.gradle.kts"
if build.exists():
    text = build.read_text(encoding="utf-8")
    if 'versionName = appVersionName' not in text:
        errors.append("versionName is not wired to appVersionName")
    match = re.search(r'val appVersionName: String = .*?\?:?\s*"([^"]+)"', text)
    if not match:
        errors.append("appVersionName default could not be read")
    elif match.group(1) != "1.0.0":
        errors.append(f"expected version 1.0.0, found {match.group(1)}")
    if 'resourceConfigurations += setOf("fa")' not in text:
        errors.append("Persian-only resource configuration is missing")
    if 'isUniversalApk = true' not in text:
        errors.append("universal APK split is missing")

strings = ROOT / "app/src/main/res/values/strings.xml"
if strings.exists():
    try:
        ET.parse(strings)
    except ET.ParseError as exc:
        errors.append(f"strings.xml is invalid: {exc}")

manifest = ROOT / "app/src/main/AndroidManifest.xml"
if manifest.exists():
    try:
        ET.parse(manifest)
    except ET.ParseError as exc:
        errors.append(f"AndroidManifest.xml is invalid: {exc}")

workflow = ROOT / ".github/workflows/android_build.yml"
if workflow.exists():
    workflow_text = workflow.read_text(encoding="utf-8")
    for needle, label in (
        ("contents: write", "release permission"),
        ("chmod +x ./gradlew", "wrapper permission repair"),
        ("testDebugUnitTest", "unit-test step"),
        ("assembleRelease", "release build step"),
        ("SHA256SUMS.txt", "checksum generation"),
    ):
        if needle not in workflow_text:
            errors.append(f"workflow missing {label}")

# XML resource names must be unique within the same values file.
for xml in (ROOT / "app/src/main/res").rglob("*.xml"):
    try:
        root = ET.parse(xml).getroot()
    except ET.ParseError:
        continue
    names: set[str] = set()
    for element in root:
        name = element.attrib.get("name")
        if name and name in names:
            errors.append(f"duplicate resource {name!r} in {xml.relative_to(ROOT)}")
        if name:
            names.add(name)

if errors:
    print("❌ static check failed")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("✅ no problems found")
