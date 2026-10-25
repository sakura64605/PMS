"""Report module imports and reject new dependencies on another module's internals.

Run: python scripts/architecture/check_module_dependencies.py
Existing dependencies are explicit migration debt, not proof of independent services.
"""
from pathlib import Path
import argparse,json,re,sys
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'src/main/java'
BASELINE=ROOT/'docs/architecture/module-internal-dependencies.json'
PREFIX='com.hongjie.pms.modules.'

def inventory():
    edges={};internals=[]
    for file in sorted(SOURCE.rglob('*.java')):
        text=file.read_text(encoding='utf-8')
        declaration=re.search(r'^package ([\w.]+);',text,re.M)
        if not declaration or not declaration[1].startswith(PREFIX):continue
        owner=declaration[1][len(PREFIX):].split('.')[0]
        imports=re.findall(r'^import (?:static )?(com\.hongjie\.pms\.modules\.[\w.*]+);',text,re.M)
        # Also catch fully qualified references in code; comments are excluded.
        code=re.sub(r'/\*.*?\*/|//[^\n]*','',text,flags=re.S)
        imports+=re.findall(r'com\.hongjie\.pms\.modules\.[A-Za-z0-9_.$]+',code)
        for target in sorted(set(imports)):
            other=target[len(PREFIX):].split('.')[0]
            if owner==other:continue
            public='.api.' in target
            key=(owner,other)
            edges.setdefault(key,{'api':set(),'internal':set()})['api' if public else 'internal'].add(target)
            if not public:internals.append({'source':file.relative_to(SOURCE).as_posix(),'target':target})
    return edges,sorted(internals,key=lambda row:(row['source'],row['target']))

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--update-baseline',action='store_true');args=parser.parse_args()
    edges,current=inventory()
    if args.update_baseline:BASELINE.write_text(json.dumps(current,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if not BASELINE.exists():raise SystemExit('Dependency baseline missing')
    known={(r['source'],r['target']) for r in json.loads(BASELINE.read_text(encoding='utf-8'))}
    added=[row for row in current if (row['source'],row['target']) not in known]
    for (source,target),values in sorted(edges.items()):
        print(f"{source} -> {target}: API={len(values['api'])}, internal={len(values['internal'])}")
    print(f"Existing internal import/reference entries: {len(current)}; new violations: {len(added)}")
    if added:
        for row in added:print(row)
        return 1
    return 0

if __name__=='__main__':sys.exit(main())
