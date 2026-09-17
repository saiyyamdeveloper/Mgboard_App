#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_settings_model.py — Mgboard_Web  →  Mgboard_Android settings model transpiler

Web ka SG_PAGES / SG_BOOL_DEFAULTS / SG_ITEMS (+ SG_HELP_ITEMS + SG_ABOUT_ITEMS)
padh kar Android ke liye `SettingsModel.kt` generate karta hai, taaki dono apps ka
settings data 1:1 identical rahe (labels EN+HI, summaries, search keywords,
Gboard defaults, gate reason strings, item order).

Usage:
    python3 Mgboard/scripts/gen_settings_model.py [path/to/Mgboard_Web]

Default paths: ../Mgboard_Web (sibling repo) aur ../app/src/main/kotlin/...
Verify karne ke liye:  python3 Mgboard/scripts/check_parity.py
"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
WEB  = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(ROOT), 'Mgboard_Web')
SRC  = os.path.join(WEB, 'index.html')
OUT  = os.path.join(ROOT, 'app', 'src', 'main', 'kotlin', 'com', 'mgboard', 'keyboard',
                    'model', 'SettingsModel.kt')
if not os.path.exists(SRC):
    sys.exit('ERROR: web source not found: %s' % SRC)

# -*- coding: utf-8 -*-
"""Parse SG_PAGES / SG_BOOL_DEFAULTS / SG_ITEMS out of Mgboard_Web/index.html
   with a hand-written tokenizer (no JS engine needed)."""

s = open(SRC, encoding='utf-8').read()

def slice_array(start_marker):
    """Return the raw text between '[' and its matching ']' after marker."""
    i = s.index(start_marker)
    i = s.index('[', i)
    depth = 0; j = i; in_s = None; esc = False
    while j < len(s):
        c = s[j]
        if in_s:
            if esc: esc = False
            elif c == '\\': esc = True
            elif c == in_s: in_s = None
        else:
            if c in "'\"`": in_s = c
            elif c == '[': depth += 1
            elif c == ']':
                depth -= 1
                if depth == 0: return s[i+1:j]
            elif c == '/' and s[j+1] == '/':
                j = s.index('\n', j)
            elif c == '/' and s[j+1] == '*':
                j = s.index('*/', j) + 1
        j += 1
    raise ValueError('unbalanced ' + start_marker)

def slice_object(text, pos):
    """From text[pos]=='{' return (obj_text, end_index) honouring nesting/strings."""
    depth = 0; j = pos; in_s = None; esc = False
    while j < len(text):
        c = text[j]
        if in_s:
            if esc: esc = False
            elif c == '\\': esc = True
            elif c == in_s: in_s = None
        else:
            if c in "'\"`": in_s = c
            elif c == '{': depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0: return text[pos:j+1], j+1
        j += 1
    raise ValueError('unbalanced object at %d' % pos)

STR_FIELDS = ('id','page','type','en','hi','sumEn','sumHi','key','gate','btn','fmt','dynamic')
NUM_FIELDS = ('min','max','step')

def parse_string(v):
    v = v.strip()
    assert v[0] in "'\"" and v[-1] == v[0], v
    body = v[1:-1]
    out = ''; i = 0
    while i < len(body):
        c = body[i]
        if c == '\\' and i+1 < len(body):
            n = body[i+1]
            out += {'n':'\n','t':'\t','r':'\r','\\':'\\',"'":"'",'"':'"'}.get(n, n)
            i += 2
        else:
            out += c; i += 1
    return out

