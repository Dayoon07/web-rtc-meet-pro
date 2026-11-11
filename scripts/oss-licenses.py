"""오픈소스 라이선스 고지 목록 만들기 (배포되는 것만: 웹 화면 번들, Android·Windows 앱).
서버·사이드카는 배포하지 않으므로 빠진다 (AGPL 이 있으면 따로 경고).

사용 (저장소 루트에서):
  python scripts/oss-licenses.py
→ apps/client/public/oss-licenses.json  (웹 /licenses 화면이 읽는다)

- 웹: npm ls --omit=dev 로 실제 번들에 들어가는 패키지를 모으고 node_modules/*/package.json 의 license 를 읽는다.
- 앱: gradle 의 desktopRuntimeClasspath · androidApp releaseRuntimeClasspath 를 모으고, gradle 캐시의 POM 에서 <licenses> 를 읽는다.
- GPL·AGPL·LGPL 같은 copyleft 는 copyleft: true 로 표시하고 화면 위에 따로 보여 준다 (검토 필요).
"""
import json, os, re, subprocess, sys, glob
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KNOWN = {
    'androidx.': 'Apache-2.0', 'co.touchlab:stately': 'Apache-2.0', 'com.google.guava:': 'Apache-2.0',
    'org.jetbrains:annotations': 'Apache-2.0', 'org.jspecify:': 'Apache-2.0',
}
COPYLEFT = re.compile(r'\b(A?GPL|LGPL|MPL|EPL|CDDL|EUPL|SSPL)', re.I)


def npm_packages(workspace):
    out = subprocess.run('npm ls --omit=dev --all --json -w ' + workspace, cwd=ROOT, capture_output=True, text=True, shell=True, encoding='utf-8').stdout
    tree = json.loads(out or '{}')
    found = {}

    def walk(deps):
        for name, info in (deps or {}).items():
            ver = info.get('version')
            if ver and (name, ver) not in found:
                found[(name, ver)] = True
                walk(info.get('dependencies'))

    for ws in (tree.get('dependencies') or {}).values():
        walk(ws.get('dependencies'))
    items = []
    for (name, ver) in sorted(found):
        pkg = None
        for base in (os.path.join(ROOT, 'node_modules'), os.path.join(ROOT, workspace, 'node_modules')):
            p = os.path.join(base, *name.split('/'), 'package.json')
            if os.path.exists(p):
                pkg = json.load(open(p, encoding='utf-8'))
                break
        # 설치되지 않은 것(다른 OS 용 선택 패키지)과 빌드 때만 쓰는 TypeScript 는 번들에 들어가지 않는다
        if pkg is None or name == 'typescript' or name.startswith('@typescript/'):
            continue
        lic = (pkg or {}).get('license') or ''
        if isinstance(lic, dict):
            lic = lic.get('type', '')
        if not lic and pkg and isinstance(pkg.get('licenses'), list):
            lic = ' OR '.join(l.get('type', '') for l in pkg['licenses'] if isinstance(l, dict))
        repo = (pkg or {}).get('repository')
        url = repo.get('url') if isinstance(repo, dict) else repo
        url = (url or (pkg or {}).get('homepage') or '').replace('git+', '').replace('git://', 'https://')
        items.append({'name': name, 'version': ver, 'license': lic or 'UNKNOWN', 'url': url})
    return items


def gradle_artifacts():
    gradlew = os.path.join(ROOT, 'apps', 'kmp', 'gradlew.bat' if os.name == 'nt' else 'gradlew')
    tasks = [(':composeApp:dependencies', 'desktopRuntimeClasspath'), (':androidApp:dependencies', 'releaseRuntimeClasspath')]
    coords = set()
    for task, conf in tasks:
        r = subprocess.run([gradlew, '-q', task, '--configuration', conf], cwd=os.path.join(ROOT, 'apps', 'kmp'), capture_output=True, text=True, encoding='utf-8', errors='replace')
        for m in re.finditer(r'[+\\]--- ([\w.\-]+):([\w.\-]+):(?:[\w.\-]+ -> )?([\w.\-]+)', r.stdout):
            g, a, v = m.groups()
            if g.startswith('project') or g == 'com.webrtcmeetpro':
                continue
            coords.add((g, a, v))
    cache = os.path.join(os.path.expanduser('~'), '.gradle', 'caches', 'modules-2', 'files-2.1')
    items = []
    for g, a, v in sorted(coords):
        lic, url = '', ''
        poms = glob.glob(os.path.join(cache, g, a, v, '*', f'{a}-{v}.pom'))
        seen = set()
        while poms and not lic:
            pom = poms.pop()
            if pom in seen:
                continue
            seen.add(pom)
            try:
                t = ET.parse(pom).getroot()
            except ET.ParseError:
                continue
            ns = {'m': t.tag[t.tag.find('{') + 1:t.tag.find('}')]} if t.tag.startswith('{') else {'m': ''}
            q = (lambda path: t.find(path.replace('/', '/m:').replace('./', './m:') if ns['m'] else path, ns))
            names = [e.text for e in t.iter() if e.tag.endswith('license') is False and e.tag.endswith('}name') and e.text and 'licenses' in '']
            for lic_el in t.iter():
                if lic_el.tag.endswith('}license') or lic_el.tag == 'license':
                    for child in lic_el:
                        if child.tag.endswith('name') and child.text:
                            lic = (lic + ' OR ' if lic else '') + child.text.strip()
            for e in t.iter():
                if (e.tag.endswith('}url') or e.tag == 'url') and e.text and not url:
                    url = e.text.strip()
            # 부모 POM 에 라이선스가 있는 경우
            if not lic:
                parent = next((e for e in t if e.tag.endswith('parent')), None)
                if parent is not None:
                    vals = {c.tag.split('}')[-1]: (c.text or '').strip() for c in parent}
                    poms += glob.glob(os.path.join(cache, vals.get('groupId', ''), vals.get('artifactId', ''), vals.get('version', ''), '*', '*.pom'))
        if not lic:
            # POM 에 라이선스가 없는 것 (Gradle 모듈 정보에만 있음). 공식 저장소에 Apache-2.0 으로 공개돼 있다
            for prefix, known in KNOWN.items():
                if f'{g}:{a}'.startswith(prefix):
                    lic = known
                    break
        items.append({'name': f'{g}:{a}', 'version': v, 'license': lic or 'UNKNOWN', 'url': url})
    return items


def mark(items):
    for i in items:
        i['copyleft'] = bool(COPYLEFT.search(i['license'])) and 'Exception' not in i['license'] and not re.search(r'\bOR\b.*\b(MIT|Apache|BSD)', i['license'])
    return items


web = mark(npm_packages('apps/client'))
app = mark(gradle_artifacts()) if '--no-app' not in sys.argv else []
out = os.path.join(ROOT, 'apps', 'client', 'public', 'oss-licenses.json')
json.dump({'web': web, 'app': app}, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(f'web {len(web)} · app {len(app)} → {out}')
for label, items in (('web', web), ('app', app)):
    flagged = [f"{i['name']}@{i['version']} ({i['license']})" for i in items if i['copyleft'] or i['license'] == 'UNKNOWN']
    if flagged:
        print(f'[{label}] 검토 필요 {len(flagged)}개:')
        for f in flagged:
            print('  -', f)
