#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_parity.py — Web ↔ Android settings model parity checker

Do cheezein independently parse karta hai:
  1. Mgboard_Web/index.html  → SG_PAGES / SG_BOOL_DEFAULTS / SG_ITEMS (+help/about)
  2. Mgboard/.../model/SettingsModel.kt (generated Kotlin)

Phir field-by-field compare karta hai: page order/labels/icons, item order/ids,
types, EN+HI labels, EN+HI summaries, search keywords, Gboard boolean defaults,
gate reason strings, slider ranges, action button labels.

Usage:
    python3 Mgboard/scripts/check_parity.py [path/to/Mgboard_Web]
Exit code 0 = parity OK.
"""
import os, re, sys, json, subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
WEB = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(ROOT), 'Mgboard_Web')
KT = os.path.join(ROOT, 'app', 'src', 'main', 'kotlin', 'com', 'mgboard', 'keyboard',
                  'model', 'SettingsModel.kt')

# ── 1. web model: generator ke parser ko hi reuse karte hain (single source of truth) ──
src = open(os.path.join(HERE, 'gen_settings_model.py'), encoding='utf-8').read()
ns = {'__file__': os.path.join(HERE, 'gen_settings_model.py')}
parse_only = src[:src.index('# ---------- pages ----------')]
exec(compile(parse_only, 'gen:header', 'exec'), ns)
for start, end in [('# ---------- pages ----------', '# ---------- bool defaults ----------'),
                   ('# ---------- bool defaults ----------', '# ---------- items ----------'),
                   ('# ---------- items ----------', "MODEL = {'pages'")]:
    exec(compile(src[src.index(start):src.index(end)], 'gen:' + start, 'exec'), ns)
pages, defaults, items = ns['pages'], ns['defaults'], ns['items']

# ── 2. Kotlin model parse ─────────────────────────────────────────────────────
kt = open(KT, encoding='utf-8').read()

def unesc(s):
    out = []; i = 0
    while i < len(s):
        c = s[i]
        if c == '\\' and i + 1 < len(s):
            n = s[i+1]
            out.append({'n':'\n','t':'\t','"':'"','\\':'\\','$':'$'}.get(n, n)); i += 2
        else:
            out.append(c); i += 1
    return ''.join(out)

def split_args(body):
    """Split top-level commas of a Kotlin call argument list."""
    out = []; cur = ''; depth = 0; in_s = False; i = 0
    while i < len(body):
        c = body[i]
        if in_s:
            cur += c
            if c == '\\': cur += body[i+1]; i += 2; continue
            if c == '"': in_s = False
        else:
            if c == '"': in_s = True; cur += c
            elif c in '([{': depth += 1; cur += c
            elif c in ')]}': depth -= 1; cur += c
            elif c == ',' and depth == 0: out.append(cur); cur = ''
            else: cur += c
        i += 1
    if cur.strip(): out.append(cur)
    return [a.strip() for a in out]

def parse_calls(name):
    res = []
    for m in re.finditer(r'\b%s\(' % name, kt):
        i = m.end() - 1; depth = 0; in_s = False; j = i
        while j < len(kt):
            c = kt[j]
            if in_s:
                if c == '\\': j += 2; continue
                if c == '"': in_s = False
            else:
                if c == '"': in_s = True
                elif c == '(': depth += 1
                elif c == ')':
                    depth -= 1
                    if depth == 0: break
            j += 1
        res.append(split_args(kt[i+1:j]))
    return res

kt_pages = []
for args in parse_calls('SgPage'):
    if len(args) < 4 or not args[0].startswith('"'): continue
    kt_pages.append({'id': unesc(args[0][1:-1]), 'en': unesc(args[1][1:-1]),
                     'hi': unesc(args[2][1:-1]), 'ic': unesc(args[3][1:-1])})

kt_items = []
for args in parse_calls('SgItem'):
    if len(args) < 5 or not args[0].startswith('"'): continue
    d = {'id': unesc(args[0][1:-1]), 'page': unesc(args[1][1:-1]),
         'type': args[2].replace('SgType.', ''), 'en': unesc(args[3][1:-1]),
         'hi': unesc(args[4][1:-1])}
    for a in args[5:]:
        if '=' not in a: continue
        k, v = a.split('=', 1); k = k.strip(); v = v.strip()
        if v.startswith('"'): d[k] = unesc(v[1:-1])
        elif v.startswith('listOf('):
            d[k] = [unesc(x.strip()[1:-1]) for x in split_args(v[7:-1]) if x.strip().startswith('"')]
        elif v == 'emptyList()': d[k] = []
        else: d[k] = v
    kt_items.append(d)

kt_defaults = dict(re.findall(r'"(\w+)" to (true|false)', kt[kt.index('object SgDefaults'):kt.index('object SgItems')]))

# ── 3. compare ────────────────────────────────────────────────────────────────
fails = []; oks = 0
def ck(label, a, b):
    global oks
    if a == b: oks += 1
    else: fails.append('%s\n    web    : %r\n    android: %r' % (label, a, b))

ck('page count', len(pages), len(kt_pages))
for i, (w, k) in enumerate(zip(pages, kt_pages)):
    ck('page[%d] id' % i, w['id'], k['id'])
    ck('page[%d] en' % i, w['en'], k['en'])
    ck('page[%d] hi' % i, w['hi'], k['hi'])
    ck('page[%d] icon' % i, w.get('ic',''), k['ic'])

ck('item count', len(items), len(kt_items))
TYPEMAP = {'toggle':'TOGGLE','slider':'SLIDER','action':'ACTION','gated':'GATED','info':'INFO'}
def js_btn(raw):
    if not raw: return None
    strs = re.findall(r"'((?:[^'\\]|\\.)*)'", raw)
    if not strs: return None
    if len(set(strs)) == 1: return strs[0]
    m = re.search(r"\|\|\s*'((?:[^'\\]|\\.)*)'", raw)
    return m.group(1) if m else strs[0]

for i, (w, k) in enumerate(zip(items, kt_items)):
    tag = 'item[%d] %s' % (i, w['id'])
    ck(tag+' id', w['id'], k['id'])
    ck(tag+' page', w['page'], k['page'])
    ck(tag+' type', TYPEMAP[w['type']], k['type'])
    ck(tag+' en', w['en'], k['en'])
    ck(tag+' hi', w['hi'], k['hi'])
    ck(tag+' sumEn', w.get('sumEn'), k.get('sumEn'))
    ck(tag+' sumHi', w.get('sumHi'), k.get('sumHi'))
    ck(tag+' kw', w.get('kw', []), k.get('kw', []))
    ck(tag+' key', w.get('key'), k.get('key'))
    ck(tag+' gate', w.get('gate'), k.get('gate'))
    ck(tag+' btn', js_btn((w.get('_raw') or {}).get('btn')), k.get('btn'))
    if w['type'] == 'slider':
        ck(tag+' min', float(w.get('min', 0)), float(k.get('min', 0)))
        ck(tag+' max', float(w.get('max', 1)), float(k.get('max', 1)))
        ck(tag+' step', float(w.get('step', 1)), float(k.get('step', 1)))

ck('bool defaults', {k: (v is True or v == 'true') for k, v in defaults.items()},
   {k: (v is True or v == 'true') for k, v in kt_defaults.items()})

# ── report ────────────────────────────────────────────────────────────────────
print('Web model      : %d pages · %d items · %d bool defaults' % (len(pages), len(items), len(defaults)))
print('Android model  : %d pages · %d items · %d bool defaults' % (len(kt_pages), len(kt_items), len(kt_defaults)))
print('Compared       : %d field checks' % (oks + len(fails)))
if fails:
    print('\n❌ %d MISMATCH(ES):' % len(fails))
    for f in fails[:40]: print('  - ' + f)
    sys.exit(1)
print('\n✅ PARITY OK — Android settings model web ke 1:1 identical hai (%d/%d checks).' % (oks, oks))