def parse_item(obj):
    """obj = '{ ... }' text of one item. Returns dict with parsed scalar fields
       plus '_raw' for function-ish fields (get/set/run)."""
    inner = obj[1:-1]
    out = {}; i = 0; n = len(inner)
    while i < n:
        while i < n and inner[i] in ' \t\r\n,': i += 1
        if i >= n: break
        if inner.startswith('/*', i):
            i = inner.index('*/', i) + 2; continue
        if inner.startswith('//', i):
            i = inner.index('\n', i) if '\n' in inner[i:] else n; continue
        # field name
        m = i
        while m < n and (inner[m].isalnum() or inner[m] == '_'): m += 1
        name = inner[i:m]
        if not name:
            i += 1; continue
        k = m
        while k < n and inner[k] in ' \t\r\n': k += 1
        if k >= n or inner[k] != ':':
            i = k; continue
        v = k + 1
        while v < n and inner[v] in ' \t\r\n': v += 1
        # value extent
        if inner[v] == "'":
            j = v + 1
            while True:
                if inner[j] == '\\': j += 2; continue
                if inner[j] == "'": break
                j += 1
            j += 1
            raw = inner[v:j]
            if name in STR_FIELDS: out[name] = parse_string(raw)
            else: out.setdefault('_raw', {})[name] = raw
        elif inner[v] == '[':
            depth = 0; j = v; in_s = None
            while j < n:
                c = inner[j]
                if in_s:
                    if c == '\\': j += 2; continue
                    if c == in_s: in_s = None
                else:
                    if c in "'\"": in_s = c
                    elif c == '[': depth += 1
                    elif c == ']':
                        depth -= 1
                        if depth == 0: break
                j += 1
            raw = inner[v:j+1]
            if name == 'kw':
                out['kw'] = [parse_string("'" + x + "'") for x in __import__('re').findall(r"'((?:[^'\\]|\\.)*)'", raw)]
            else:
                out.setdefault('_raw', {})[name] = raw
            j += 1
        elif inner[v] in '-0123456789.':
            j = v
            while j < n and (inner[j].isdigit() or inner[j] in '.-'): j += 1
            out[name] = float(inner[v:j]) if '.' in inner[v:j] else int(inner[v:j])
        else:  # function / expression value → capture until top-level ',' or end
            depth = 0; j = v; in_s = None
            while j < n:
                c = inner[j]
                if in_s:
                    if c == '\\': j += 2; continue
                    if c == in_s: in_s = None
                else:
                    if c in "'\"`": in_s = c
                    elif c in '([{': depth += 1
                    elif c in ')]}': depth -= 1
                    elif c == ',' and depth == 0: break
                j += 1
            out.setdefault('_raw', {})[name] = inner[v:j].strip()
        i = j
    return out

# ---------- pages ----------
pages_txt = slice_array('const SG_PAGES = [')
pages = []
p = 0
while True:
    q = pages_txt.find('{', p)
    if q < 0: break
    obj, p = slice_object(pages_txt, q)
    inner = obj.strip('{}')
    d = {}
    import re as _re
    for m in _re.finditer(r"(\w+)\s*:\s*'((?:[^'\\]|\\.)*)'", inner):
        d[m.group(1)] = parse_string("'" + m.group(2) + "'")
    if d.get('id'): pages.append(d)

# ---------- bool defaults ----------
k = s.index('const SG_BOOL_DEFAULTS = {')
k = s.index('{', k)
obj, _ = slice_object(s, k)
import re as _re
defaults = {}
for m in _re.finditer(r"(\w+)\s*:\s*(true|false)", obj):
    defaults[m.group(1)] = (m.group(2) == 'true')

# ---------- items ----------
items = []
for marker in ('const SG_ITEMS = [', 'const SG_HELP_ITEMS = [', 'const SG_ABOUT_ITEMS = ['):
    txt = slice_array(marker)
    p = 0
    while True:
        q = txt.find('{', p)
        if q < 0: break
        obj, p = slice_object(txt, q)
        it = parse_item(obj)
        if it.get('id'): items.append(it)

# ---------- side effects (switch cases in sgApplySideEffects) ----------
a = s.index('function sgApplySideEffects(key, v){')
b = s.index('\n}', a)
side_txt = s[a:b]
side_cases = _re.findall(r"case '(\w+)':", side_txt)


# -*- coding: utf-8 -*-
"""Generate SettingsModel.kt for Mgboard_Android from the parsed web model.
   Guarantee: Android ka data model web (Mgboard_Web/index.html) se 1:1 identical."""

MODEL = {'pages': pages, 'defaults': defaults, 'items': items, 'side_cases': side_cases}

M = MODEL
pages, defaults, items = M['pages'], M['defaults'], M['items']

def kstr(v):
    if v is None: return 'null'
    out = []
    for ch in v:
        if ch == '\\': out.append('\\\\')
        elif ch == '"': out.append('\\"')
        elif ch == '$': out.append('\\$')
        elif ch == '\n': out.append('\\n')
        elif ch == '\t': out.append('\\t')
        elif ch == '\r': continue
        else: out.append(ch)
    return '"' + ''.join(out) + '"'

