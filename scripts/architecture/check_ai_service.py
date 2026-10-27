"""Reject AI business leakage into core or direct access to other service models."""

from pathlib import Path
import re, sys, zipfile

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "apps/pms-app"
AI = ROOT / "services/pms-ai-service"
errors = []
for file in (AI / "src/main/java").rglob("*.java"):
    source = file.read_text(encoding="utf-8")
    for owner in re.findall(r"import (?:static )?com\.hongjie\.pms\.modules\.([a-z]+)\.", source):
        if owner != "ai":
            errors.append(f"{file.relative_to(ROOT)} imports {owner} business code")
if (CORE / "src/main/java/com/hongjie/pms/modules/ai").exists():
    if any((CORE / "src/main/java/com/hongjie/pms/modules/ai").rglob("*.java")):
        errors.append("AI implementation remains under core")
if "langchain4j" in (CORE / "pom.xml").read_text(encoding="utf-8"):
    errors.append("Core still depends on LangChain4j")
jar = CORE / "target/PMS-0.0.1-SNAPSHOT.jar"
if jar.exists():
    with zipfile.ZipFile(jar) as z:
        if any(n.startswith("BOOT-INF/classes/com/hongjie/pms/modules/ai/") for n in z.namelist()):
            errors.append("Core jar retains AI implementation classes")
        if any(
            n.startswith("BOOT-INF/lib/langchain4j") or "onnxruntime" in n for n in z.namelist()
        ):
            errors.append("Core jar retains model/ONNX runtime")
for file in [
    AI / "pom.xml",
    ROOT / "components/service-runtime/pom.xml",
    ROOT / "contracts/ai-contracts/pom.xml",
]:
    if re.search(r"<artifactId>\s*PMS\s*</artifactId>", file.read_text(encoding="utf-8")):
        errors.append(f"{file.relative_to(ROOT)} depends on the core application")
if errors:
    print("\n".join(errors))
    sys.exit(1)
print(
    "AI ownership guard passed: standalone service, no foreign business imports or core model runtime"
)
