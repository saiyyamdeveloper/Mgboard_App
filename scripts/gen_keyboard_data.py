#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_keyboard_data.py — Mgboard_Web (index.html) → Mgboard Android `KeyboardData.kt`

Layout data aur Unicode maps ko **haath se copy nahi** kiya jaata. Yeh script web ke
`index.html` se JS literals padh kar Kotlin mein transpile karti hai, taaki dono apps
mein har glyph, har long-press alternate, har row-order identical rahe.

Transpile hone wale constants:
  CP · CONSONANTS · MATRAS · VOWEL_KEYS · MATRA_KEYS · ROW2..ROW5 ·
  DEV_VOWEL_KEYS · DEV_MATRA_KEYS · DEV_ROW2..DEV_ROW5 · QWERTY_ROWS ·
  NUM_ROWS · CALC_OPS · EMOJI

Usage:
    python3 scripts/gen_keyboard_data.py [path/to/Mgboard_Web]
Verify:
    python3 scripts/check_data_parity.py
"""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
WEB = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(ROOT), 'Mgboard_Web')
SRC = os.path.join(WEB, 'index.html')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'kotlin', 'com', 'mgboard', 'keyboard',
                   'data', 'KeyboardData.kt')

if not os.path.exists(SRC):
    sys.exit('ERROR: web source not found: %s' % SRC)
s = open(SRC, encoding='utf-8').read()

# ══════════════════════════ JS literal reader ══════════════════════════

def balanced(text, start, opener, closer):
    depth = 0; j = start; in_s = None; esc = False
    while j < len(text):
        c = text[j]
        if in_s:
            if esc: esc = False
            elif c == '\\': esc = True
            elif c == in_s: in_s = None
        else:
            if c in "'\"`": in_s = c
            elif c == '/' and text[j+1:j+2] == '/': j = text.index('\n', j)
            elif c == '/' and text[j+1:j+2] == '*': j = text.index('*/', j) + 1
            elif c == opener: depth += 1
            elif c == closer:
                depth -= 1
                if depth == 0: return text[start:j+1]
        j += 1
    raise ValueError('unbalanced %s at %d' % (opener, start))

def js_array(name):
    i = s.index('const %s = [' % name)
    i = s.index('[', i)
    return balanced(s, i, '[', ']')

def js_object(name):
    i = s.index('const %s = {' % name)
    i = s.index('{', i)
    return balanced(s, i, '{', '}')

def js_set(name):
    i = s.index('const %s = new Set([' % name)
    i = s.index('[', i)
    return balanced(s, i, '[', ']')

def split_top(text):
    """Split a JS literal body on top-level commas (strings/brackets aware)."""
    out = []; cur = ''; depth = 0; in_s = None; esc = False; i = 0
    while i < len(text):
        c = text[i]
        if in_s:
            cur += c
            if esc: esc = False
            elif c == '\\': esc = True; 
            elif c == in_s: in_s = None
            i += 1; continue
        if c in "'\"`": in_s = c; cur += c; i += 1; continue
        if c == '/' and text[i+1:i+2] == '/':
            nl = text.find('\n', i)
            i = len(text) if nl < 0 else nl
            continue
        if c == '/' and text[i+1:i+2] == '*':
            i = text.index('*/', i) + 2
            continue
        if c in '[{(': depth += 1
        elif c in ']})': depth = max(0, depth - 1)   # callers brackets strip karke dete hain
        if c == ',' and depth == 0:
            out.append(cur); cur = ''
        else:
            cur += c
        i += 1
    if cur.strip(): out.append(cur)
    return [x.strip() for x in out if x.strip()]

# ══════════════════════════ JS → Kotlin expression ══════════════════════════

ESC_MAP = {'n': '\\n', 't': '\\t', 'r': '\\r', '\\': '\\\\', "'": "'", '"': '\\"',
           'b': '\\b', 'f': '\\f', '0': '\\u0000'}

def k_string(js):
    r"""Convert a JS string literal to a Kotlin **expression**.

    IMPORTANT: Kotlin ka \uXXXX escape sirf 4 hex digits leta hai, isliye
    supplementary code points (U+11D00..U+11DAF — Masaram Gondi) ke liye
    String(intArrayOf(0x11D45), 0, 1) emit karna padta hai. Warna \u11D45
    ka matlab \u11D4 + literal '5' ho jaata hai (yeh bug tests mein pakda gaya).
    """
    body = js[1:-1]
    pieces = []          # ('lit', str) | ('cp', int)
    buf = []
    i = 0
    while i < len(body):
        c = body[i]
        if c == '\\' and i + 1 < len(body):
            n = body[i + 1]
            if n == 'u':
                if body[i + 2:i + 3] == '{':
                    end = body.index('}', i + 3)
                    cp = int(body[i + 3:end], 16)
                    i = end + 1
                else:
                    cp = int(body[i + 2:i + 6], 16)
                    i += 6
                if cp > 0xFFFF:
                    if buf:
                        pieces.append(('lit', ''.join(buf)))
                        buf = []
                    pieces.append(('cp', cp))
                else:
                    buf.append('\\u%04x' % cp)
                continue
            buf.append(ESC_MAP.get(n, n))
            i += 2
            continue
        if c == '"':
            buf.append('\\"')
        elif c == '$':
            buf.append('\\$')
        elif c == '\n':
            buf.append('\\n')
        elif c == '\r':
            pass
        else:
            buf.append(c)
        i += 1
    if buf:
        pieces.append(('lit', ''.join(buf)))

    if not pieces:
        return '""'
    if len(pieces) == 1 and pieces[0][0] == 'lit':
        return '"' + pieces[0][1] + '"'
    parts = []
    for kind, val in pieces:
        if kind == 'lit':
            parts.append('"%s"' % val)
        else:
            parts.append('String(intArrayOf(0x%X), 0, 1)' % val)
    return '(' + ' + '.join(parts) + ')'

def k_expr(js):
    """Convert a small JS expression (string literal, CP.X, A+B, identifier)."""
    js = js.strip()
    if not js: return 'null'
    if js == 'null': return 'null'
    if js[0] in "'\"":
        return k_string(js)
    # concatenation with '+' (string-literal aware)
    parts = split_plus(js)
    if len(parts) > 1:
        return ' + '.join(k_expr(p) for p in parts)
    if re.fullmatch(r'CP\.[A-Z0-9_]+', js):
        return 'Cp.' + js[3:]          # Kotlin object ka naam Cp hai
    if re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', js):
        return js
    if re.fullmatch(r'-?\d+(\.\d+)?', js):
        return js
    if js.startswith("CP[") and js.endswith("]"):
        return 'Cp.MAP.getValue(%s)' % k_string(js[3:-1])
    raise ValueError('cannot translate expression: %r' % js)

def split_plus(js):
    out = []; cur = ''; in_s = None; esc = False; i = 0
    while i < len(js):
        c = js[i]
        if in_s:
            cur += c
            if esc: esc = False
            elif c == '\\': esc = True
            elif c == in_s: in_s = None
            i += 1; continue
        if c in "'\"": in_s = c; cur += c; i += 1; continue
        if c == '+':
            out.append(cur); cur = ''
        else:
            cur += c
        i += 1
    if cur.strip(): out.append(cur)
    return [x.strip() for x in out if x.strip()]

# ══════════════════════════ structures ══════════════════════════

def obj_fields(text):
    """{g:CP.A,s:'A',lp:CP.X,lps:'X',cls:'y'} → ordered dict"""
    inner = text.strip()[1:-1]
    d = {}
    for part in split_top(inner):
        if ':' not in part: continue
        k, v = part.split(':', 1)
        d[k.strip()] = v.strip()
    return d

def k_keydef(js, nullable=True):
    js = js.strip()
    if js == 'null': return 'null'
    f = obj_fields(js)
    args = []
    args.append('g = %s' % k_expr(f.get('g', "''")))
    args.append('s = %s' % k_string(f.get('s', "''")) if f.get('s','').startswith(("'", '"'))
                else 's = %s' % k_expr(f.get('s', "''")))
    if 'lp' in f: args.append('lp = %s' % k_expr(f['lp']))
    if 'lps' in f: args.append('lps = %s' % k_string(f['lps']))
    if 'cls' in f: args.append('cls = %s' % k_string(f['cls']))
    return 'KeyDef(' + ', '.join(args) + ')'

def k_row_of_keydefs(js):
    items = split_top(js.strip()[1:-1])
    if not items: return 'emptyList()'
    return 'listOf(\n        ' + ',\n        '.join(k_keydef(i) for i in items) + '\n    )'

def k_string_array(js):
    items = split_top(js.strip()[1:-1])
    return 'listOf(' + ', '.join(k_expr(i) for i in items) + ')'

def k_rows_of_strings(js):
    rows = split_top(js.strip()[1:-1])
    return 'listOf(\n    ' + ',\n    '.join(k_string_array(r) for r in rows) + '\n)'

def k_cp_map(js):
    inner = js.strip()[1:-1]
    lines = []
    for part in split_top(inner):
        if ':' not in part: continue
        k, v = part.split(':', 1)
        lines.append('    "%s" to %s,' % (k.strip(), k_expr(v.strip())))
    return 'mapOf(\n' + '\n'.join(lines) + '\n)'

def k_char_set(js):
    items = split_top(js.strip()[1:-1])
    return 'setOf(\n    ' + ',\n    '.join(k_expr(i) for i in items) + '\n)'

def k_emoji_map(js):
    inner = js.strip()[1:-1]
    lines = []
    for part in split_top(inner):
        if ':' not in part: continue
        k, v = part.split(':', 1)
        lines.append('    %s to %s,' % (k_string(k.strip()), k_string_array(v.strip())))
    return 'linkedMapOf(\n' + '\n'.join(lines) + '\n)'

def k_keydef_list(js):
    items = split_top(js.strip()[1:-1])
    return 'listOf(\n        ' + ',\n        '.join(k_keydef(i) for i in items) + '\n    )'

def k_rows_of_keydefs(js):
    rows = split_top(js.strip()[1:-1])
    return 'listOf(\n    ' + ',\n    '.join(k_row_of_keydefs(r) for r in rows) + '\n    )'

# ══════════════════════════ emit ══════════════════════════

L = []
w = L.append
w('package com.mgboard.keyboard.data')
w('')
w('/* ============================================================================')
w(' *  KeyboardData.kt  —  GENERATED FILE, DO NOT EDIT BY HAND')
w(' *')
w(' *  Source of truth : Mgboard_Web/index.html')
w(' *  Generator       : Mgboard/scripts/gen_keyboard_data.py')
w(' *  Verify          : python3 Mgboard/scripts/check_data_parity.py')
w(' *')
w(' *  Web ke layout literals (CP unicode map, consonant/matra sets, Gondi rows,')
w(' *  Hindi→Gondi rows, QWERTY rows, numbers/symbol pages, emoji categories)')
w(' *  yahan 1:1 transpile hote hain — har glyph, har long-press alternate,')
w(' *  har row order bilkul wahi.')
w(' * ========================================================================== */')
w('')
w('/**')
w(' * Ek key ki definition.')
w(' *')
w(' * @param g   glyph — jo character type hota hai (Gondi Unicode / literal)')
w(' * @param s   screen label (Hindi panel mein Devanagari glyph, Gondi panel mein naam)')
w(' * @param lp  long-press alternate glyph (null = koi alternate nahi)')
w(' * @param lps long-press alternate ka label')
w(' * @param cls web CSS class ka equivalent semantic tag ("special", "danger")')
w(' */')
w('data class KeyDef(')
w('    val g: String,')
w('    val s: String = "",')
w('    val lp: String? = null,')
w('    val lps: String? = null,')
w('    val cls: String? = null,')
w(')')
w('')
w('object Cp {')
w('    /** Web ka `CP` — Masaram Gondi (U+11D00–U+11DAF) + borrowed symbols. */')
w('    val MAP: Map<String, String> = ' + k_cp_map(js_object('CP')))
w('')
w('    private fun g(k: String): String = MAP.getValue(k)')
# named accessors for readability in engine code
cp_fields = [p.split(':')[0].strip() for p in split_top(js_object('CP')[1:-1]) if ':' in p]
for f in cp_fields:
    w('    val %s: String get() = g("%s")' % (f, f))
w('}')
w('')
w('object Gondi {')
w('    /** 37 consonants (34 + 3 conjunct letters KSSA/JNYA/TRA). */')
w('    val CONSONANTS: Set<String> = ' + k_char_set(js_set('CONSONANTS')))
w('')
w('    /** 10 dependent vowel signs + halanta (row1 "matra" mode). */')
w('    val MATRAS: Set<String> = ' + k_char_set(js_set('MATRAS')))
w('')
w('    /** Row 1 — 10 independent vowels (dynamic: vowel ↔ matra). */')
w('    val VOWEL_KEYS: List<KeyDef> = ' + k_keydef_list(js_array('VOWEL_KEYS')))
w('')
w('    /** Row 1 — 10 dependent vowel signs. */')
w('    val MATRA_KEYS: List<KeyDef> = ' + k_keydef_list(js_array('MATRA_KEYS')))
w('')
for n in ('ROW2', 'ROW3', 'ROW4', 'ROW5'):
    w('    val %s: List<KeyDef?> = %s' % (n, k_row_of_keydefs(js_array(n))))
    w('')
w('    /** Rows 2–5 (web: [ROW2, ROW3, ROW4, ROW5]). */')
w('    val LETTER_ROWS: List<List<KeyDef?>> = listOf(ROW2, ROW3, ROW4, ROW5)')
w('}')
w('')
w('object Hindi {')
w('    val VOWEL_KEYS: List<KeyDef> = ' + k_keydef_list(js_array('DEV_VOWEL_KEYS')))
w('')
w('    val MATRA_KEYS: List<KeyDef> = ' + k_keydef_list(js_array('DEV_MATRA_KEYS')))
w('')
for n in ('DEV_ROW2', 'DEV_ROW3', 'DEV_ROW4', 'DEV_ROW5'):
    out_name = n.replace('DEV_', '')
    w('    val %s: List<KeyDef?> = %s' % (out_name, k_row_of_keydefs(js_array(n))))
    w('')
w('    val LETTER_ROWS: List<List<KeyDef?>> = listOf(ROW2, ROW3, ROW4, ROW5)')
w('}')
w('')
w('object Qwerty {')
w('    /** 3 letter rows (row 4 = shift/symbol/backspace control row, code se banti hai). */')
w('    val ROWS: List<List<String>> = ' + k_rows_of_strings(js_array('QWERTY_ROWS')))
w('}')
w('')
w('object Numbers {')
w('    /** 5 rows × 10 keys — shared by Gondi aur Hindi panels (web: #kbN). */')
w('    val ROWS: List<List<KeyDef>> = ' + k_rows_of_keydefs(js_array('NUM_ROWS')))
w('')
w('    /** Calculator page ke operators (web: CALC_OPS). */')
w('    val CALC_OPS: List<String> = ' + k_string_array(js_array('CALC_OPS')))
w('}')
w('')
w('object EmojiData {')
w('    /** 9 categories (order preserved — linked map). */')
w('    val CATEGORIES: Map<String, List<String>> = ' + k_emoji_map(js_object('EMOJI')))
w('}')
w('')

os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'w', encoding='utf-8').write('\n'.join(L))
print('wrote %s (%d lines)' % (OUT, len(L)))
print('CP entries      :', len(cp_fields))
print('CONSONANTS      :', len(split_top(js_set('CONSONANTS')[1:-1])))
print('MATRAS          :', len(split_top(js_set('MATRAS')[1:-1])))
print('Gondi rows      : 10 vowels, 10 matras, ' +
      ', '.join(str(len(split_top(js_array(n)[1:-1]))) for n in ('ROW2','ROW3','ROW4','ROW5')))
print('Hindi rows      : ' +
      ', '.join(str(len(split_top(js_array(n)[1:-1]))) for n in ('DEV_ROW2','DEV_ROW3','DEV_ROW4','DEV_ROW5')))
print('QWERTY rows     :', [len(split_top(r[1:-1])) for r in split_top(js_array('QWERTY_ROWS')[1:-1])])
print('NUM_ROWS        :', [len(split_top(r[1:-1])) for r in split_top(js_array('NUM_ROWS')[1:-1])])
print('Emoji categories:', len(split_top(js_object('EMOJI')[1:-1])))
