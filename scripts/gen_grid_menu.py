#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_grid_menu.py — Mgboard_Web (index.html) → Mgboard Android `GridMenu.kt`

Web ke `EXT_ITEMS` (grid menu / access points) se Kotlin data model banata hai:
tile ids, labels (EN + HI), enabled/gated status aur **Gboard ke verbatim
gateReason strings**. Icons web mein inline SVG hain; Android par unka mapping
`GridIcons` mein hand-maintained hota hai (naya tile aaye to build fail hoga —
jaan-boojh kar, taaki icon add karna na bhoole).

Usage:  python3 scripts/gen_grid_menu.py [path/to/Mgboard_Web]
"""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
WEB = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(ROOT), 'Mgboard_Web')
SRC = os.path.join(WEB, 'index.html')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'kotlin', 'com', 'mgboard', 'keyboard',
                   'grid', 'GridMenu.kt')
if not os.path.exists(SRC):
    sys.exit('ERROR: web source not found: %s' % SRC)
s = open(SRC, encoding='utf-8').read()

i = s.index('const EXT_ITEMS = [')
j = s.index('\n];', i)
body = s[i:j]

# ── constants ──
def const_int(name):
    m = re.search(r'const\s+%s\s*=\s*(-?\d+)' % name, s)
    return int(m.group(1)) if m else None

MIN_PIN = const_int('GBOARD_MIN_PINNED')
MAX_PIN = const_int('GBOARD_MAX_PINNED')
MAX_PINNED = const_int('MAX_PINNED')
EXT_PER_PAGE = const_int('EXT_PER_PAGE')
PIN_COUNT_KEY = re.search(r"const\s+PIN_COUNT_KEY\s*=\s*'([^']+)'", s).group(1)
PIN_STORAGE_KEY = re.search(r"const\s+PIN_STORAGE_KEY\s*=\s*'([^']+)'", s).group(1)

# ── items ──
raw_items = re.findall(r"\{id:'([a-zA-Z]+)'(.*?)(?=\n  \{id:'|\Z)", body, re.S)
items = []
for name, rest in raw_items:
    def fld(key):
        m = re.search(r"\b%s\s*:\s*'((?:[^'\\]|\\.)*)'" % key, rest)
        if not m: return None
        v = m.group(1).replace("\\'", "'").replace('\\\\', '\\')
        return v
    items.append({
        'id': name,
        'lbl': fld('lbl'),
        'lblHi': fld('lblHi'),
        'enabled': 'enabled:false' not in rest,
        'gate': fld('gateReason'),
    })

def kstr(v):
    if v is None: return 'null'
    out = []
    for ch in v:
        if ch == '\\': out.append('\\\\')
        elif ch == '"': out.append('\\"')
        elif ch == '$': out.append('\\$')
        elif ch == '\n': out.append('\\n')
        else: out.append(ch)
    return '"' + ''.join(out) + '"'

L = []
w = L.append
w('package com.mgboard.keyboard.grid')
w('')
w('/* ============================================================================')
w(' *  GridMenu.kt  —  GENERATED FILE, DO NOT EDIT BY HAND')
w(' *')
w(' *  Source of truth : Mgboard_Web/index.html  (EXT_ITEMS + capacity constants)')
w(' *  Generator       : Mgboard/scripts/gen_grid_menu.py')
w(' *')
w(' *  Gboard parity: tile ids/labels, enabled vs gated, aur gateReason strings')
w(' *  (Gboard ke verbatim) web se 1:1 aate hain. Capacity rules bhi web ke same:')
w(' *  portrait 5 / landscape 6, user override %d–%d.' % (MIN_PIN, MAX_PIN))
w(' * ========================================================================== */')
w('')
w('/**')
w(' * Grid menu ka ek access point (tile).')
w(' *')
w(' * @param enabled false matlab tile dikhta hai par gated hai — tap par')
w(' *                [gateReason] toast hota hai (hide-nothing principle).')
w(' */')
w('data class GridTile(')
w('    val id: String,')
w('    val label: String,')
w('    val labelHi: String? = null,')
w('    val enabled: Boolean = true,')
w('    val gateReason: String? = null,')
w(')')
w('')
w('object GridMenu {')
w('')
w('    /** Web ke `EXT_ITEMS` — same order. `mic` fixed slot hai (draggable nahi). */')
w('    val TILES: List<GridTile> = listOf(')
for it in items:
    args = [kstr(it['id']), kstr(it['lbl'])]
    if it['lblHi']: args.append('labelHi = ' + kstr(it['lblHi']))
    args.append('enabled = ' + ('true' if it['enabled'] else 'false'))
    if it['gate']: args.append('gateReason = ' + kstr(it['gate']))
    w('        GridTile(' + ', '.join(args) + '),')
w('    )')
w('')
w('    /** Fixed slot — grid mein draggable nahi (Gboard: mic hamesha right par). */')
w('    const val MIC_ID = "mic"')
w('')
w('    /** Draggable tiles (mic ke bina). */')
w('    val GRID_TILES: List<GridTile> get() = TILES.filter { it.id != MIC_ID }')
w('')
w('    val ENABLED_TILES: List<GridTile> get() = GRID_TILES.filter { it.enabled }')
w('    val GATED_TILES: List<GridTile> get() = GRID_TILES.filter { !it.enabled }')
w('')
w('    fun byId(id: String): GridTile? = TILES.firstOrNull { it.id == id }')
w('')
w('    // ── capacity (Gboard-exact, web: maxPinnedCapacity/setPinnedCapacity) ────')
w('')
w('    /** Gboard: "between %d and %d, inclusive". */' % (MIN_PIN, MAX_PIN))
w('    const val MIN_PINNED = %d' % MIN_PIN)
w('    const val MAX_PINNED = %d' % MAX_PIN)
w('')
w('    /** Web ka `MAX_PINNED` (2023 redesign ke baad Gboard mein 6 slots). */')
w('    const val LANDSCAPE_CAPACITY = %d' % MAX_PINNED)
w('')
w('    /** Gboard portrait default = 5 (web: else branch). */')
w('    const val PORTRAIT_CAPACITY = 5')
w('')
w('    /** Grid popup mein ek page par kitne tiles (web: EXT_PER_PAGE). */')
w('    const val PER_PAGE = %d' % EXT_PER_PAGE)
w('')
w('    /** Storage keys — web localStorage ke same naam. */')
w('    const val KEY_CAPACITY = "%s"' % PIN_COUNT_KEY)
w('    const val KEY_PINNED = "%s"' % PIN_STORAGE_KEY)
w('')
w('    /**')
w('     * Web `maxPinnedCapacity()`: orientation default, phir stored override,')
w('     * phir clamp. `stored` null/empty/invalid ho to orientation default chalta')
w('     * hai (web ka parseInt-NaN branch).')
w('     */')
w('    fun capacity(landscape: Boolean, stored: Int?): Int {')
w('        val base = when {')
w('            stored != null -> stored')
w('            landscape -> LANDSCAPE_CAPACITY')
w('            else -> PORTRAIT_CAPACITY')
w('        }')
w('        return clamp(base)')
w('    }')
w('')
w('    /**')
w('     * Web `setPinnedCapacity(n)`: `parseInt(n,10) || 5` wala falsy-zero bug')
w('     * yahan nahi hai — 0 → MIN_PINNED clamp hona chahiye (web ka [FIX]).')
w('     */')
w('    fun normalizeCapacity(n: Int?): Int = clamp(if (n == null) 5 else n)')
w('')
w('    fun clamp(n: Int): Int = n.coerceIn(MIN_PINNED, MAX_PINNED)')
w('')
w('    /**')
w('     * Gboard ka order format — "Define the order of displayed access point')
w('     * icons, separated by semicolon" (web: extApplyOrderSemicolon).')
w('     *')
w('     * Rules: mic drop (fixed slot), unknown ids drop, gated ids drop,')
w('     * duplicates drop, capacity tak trim.')
w('     */')
w('    fun parseOrderSemicolon(str: String?, capacity: Int): List<String> {')
w('        if (str == null) return emptyList()')
w('        val enabledIds = ENABLED_TILES.map { it.id }.toSet()')
w('        return str.split(";")')
w('            .map { it.trim() }')
w('            .filter { it.isNotEmpty() }')
w('            .filter { it != MIC_ID && enabledIds.contains(it) }')
w('            .distinct()')
w('            .take(capacity)')
w('    }')
w('')
w('    /** Web `extOrderSemicolon()` — pinned ids ko Gboard format mein dedo. */')
w('    fun toOrderSemicolon(ids: List<String>): String = ids.joinToString(";")')
w('}')
w('')

os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'w', encoding='utf-8').write('\n'.join(L))
print('wrote %s (%d lines)' % (OUT, len(L)))
print('tiles: %d (enabled %d, gated %d, mic %s)' % (
    len(items),
    sum(1 for x in items if x['enabled'] and x['id'] != 'mic'),
    sum(1 for x in items if not x['enabled']),
    'present' if any(x['id'] == 'mic' for x in items) else 'MISSING'))
print('capacity: %d–%d, portrait 5 / landscape %d, per page %d' % (MIN_PIN, MAX_PIN, MAX_PINNED, EXT_PER_PAGE))
missing_hi = [x['id'] for x in items if not x['lblHi']]
print('tiles without HI label:', ', '.join(missing_hi) if missing_hi else 'none')
gated_no_reason = [x['id'] for x in items if not x['enabled'] and not x['gate']]
print('gated without reason:', gated_no_reason or 'none')