def klist(v):
    if not v: return 'emptyList()'
    return 'listOf(' + ', '.join(kstr(x) for x in v) + ')'

def btn_label(raw):
    """Extract a static button label from a JS arrow-fn body."""
    if not raw: return None
    strs = re.findall(r"'((?:[^'\\]|\\.)*)'", raw)
    if not strs: return None
    if len(set(strs)) == 1: return strs[0]
    # {gondi:'A',qwerty:'B',hindi:'C'}[state.kbMode] || 'D'  → fallback label
    m = re.search(r"\|\|\s*'((?:[^'\\]|\\.)*)'", raw)
    return m.group(1) if m else strs[0]

# Actions/info jo Android par abhi ASLI kaam karte hain (baaki placeholder toast)
ANDROID_READY = {
    'sg_cp_clear', 'sg_delete_data', 'sg_help_open', 'sg_about_open',
    'sg_dbg_capacity', 'sg_learned', 'sg_cp_retention', 'sg_dbg_gates',
}

L = []
w = L.append
w('package com.mgboard.keyboard.model')
w('')
w('/* ============================================================================')
w(' *  SettingsModel.kt  —  GENERATED FILE, DO NOT EDIT BY HAND')
w(' *')
w(' *  Source of truth : Mgboard_Web/index.html  (SG_PAGES / SG_BOOL_DEFAULTS /')
w(' *                    SG_ITEMS + SG_HELP_ITEMS + SG_ABOUT_ITEMS)')
w(' *  Generator       : Mgboard/scripts/gen_settings_model.py')
w(' *  Verify          : python3 Mgboard/scripts/check_parity.py')
w(' *')
w(' *  Web aur Android ka settings model byte-level identical rakha gaya hai:')
w(' *  same page ids, same item ids/order, same EN+HI labels, same summaries,')
w(' *  same search keywords, same Gboard defaults, same gate reason strings.')
w(' * ========================================================================== */')
w('')
w('enum class SgType { TOGGLE, SLIDER, ACTION, GATED, INFO }')
w('')
w('/** One settings page (Gboard ki 13 + MgBoard ka Developer/Debug). */')
w('data class SgPage(')
w('    val id: String,')
w('    val en: String,')
w('    val hi: String,')
w('    val icon: String,')
w(')')
w('')
w('/**')
w(' * One settings row.')
w(' *')
w(' * @param key      SharedPreferences key for a plain boolean toggle (null = custom get/set).')
w(' * @param btn      Static label for ACTION rows (button text).')
w(' * @param gate     Verbatim Gboard reason string shown when a GATED row is tapped.')
w(' * @param min/max/step  SLIDER range (Gboard-exact).')
w(' * @param webFn    The original web get/set/run/dynamic body, kept as a comment-grade')
w(' *                 reference so the Android handler stays faithful to web behaviour.')
w(' */')
w('data class SgItem(')
w('    val id: String,')
w('    val page: String,')
w('    val type: SgType,')
w('    val en: String,')
w('    val hi: String,')
w('    val sumEn: String? = null,')
w('    val sumHi: String? = null,')
w('    val kw: List<String> = emptyList(),')
w('    val key: String? = null,')
w('    val btn: String? = null,')
w('    val gate: String? = null,')
w('    val min: Double = 0.0,')
w('    val max: Double = 1.0,')
w('    val step: Double = 1.0,')
w('    val webFn: String? = null,')
w(')')
w('')
w('object SgPages {')
w('    val ALL: List<SgPage> = listOf(')
for p in pages:
    w('        SgPage(%s, %s, %s, %s),' % (kstr(p['id']), kstr(p['en']), kstr(p['hi']), kstr(p.get('ic',''))))
w('    )')
w('')
w('    fun byId(id: String): SgPage? = ALL.firstOrNull { it.id == id }')
w('}')
w('')
w('object SgDefaults {')
w('    /** Gboard-exact boolean defaults — web ke SG_BOOL_DEFAULTS se identical. */')
w('    val BOOL: Map<String, Boolean> = mapOf(')
for k in sorted(defaults):
    w('        %s to %s,' % (kstr(k), 'true' if defaults[k] else 'false'))
w('    )')
w('}')
w('')
w('object SgItems {')
w('    /** 90 items in the exact web order (SG_ITEMS then help, then about). */')
w('    val ALL: List<SgItem> = listOf(')
TYPEMAP = {'toggle':'TOGGLE','slider':'SLIDER','action':'ACTION','gated':'GATED','info':'INFO'}
for it in items:
    raw = it.get('_raw') or {}
    fnkeys = [k for k in ('get','set','run','dynamic','btn') if k in raw]
    webfn = None
    if it['type'] == 'slider' and 'get' in raw:
        webfn = raw['get'] + ' | ' + raw.get('set','')
    elif it['type'] == 'action' and 'run' in raw:
        webfn = raw['run']
    elif it['type'] == 'info' and 'dynamic' in raw:
        webfn = raw['dynamic']
    elif it['type'] == 'toggle' and 'get' in raw:
        webfn = raw['get'] + ' | ' + raw.get('set','')
    if webfn:
        webfn = re.sub(r'\s+', ' ', webfn).strip()
        if len(webfn) > 300: webfn = webfn[:297] + '...'
    bl = btn_label(raw.get('btn'))
    args = [kstr(it['id']), kstr(it['page']), 'SgType.' + TYPEMAP[it['type']], kstr(it['en']), kstr(it['hi'])]
    if it.get('sumEn'): args.append('sumEn = ' + kstr(it['sumEn']))
    if it.get('sumHi'): args.append('sumHi = ' + kstr(it['sumHi']))
    if it.get('kw'): args.append('kw = ' + klist(it['kw']))
    if it.get('key'): args.append('key = ' + kstr(it['key']))
    if bl: args.append('btn = ' + kstr(bl))
    if it.get('gate'): args.append('gate = ' + kstr(it['gate']))
    if it['type'] == 'slider':
        def dbl(x):
            f = float(x)
            return ('%.10g' % f) if ('.' in ('%.10g' % f)) else ('%d.0' % f)
        args.append('min = %s' % dbl(it.get('min', 0)))
        args.append('max = %s' % dbl(it.get('max', 1)))
        args.append('step = %s' % dbl(it.get('step', 1)))
    if webfn: args.append('webFn = ' + kstr(webfn))
    w('        SgItem(')
    for a in args:
        w('            %s,' % a)
    w('        ),')
w('    )')
w('')
w('    fun byId(id: String): SgItem? = ALL.firstOrNull { it.id == id }')
w('')
w('    fun ofPage(pageId: String): List<SgItem> = ALL.filter { it.page == pageId }')
w('')
w('    /**')
w('     * Gboard-exact search: case-insensitive substring over')
w('     * en + hi + sumEn + sumHi + id + keywords (web sgMatches() ka 1:1 port).')
w('     */')
w('    fun matches(it: SgItem, qRaw: String): Boolean {')
w('        val q = qRaw.lowercase()')
w('        if (q.isEmpty()) return false')
w('        val hay = listOfNotNull(it.en, it.hi, it.sumEn, it.sumHi, it.id)')
w('            .plus(it.kw)')
w('            .joinToString(" ")')
w('            .lowercase()')
w('        return hay.contains(q)')
w('    }')
w('')
w('    fun search(q: String): List<SgItem> = ALL.filter { matches(it, q) }')
w('')
w('    /** Rows whose behaviour needs the MgBoard IME service (not ported yet). */')
w('    val PENDING_IME: Set<String> = setOf(')
pend = [it['id'] for it in items
        if (it['type'] in ('action','info') and it['id'] not in ANDROID_READY)
        or (it['type'] == 'toggle' and it.get('id') in ('sg_toolbar',))
        or (it['type'] == 'slider')]
for p_ in pend:
    w('        %s,' % kstr(p_))
w('    )')
w('}')
w('')

out = '\n'.join(L)
path = OUT
open(path, 'w', encoding='utf-8').write(out)
print('wrote', path, len(out.splitlines()), 'lines')
print('items emitted:', len(items), '| pages:', len(pages), '| defaults:', len(defaults))
print('PENDING_IME:', len(pend))
