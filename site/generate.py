#!/usr/bin/env python3
"""
Generate the Velo documentation website from the Markdown in ../docs.

The docs are the single source of truth for *content*: every docs/NN-name.md
chapter becomes a styled, standalone HTML page (with Velo syntax highlighting),
and inter-chapter `.md` links are rewritten to `.html`, so the site mirrors the
docs exactly and cannot drift from them.

This script owns only *presentation*: a compact, engineering-grade shell — a
persistent grouped sidebar (jump anywhere), an on-this-page outline, previous/
next paging, and a client-side search box. The chapter grouping/order lives in
SECTIONS below (a reading path, not the raw file order); any NN-*.md file that
is not listed there is still published and appended to an "Other" section, with
a warning, so nothing is silently dropped. The per-chapter hand-written
Previous/Next footer in the Markdown is stripped from the HTML (the shell paging
replaces it) but kept in the .md for standalone GitHub browsing.

Pure standard library — no third-party packages — so it runs in CI under a bare
`python3` with no install step.
"""

import os
import re
import html
import json
import shutil

SITE_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.dirname(SITE_DIR)
DOCS_DIR = os.path.join(REPO_DIR, "docs")
OUTPUT_DIR = os.path.join(SITE_DIR, "public")

REPO_URL = "https://github.com/solkin/velo-lang"
TAGLINE = "Simple. Embeddable. Yours."
LEAD = ("A functional, strictly-typed, compilable language on a small stack-based "
        "virtual machine — with generics, actors, and one-call JVM embedding.")

# --------------------------------------------------------------------------- #
# Site structure — the reading order and grouping (presentation only).        #
# --------------------------------------------------------------------------- #
# 01-introduction is folded into the landing page (the home *is* the intro), so
# it is not generated as its own chapter — see EXCLUDE and the sidebar's leading
# "Introduction" link to index.html.
EXCLUDE = {"01-introduction"}

SECTIONS = [
    ("Getting started", [
        "02-language-basics", "03-data-types",
        "04-variables-and-assignment", "05-operators",
    ]),
    ("Control flow", [
        "06-conditionals", "07-loops", "08-functions",
    ]),
    ("Collections & text", [
        "09-arrays", "10-dictionaries", "11-strings", "12-tuples",
    ]),
    ("Types & objects", [
        "13-classes", "28-data-classes", "29-interfaces",
        "14-extension-functions", "24-operator-overloading", "23-generics",
    ]),
    ("Functional", [
        "25-closures", "22-apply-blocks", "27-callbacks",
    ]),
    ("Concurrency & errors", [
        "26-actors", "32-error-handling",
    ]),
    ("Systems & interop", [
        "21-pointers", "15-native-classes", "17-standard-library",
        "16-modules-and-imports",
    ]),
    ("Reference", [
        "18-running-programs", "19-language-features", "20-best-practices",
        "30-llm-guide", "31-grammar",
    ]),
]

# Short sidebar labels where the H1 is long (falls back to the H1 otherwise).
SHORT_TITLES = {
    "04-variables-and-assignment": "Variables",
    "06-conditionals": "Conditionals",
    "16-modules-and-imports": "Modules & imports",
    "22-apply-blocks": "Apply blocks",
    "30-llm-guide": "LLM cheat-sheet",
    "31-grammar": "Grammar",
}

# The essential path to being productive in ~30 minutes: (stem, one-liner).
LEARNING_PATH = [
    ("02-language-basics", "Program shape, comments, semicolons."),
    ("03-data-types", "The primitive and composite types."),
    ("04-variables-and-assignment", "Declaring, typing, re-binding."),
    ("05-operators", "Arithmetic, comparison, logic, bitwise."),
    ("06-conditionals", "if / else, when pattern-matching."),
    ("07-loops", "while and for over ranges and arrays."),
    ("08-functions", "Parameters, returns, lambdas."),
    ("13-classes", "Group data and behaviour."),
]

# Curated "keep going" links for the landing (stem, label).
EXPLORE = [
    ("17-standard-library", "Standard library"),
    ("26-actors", "Actors & concurrency"),
    ("32-error-handling", "Error handling"),
    ("23-generics", "Generics"),
    ("15-native-classes", "Native classes"),
    ("31-grammar", "Grammar & precedence"),
]


def escape(text):
    return html.escape(text, quote=False)


# --------------------------------------------------------------------------- #
# Brand mark — a white bicycle on the green→lime gradient badge.               #
# --------------------------------------------------------------------------- #
BIKE_SVG = ('<svg viewBox="0 0 48 48" fill="none" stroke-linecap="round" '
            'stroke-linejoin="round" aria-hidden="true">'
            '<circle cx="13" cy="32" r="8.2"/><circle cx="35" cy="32" r="8.2"/>'
            '<path d="M13 32 22 32 20 16 30 17.5 35 32"/>'
            '<path d="M22 32 30 17.5"/><path d="M20 16 13 32"/>'
            '<path d="M16.5 15 22 15.2"/><path d="M30 17.5 33.5 13.5"/></svg>')


def badge(cls="logo"):
    return f'<span class="{cls}">{BIKE_SVG}</span>'


FAVICON_SVG = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">'
               '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">'
               '<stop offset="0" stop-color="#0b8a3a"/>'
               '<stop offset="0.55" stop-color="#4fb023"/>'
               '<stop offset="1" stop-color="#b4d80a"/></linearGradient></defs>'
               '<rect width="48" height="48" rx="11" fill="url(#g)"/>'
               '<g transform="translate(4.8 5.6) scale(0.8)" fill="none" '
               'stroke="#fff" stroke-width="3" stroke-linecap="round" '
               'stroke-linejoin="round">'
               '<circle cx="13" cy="32" r="8.2"/><circle cx="35" cy="32" r="8.2"/>'
               '<path d="M13 32 22 32 20 16 30 17.5 35 32"/>'
               '<path d="M22 32 30 17.5"/><path d="M20 16 13 32"/>'
               '<path d="M16.5 15 22 15.2"/><path d="M30 17.5 33.5 13.5"/></g></svg>')


# --------------------------------------------------------------------------- #
# Velo syntax highlighting                                                     #
# --------------------------------------------------------------------------- #
KEYWORDS = [
    'import', 'func', 'class', 'data', 'interface', 'ext', 'operator',
    'actor', 'async', 'await', 'if', 'then', 'else', 'when', 'enum',
    'while', 'for', 'in', 'let', 'new', 'return', 'break', 'continue',
    'try', 'catch', 'throw', 'null', 'void', 'true', 'false',
]
TYPES = [
    'byte', 'int', 'long', 'float', 'str', 'bool', 'any',
    'array', 'dict', 'tuple', 'ptr', 'func', 'future', 'Self',
]


def highlight_velo(code):
    keywords = set(KEYWORDS)
    types = set(TYPES)
    result_lines = []
    for line in code.split('\n'):
        chars = list(line)
        tokens = []
        i = 0
        while i < len(chars):
            if chars[i] == '#':
                tokens.append(('comment', line[i:]))
                i = len(chars)
            elif chars[i] == '"':
                j = i + 1
                while j < len(chars):
                    if chars[j] == '\\' and j + 1 < len(chars):
                        j += 2
                        continue
                    if chars[j] == '"':
                        j += 1
                        break
                    j += 1
                tokens.append(('string', line[i:j]))
                i = j
            elif chars[i].isdigit() or (chars[i] == '-' and i + 1 < len(chars) and chars[i + 1].isdigit()):
                j = i
                if chars[j] == '-':
                    j += 1
                if j + 1 < len(chars) and chars[j] == '0' and chars[j + 1] in 'xXbB':
                    j += 2
                    while j < len(chars) and (chars[j].isalnum() or chars[j] == '_'):
                        j += 1
                else:
                    while j < len(chars) and (chars[j].isdigit() or chars[j] in '._'):
                        j += 1
                tokens.append(('number', line[i:j]))
                i = j
            elif chars[i].isalpha() or chars[i] == '_':
                j = i
                while j < len(chars) and (chars[j].isalnum() or chars[j] == '_'):
                    j += 1
                word = line[i:j]
                if word in keywords:
                    tokens.append(('keyword', word))
                elif word in types:
                    tokens.append(('type', word))
                elif word[0].isupper():
                    tokens.append(('type', word))
                else:
                    tokens.append(('plain', word))
                i = j
            elif chars[i] in '+-*/%=<>!&|^':
                j = i
                while j < len(chars) and chars[j] in '+-*/%=<>!&|^':
                    j += 1
                tokens.append(('operator', line[i:j]))
                i = j
            else:
                tokens.append(('plain', chars[i]))
                i += 1
        out = ''
        css = {'keyword': 'kw', 'type': 'ty', 'string': 'st',
               'number': 'nu', 'comment': 'cm', 'operator': 'op'}
        for ttype, tval in tokens:
            esc = escape(tval)
            cls = css.get(ttype)
            out += f'<span class="{cls}">{esc}</span>' if cls else esc
        result_lines.append(out)
    return '\n'.join(result_lines)


# --------------------------------------------------------------------------- #
# Markdown -> HTML (a focused converter for the subset the docs use)          #
# --------------------------------------------------------------------------- #

def slugify(text):
    t = re.sub(r'`[^`]*`', lambda m: m.group(0).strip('`'), text)
    t = re.sub(r'[*_\[\]()]', '', t)
    t = t.strip().lower()
    t = re.sub(r'[^a-z0-9\s-]', '', t)
    t = re.sub(r'\s+', '-', t)
    return t


def rewrite_link(url):
    if url.startswith(('http://', 'https://', 'mailto:', '#', '/')):
        return url
    anchor = ''
    if '#' in url:
        url, frag = url.split('#', 1)
        anchor = '#' + frag
    url = re.sub(r':\d+$', '', url)
    if url.endswith('.md'):
        base = url[:-3]
        if base == 'README' or base.endswith('/README') or base == '01-introduction':
            url = 'index.html'
        else:
            url = base + '.html'
    return url + anchor


def inline(text):
    spans = []

    def stash(m):
        spans.append('<code>' + escape(m.group(1)) + '</code>')
        return '\x00%d\x00' % (len(spans) - 1)

    text = re.sub(r'`([^`]+)`', stash, text)
    text = escape(text)
    text = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', text)
    text = re.sub(r'(?<![\*\w])\*([^*\n]+)\*(?!\w)', r'<em>\1</em>', text)

    def link(m):
        label, url = m.group(1), rewrite_link(m.group(2))
        return f'<a href="{html.escape(url, quote=True)}">{label}</a>'

    text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', link, text)
    for idx, span in enumerate(spans):
        text = text.replace('\x00%d\x00' % idx, span)
    return text


def render_code(code, lang):
    lang = (lang or '').strip().lower()
    inner = highlight_velo(code) if lang in ('', 'velo') else escape(code)
    return (f'<div class="code-wrap"><button class="copy" type="button" '
            f'aria-label="Copy">Copy</button>'
            f'<pre class="code"><code>{inner}</code></pre></div>')


def split_row(row):
    row = row.strip()
    if row.startswith('|'):
        row = row[1:]
    if row.endswith('|'):
        row = row[:-1]
    cells = re.split(r'(?<!\\)\|', row)
    return [c.strip().replace('\\|', '|') for c in cells]


def render_table(header, rows):
    out = ['<div class="table-wrap"><table><thead><tr>']
    for h in split_row(header):
        out.append(f'<th>{inline(h)}</th>')
    out.append('</tr></thead><tbody>')
    for r in rows:
        out.append('<tr>')
        for c in split_row(r):
            out.append(f'<td>{inline(c)}</td>')
        out.append('</tr>')
    out.append('</tbody></table></div>')
    return ''.join(out)


MARKER_RE = re.compile(r'^(\s*)(\d+\.|[-*])\s+(.*)$')


def is_block_start(line):
    s = line.lstrip()
    return bool(
        s.startswith('```')
        or re.match(r'^#{1,6}\s', s)
        or re.match(r'^(-{3,}|\*{3,})\s*$', line)
        or s.startswith('>')
        or MARKER_RE.match(line)
        or s.startswith('|')
    )


def parse_list(lines, i):
    items = []
    while i < len(lines):
        m = MARKER_RE.match(lines[i])
        if not m:
            break
        indent = len(m.group(1))
        cont = indent + len(m.group(2)) + 1
        buf = [m.group(3)]
        i += 1
        while i < len(lines):
            ln = lines[i]
            if ln.strip() == '':
                buf.append('')
                i += 1
                continue
            lead = len(ln) - len(ln.lstrip())
            nm = MARKER_RE.match(ln)
            if nm and len(nm.group(1)) <= indent:
                break
            if lead < cont and not (nm and len(nm.group(1)) > indent):
                break
            buf.append(ln[cont:] if len(ln) >= cont else ln.lstrip())
            i += 1
        while buf and buf[-1] == '':
            buf.pop()
        inner = ''.join(parse_blocks(buf))
        one = re.match(r'^<p>(.*)</p>$', inner, re.S)
        if one and '<p>' not in one.group(1):
            inner = one.group(1)
        items.append(f'<li>{inner}</li>')
    return items, i


def parse_blocks(lines):
    parts = []
    i, n = 0, len(lines)
    while i < n:
        line = lines[i]
        if line.strip() == '':
            i += 1
            continue
        if line.lstrip().startswith('```'):
            lang = line.lstrip()[3:].strip()
            i += 1
            code = []
            while i < n and not lines[i].lstrip().startswith('```'):
                code.append(lines[i])
                i += 1
            i += 1
            parts.append(render_code('\n'.join(code), lang))
            continue
        m = re.match(r'^(#{1,6})\s+(.*)$', line)
        if m:
            level, raw = len(m.group(1)), m.group(2).strip()
            parts.append(f'<h{level} id="{slugify(raw)}">{inline(raw)}</h{level}>')
            i += 1
            continue
        if re.match(r'^(-{3,}|\*{3,})\s*$', line):
            parts.append('<hr>')
            i += 1
            continue
        if line.lstrip().startswith('|') and i + 1 < n \
                and re.match(r'^\s*\|?[\s:|-]+\|?\s*$', lines[i + 1]) and '-' in lines[i + 1]:
            header = line
            i += 2
            rows = []
            while i < n and lines[i].lstrip().startswith('|'):
                rows.append(lines[i])
                i += 1
            parts.append(render_table(header, rows))
            continue
        if line.lstrip().startswith('>'):
            bq = []
            while i < n and lines[i].lstrip().startswith('>'):
                bq.append(re.sub(r'^\s*>\s?', '', lines[i]))
                i += 1
            parts.append('<blockquote>' + ''.join(parse_blocks(bq)) + '</blockquote>')
            continue
        if MARKER_RE.match(line):
            ordered = bool(re.match(r'^\s*\d+\.', line))
            items, i = parse_list(lines, i)
            tag = 'ol' if ordered else 'ul'
            parts.append(f'<{tag}>' + ''.join(items) + f'</{tag}>')
            continue
        para = [line.strip()]
        i += 1
        while i < n and lines[i].strip() != '' and not is_block_start(lines[i]):
            para.append(lines[i].strip())
            i += 1
        parts.append(f'<p>{inline(" ".join(para))}</p>')
    return parts


def md_to_html(md):
    return '\n'.join(parse_blocks(md.split('\n')))


def strip_footer_nav(md):
    """Drop a trailing hand-written `--- / [Previous …] | [Next …]` block; the
    shell provides paging. Only strips when a nav line is actually present."""
    lines = md.rstrip('\n').split('\n')
    i = len(lines)
    while i > 0:
        s = lines[i - 1].strip()
        if s == '' or '[Previous:' in s or '[Next:' in s or re.match(r'^-{3,}$', s):
            i -= 1
        else:
            break
    tail = lines[i:]
    if any('[Previous:' in l or '[Next:' in l for l in tail):
        return '\n'.join(lines[:i]).rstrip('\n') + '\n'
    return md


def first_heading(md):
    for line in md.split('\n'):
        m = re.match(r'^#\s+(.*)$', line)
        if m:
            return m.group(1).strip()
    return "Velo Lang"


def strip_tags(text):
    return re.sub(r'<[^>]+>', '', text)


def extract_toc(body_html):
    toc = []
    for m in re.finditer(r'<h([23]) id="([^"]+)">(.*?)</h\1>', body_html, re.S):
        toc.append((int(m.group(1)), strip_tags(m.group(3)).strip(), m.group(2)))
    return toc


def snippet(body_html):
    for m in re.finditer(r'<p>(.*?)</p>', body_html, re.S):
        text = re.sub(r'\s+', ' ', strip_tags(m.group(1)).strip())
        if len(text) > 20:
            return text[:170]
    return ""


# --------------------------------------------------------------------------- #
# Navigation model                                                            #
# --------------------------------------------------------------------------- #

def build_order(available_stems):
    sections = [(title, [s for s in stems if s in available_stems])
                for title, stems in SECTIONS]
    listed = {s for _, stems in SECTIONS for s in stems}
    leftover = sorted(s for s in available_stems if s not in listed)
    if leftover:
        print(f"WARNING: chapters not in SECTIONS (appended to 'Other'): "
              f"{', '.join(leftover)}")
        sections.append(("Other", leftover))
    sections = [(t, s) for t, s in sections if s]
    flat = [(t, s) for t, stems in sections for s in stems]
    return sections, flat


def nav_label(stem, titles):
    return SHORT_TITLES.get(stem, titles[stem])


def sidebar_html(sections, titles, active_stem):
    # active_stem is None on the landing page — which serves as the Introduction.
    out = ['<nav class="side-nav" aria-label="Documentation">']
    for gi, (title, stems) in enumerate(sections):
        out.append(f'<div class="side-group"><span class="side-group-label">'
                   f'{escape(title)}</span><ul>')
        if gi == 0:
            home_cls = ' class="active"' if active_stem is None else ''
            out.append(f'<li><a href="index.html"{home_cls}>Introduction</a></li>')
        for stem in stems:
            cls = ' class="active"' if stem == active_stem else ''
            out.append(f'<li><a href="{stem}.html"{cls}>'
                       f'{escape(nav_label(stem, titles))}</a></li>')
        out.append('</ul></div>')
    out.append('</nav>')
    return ''.join(out)


def toc_html(toc):
    if len([t for t in toc if t[0] == 2]) < 2:
        return ''
    out = ['<nav class="toc" aria-label="On this page">'
           '<span class="toc-label">On this page</span><ul>']
    for level, text, slug in toc:
        out.append(f'<li class="lvl{level}"><a href="#{slug}">{escape(text)}</a></li>')
    out.append('</ul></nav>')
    return ''.join(out)


# --------------------------------------------------------------------------- #
# Page shells                                                                  #
# --------------------------------------------------------------------------- #

def head(title):
    return f'''<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{escape(title)} — Velo Lang</title>
    <link rel="stylesheet" href="style.css">
    <link rel="icon" href="favicon.svg" type="image/svg+xml">
    <script src="search-index.js" defer></script>
    <script src="app.js" defer></script>
</head>
<body>'''


def topbar():
    return f'''    <header class="topbar">
        <div class="topbar-inner">
            <button class="menu-btn" aria-label="Menu">☰</button>
            <a class="brand" href="index.html">{badge()}<span class="brand-name">Velo</span></a>
            <div class="search">
                <input type="search" id="q" placeholder="Search…  ( / )"
                       autocomplete="off" spellcheck="false" aria-label="Search">
                <div class="search-results" id="results"></div>
            </div>
            <div class="topbar-right">
                <button class="theme-btn" aria-label="Toggle theme" title="Toggle theme"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 3 a9 9 0 0 0 0 18 Z"/></svg></button>
                <a class="gh" href="{REPO_URL}">GitHub</a>
            </div>
        </div>
    </header>'''


def foot():
    return f'''    <footer class="site-footer">
        <div class="site-footer-inner">
            <span>Velo Lang — MIT licensed.</span>
            <span><a href="{REPO_URL}">Source</a> · <a href="{REPO_URL}/blob/main/LICENSE">License</a></span>
        </div>
    </footer>
</body>
</html>'''


def chapter_page(stem, md, sections, flat, titles):
    body = md_to_html(strip_footer_nav(md))
    toc = extract_toc(body)
    section_label = next((t for t, stems in sections if stem in stems), "")
    idx = next(i for i, (_, s) in enumerate(flat) if s == stem)
    prev_item = flat[idx - 1] if idx > 0 else None
    next_item = flat[idx + 1] if idx < len(flat) - 1 else None

    def pager(item, kind):
        if not item:
            return '<span class="pager-gap"></span>'
        _, st = item
        arrow = '←' if kind == 'prev' else '→'
        label = 'Previous' if kind == 'prev' else 'Next'
        return (f'<a class="pager {kind}" href="{st}.html">'
                f'<span class="pager-dir">{arrow} {label}</span>'
                f'<span class="pager-title">{escape(titles[st])}</span></a>')

    return f'''{head(titles[stem])}
{topbar()}
    <div class="layout">
        <aside class="sidebar" id="sidebar">{sidebar_html(sections, titles, stem)}</aside>
        <div class="sidebar-scrim" id="scrim"></div>
        <main class="main">
            <article class="content">
                <p class="crumb">{escape(section_label)}</p>
{body}
                <nav class="pager-row" aria-label="Chapter">
                    {pager(prev_item, 'prev')}
                    {pager(next_item, 'next')}
                </nav>
            </article>
            {foot()}
        </main>
        <aside class="rightbar">{toc_html(toc)}</aside>
    </div>'''


def index_page(sections, flat, titles):
    steps = []
    for n, (stem, hook) in enumerate(LEARNING_PATH, 1):
        if stem not in titles:
            continue
        steps.append(
            f'<a class="step" href="{stem}.html"><span class="step-n">{n}</span>'
            f'<span class="step-body"><span class="step-title">{escape(titles[stem])}</span>'
            f'<span class="step-hook">{escape(hook)}</span></span></a>')
    explore = ''.join(
        f'<a class="chip" href="{stem}.html">{escape(label)}</a>'
        for stem, label in EXPLORE if stem in titles)

    hello = ('Terminal term = new Terminal()\n\n'
             'let name = "Velo"\n'
             'term.println("Hello, $name!")')

    features = [
        ("Strict typing", "every value has a checked type"),
        ("Functional", "higher-order functions and closures"),
        ("Pattern matching", "when expressions, enum sum types"),
        ("Concurrency", "actors with async / await"),
        ("Error handling", "try / catch / throw"),
        ("Generics", "classes, functions, extensions"),
        ("Value types", "immutable data classes"),
        ("Embeddable", "one-call JVM integration"),
    ]
    feature_html = ''.join(
        f'<li><b>{escape(t)}</b> — {escape(d)}</li>' for t, d in features)

    return f'''{head("Documentation")}
{topbar()}
    <div class="layout">
        <aside class="sidebar" id="sidebar">{sidebar_html(sections, titles, None)}</aside>
        <div class="sidebar-scrim" id="scrim"></div>
        <main class="main">
            <div class="content home">
                <section class="hero">
                    {badge("logo hero-logo")}
                    <h1>Velo</h1>
                    <p class="tagline">{escape(TAGLINE)}</p>
                    <p class="lead">{escape(LEAD)}</p>
                    <div class="hero-actions">
                        <a class="btn primary" href="02-language-basics.html">Start learning</a>
                        <a class="btn" href="30-llm-guide.html">Cheat-sheet</a>
                    </div>
                </section>

                <div class="code-wrap hero-code"><pre class="code"><code>{highlight_velo(hello)}</code></pre></div>

                <section class="features">
                    <h2 id="highlights">Highlights</h2>
                    <ul class="feature-list">{feature_html}</ul>
                </section>

                <section class="path">
                    <h2 id="start-here">Start here · ~30 minutes</h2>
                    <p class="section-sub">Read these eight in order and you can write real Velo. Everything else is reference in the sidebar.</p>
                    <div class="steps">{''.join(steps)}</div>
                </section>

                <section class="explore">
                    <h2 id="keep-going">Keep going</h2>
                    <div class="chips">{explore}</div>
                </section>
            </div>
            {foot()}
        </main>
    </div>'''


# --------------------------------------------------------------------------- #
# Assets                                                                       #
# --------------------------------------------------------------------------- #

def build_search_index(pages):
    entries = []
    for stem, title, section, body in pages:
        toc = extract_toc(body)
        entries.append({
            "t": title, "u": f"{stem}.html", "s": section,
            "h": [[text, slug] for _, text, slug in toc],
            "x": snippet(body),
        })
    return "window.VELO_SEARCH = " + json.dumps(entries, ensure_ascii=False) + ";\n"


def app_js():
    return r'''(function () {
  "use strict";
  var root = document.documentElement;
  try { var s = localStorage.getItem("velo-theme"); if (s) root.setAttribute("data-theme", s); } catch (e) {}

  function toggleTheme() {
    var cur = root.getAttribute("data-theme");
    if (!cur) cur = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    var next = cur === "dark" ? "light" : "dark";
    root.setAttribute("data-theme", next);
    try { localStorage.setItem("velo-theme", next); } catch (e) {}
  }
  function openSidebar(open) {
    var sb = document.getElementById("sidebar"), sc = document.getElementById("scrim");
    if (sb) sb.classList.toggle("open", open);
    if (sc) sc.classList.toggle("show", open);
  }

  // Reveal the active chapter in the sidebar (it may sit below the fold).
  (function () {
    var act = document.querySelector(".side-nav a.active"), sb = document.getElementById("sidebar");
    if (!act || !sb) return;
    var r = act.getBoundingClientRect(), sr = sb.getBoundingClientRect();
    if (r.top < sr.top + 8 || r.bottom > sr.bottom - 8) {
      sb.scrollTop += (r.top - sr.top) - (sb.clientHeight - act.offsetHeight) / 2;
    }
  })();

  document.addEventListener("click", function (e) {
    if (e.target.closest(".theme-btn")) { toggleTheme(); return; }
    if (e.target.closest(".menu-btn")) { openSidebar(true); return; }
    if (e.target.id === "scrim") { openSidebar(false); return; }
    var copy = e.target.closest(".copy");
    if (copy) {
      var code = copy.parentElement.querySelector("code");
      if (code && navigator.clipboard) {
        navigator.clipboard.writeText(code.innerText);
        var old = copy.textContent; copy.textContent = "Copied";
        setTimeout(function () { copy.textContent = old; }, 1200);
      }
    }
  });

  // ---- Search --------------------------------------------------------------
  var q = document.getElementById("q"), results = document.getElementById("results");
  var idx = window.VELO_SEARCH || [];
  function score(e, n) {
    var t = e.t.toLowerCase();
    if (t.indexOf(n) === 0) return 100;
    if (t.indexOf(n) >= 0) return 60;
    for (var i = 0; i < e.h.length; i++) if (e.h[i][0].toLowerCase().indexOf(n) >= 0) return 40;
    if ((e.x || "").toLowerCase().indexOf(n) >= 0) return 20;
    return 0;
  }
  function esc(x){ return x.replace(/[&<>]/g, function(c){ return {"&":"&amp;","<":"&lt;",">":"&gt;"}[c]; }); }
  function search() {
    var n = (q.value || "").trim().toLowerCase();
    if (n.length < 2) { results.classList.remove("show"); results.innerHTML = ""; return; }
    var hits = [];
    for (var i = 0; i < idx.length; i++) { var sc = score(idx[i], n); if (sc > 0) hits.push({ e: idx[i], s: sc }); }
    hits.sort(function (a, b) { return b.s - a.s; });
    if (!hits.length) { results.innerHTML = '<div class="no-hits">No matches.</div>'; results.classList.add("show"); return; }
    var out = "";
    hits.slice(0, 8).forEach(function (h) {
      var e = h.e, sub = e.s;
      for (var j = 0; j < e.h.length; j++) if (e.h[j][0].toLowerCase().indexOf(n) >= 0) { sub = e.h[j][0]; break; }
      out += '<a class="hit" href="' + e.u + '"><span class="hit-title">' + esc(e.t) +
             '</span><span class="hit-sub">' + esc(sub) + '</span></a>';
    });
    results.innerHTML = out; results.classList.add("show");
  }
  if (q) {
    q.addEventListener("input", search);
    q.addEventListener("focus", search);
    q.addEventListener("keydown", function (e) { if (e.key === "Escape") { results.classList.remove("show"); q.blur(); } });
    document.addEventListener("click", function (e) { if (!e.target.closest(".search")) results.classList.remove("show"); });
    document.addEventListener("keydown", function (e) {
      if (e.key === "/" && document.activeElement !== q && !/^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName)) { e.preventDefault(); q.focus(); }
    });
  }

  // ---- On-this-page scrollspy (position-based; no rAF so it works in any tab)
  var tocLinks = [].slice.call(document.querySelectorAll(".toc a"));
  if (tocLinks.length) {
    var map = {};
    tocLinks.forEach(function (a) { map[decodeURIComponent(a.getAttribute("href").slice(1))] = a; });
    var heads = [].slice.call(document.querySelectorAll(".content h2, .content h3")).filter(function (h) { return h.id && map[h.id]; });
    var offsets = [];
    function measure() { offsets = heads.map(function (h) { return h.getBoundingClientRect().top + window.scrollY; }); }
    function spy() {
      var y = window.scrollY + 100, idx = 0;
      for (var i = 0; i < offsets.length; i++) { if (offsets[i] <= y) idx = i; else break; }
      if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4) idx = heads.length - 1;
      tocLinks.forEach(function (a) { a.classList.remove("active"); });
      if (heads[idx] && map[heads[idx].id]) map[heads[idx].id].classList.add("active");
    }
    measure(); spy();
    window.addEventListener("scroll", spy, { passive: true });
    window.addEventListener("resize", function () { measure(); spy(); });
    window.addEventListener("load", function () { measure(); spy(); });
  }
})();
'''


def generate_css():
    return r''':root {
    --bg: #ffffff;
    --surface: #f5f7f8;
    --surface-2: #eef1f3;
    --text: #1b2127;
    --text-muted: #5b6670;
    --heading: #10151a;
    --accent: #0f7d38;
    --accent-hover: #0b6630;
    --accent-soft: rgba(15, 125, 56, 0.09);
    --border: #e2e6ea;
    --border-soft: #edf0f2;
    --brand-grad: linear-gradient(135deg, #0b8a3a 0%, #4fb023 52%, #b4d80a 100%);
    --kw: #8a3ffc;
    --ty: #0b62c4;
    --st: #0a7d33;
    --nu: #b3541e;
    --cm: #94a0a8;
    --op: #5b6670;
    --shadow: 0 6px 22px rgba(16, 24, 32, 0.11);
    --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    --font-mono: "SF Mono", "JetBrains Mono", "Fira Code", "Roboto Mono", Menlo, Consolas, monospace;
    --sidebar-w: 260px;
    --rightbar-w: 220px;
    --content-w: 790px;
    --topbar-h: 55px;
}

:root[data-theme="dark"] {
    --bg: #0e1116;
    --surface: #161b22;
    --surface-2: #1b222b;
    --text: #cdd5dd;
    --text-muted: #8a949e;
    --heading: #eef2f6;
    --accent: #48c76a;
    --accent-hover: #63d581;
    --accent-soft: rgba(72, 199, 106, 0.13);
    --border: #262d36;
    --border-soft: #1d232b;
    --kw: #c297ff;
    --ty: #6cb0ff;
    --st: #7ee787;
    --nu: #ffab70;
    --cm: #667079;
    --op: #8a949e;
    --shadow: 0 8px 26px rgba(0, 0, 0, 0.5);
}

@media (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) {
        --bg: #0e1116; --surface: #161b22; --surface-2: #1b222b;
        --text: #cdd5dd; --text-muted: #8a949e; --heading: #eef2f6;
        --accent: #48c76a; --accent-hover: #63d581; --accent-soft: rgba(72,199,106,0.13);
        --border: #262d36; --border-soft: #1d232b;
        --kw: #c297ff; --ty: #6cb0ff; --st: #7ee787; --nu: #ffab70; --cm: #667079; --op: #8a949e;
        --shadow: 0 8px 26px rgba(0,0,0,0.5);
    }
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

html { font-size: 15.4px; -webkit-text-size-adjust: 100%; scroll-behavior: smooth; scroll-padding-top: 72px; }
body { font-family: var(--font-sans); color: var(--text); background: var(--bg); line-height: 1.5; -webkit-font-smoothing: antialiased; }
a { color: var(--accent); text-decoration: none; }
a:hover { color: var(--accent-hover); text-decoration: underline; }
code { font-family: var(--font-mono); font-size: 0.86em; }

/* ---- Brand mark ---- */
.logo { display: inline-grid; place-items: center; width: 26px; height: 26px; border-radius: 7px; background: var(--brand-grad); flex: none; }
.logo svg { width: 68%; height: 68%; stroke: #fff; stroke-width: 3; fill: none; }

/* ---- Top bar ---- */
.topbar { position: sticky; top: 0; z-index: 50; background: color-mix(in srgb, var(--bg) 90%, transparent); backdrop-filter: saturate(1.4) blur(8px); border-bottom: 1px solid var(--border); }
.topbar-inner { max-width: 1320px; margin: 0 auto; height: var(--topbar-h); padding: 0 16px; display: flex; align-items: center; gap: 14px; }
.brand { display: flex; align-items: center; gap: 8px; font-weight: 700; font-size: 1.02rem; letter-spacing: -0.02em; color: var(--heading); }
.brand:hover { text-decoration: none; }
.brand-name { line-height: 1; }
.menu-btn { display: none; background: none; border: none; font-size: 1.2rem; color: var(--text); cursor: pointer; padding: 2px 6px; }
.topbar-right { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.gh { font-size: 0.85rem; font-weight: 500; color: var(--text-muted); }
.theme-btn { width: 32px; height: 32px; border: 1px solid var(--border); border-radius: 7px; background: var(--surface); cursor: pointer; color: var(--text-muted); display: inline-grid; place-items: center; padding: 0; }
.theme-btn:hover { color: var(--accent); border-color: var(--accent); }
.theme-btn svg { width: 15px; height: 15px; display: block; }
.theme-btn svg circle { fill: none; stroke: currentColor; stroke-width: 2; }
.theme-btn svg path { fill: currentColor; stroke: none; }

/* ---- Search ---- */
.search { position: relative; flex: 1; max-width: 420px; }
.search input { width: 100%; height: 32px; padding: 0 11px; border: 1px solid var(--border); border-radius: 7px; background: var(--surface); color: var(--text); font-family: var(--font-sans); font-size: 0.85rem; }
.search input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.search-results { display: none; position: absolute; top: 40px; left: 0; right: 0; background: var(--bg); border: 1px solid var(--border); border-radius: 9px; box-shadow: var(--shadow); overflow: hidden; max-height: 72vh; overflow-y: auto; }
.search-results.show { display: block; }
.hit { display: block; padding: 8px 13px; border-bottom: 1px solid var(--border-soft); }
.hit:hover { background: var(--accent-soft); text-decoration: none; }
.hit:last-child { border-bottom: none; }
.hit-title { display: block; font-weight: 600; color: var(--heading); font-size: 0.88rem; }
.hit-sub { display: block; font-size: 0.76rem; color: var(--text-muted); }
.no-hits { padding: 12px 13px; color: var(--text-muted); font-size: 0.85rem; }

/* ---- Layout ---- */
.layout { max-width: 1320px; margin: 0 auto; display: grid; grid-template-columns: var(--sidebar-w) minmax(0, 1fr) var(--rightbar-w); align-items: start; }
.sidebar { position: sticky; top: var(--topbar-h); align-self: start; height: calc(100vh - var(--topbar-h)); overflow-y: auto; padding: 18px 10px 32px 16px; border-right: 1px solid var(--border-soft); }
.main { min-width: 0; }
.rightbar { position: sticky; top: var(--topbar-h); align-self: start; height: calc(100vh - var(--topbar-h)); overflow-y: auto; padding: 26px 16px 32px 6px; }
.sidebar-scrim { display: none; }

/* ---- Sidebar nav (dense) ---- */
.side-group { margin-bottom: 14px; }
.side-group-label { display: block; font-size: 0.68rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); padding: 0 8px; margin-bottom: 3px; }
.side-nav ul { list-style: none; }
.side-nav li a { display: block; padding: 3px 8px; border-radius: 6px; color: var(--text); font-size: 0.83rem; line-height: 1.35; border-left: 2px solid transparent; }
.side-nav li a:hover { background: var(--surface); text-decoration: none; }
.side-nav li a.active { color: var(--accent); font-weight: 600; background: var(--accent-soft); border-left-color: var(--accent); }

/* ---- Content ---- */
.content { max-width: var(--content-w); margin: 0 auto; padding: 26px 30px 20px; }
.content.home { max-width: 780px; }
.crumb { font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--accent); margin: 0 0 4px; }

.content h1 { font-size: 1.7rem; font-weight: 750; letter-spacing: -0.02em; color: var(--heading); margin: 2px 0 14px; line-height: 1.2; }
.content h2 { font-size: 1.22rem; font-weight: 680; letter-spacing: -0.01em; color: var(--heading); margin: 30px 0 10px; padding-bottom: 5px; border-bottom: 1px solid var(--border-soft); scroll-margin-top: 72px; }
.content h3 { font-size: 1.03rem; font-weight: 650; color: var(--heading); margin: 20px 0 7px; scroll-margin-top: 72px; }
.content h4 { font-size: 0.92rem; font-weight: 650; color: var(--heading); margin: 16px 0 6px; }
.content p { margin: 9px 0; }
.content ul, .content ol { margin: 9px 0 9px 1.35em; }
.content li { margin: 3px 0; }
.content li > ul, .content li > ol { margin: 3px 0 3px 1em; }
.content a { overflow-wrap: anywhere; }
.content strong { color: var(--heading); font-weight: 650; }
.content :not(pre) > code { background: var(--surface-2); padding: 1px 5px; border-radius: 4px; font-size: 0.84em; border: 1px solid var(--border-soft); }

/* ---- Code ---- */
.code-wrap { position: relative; margin: 12px 0; }
.content pre.code { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 12px 14px; overflow-x: auto; line-height: 1.5; font-size: 0.8rem; }
.content pre.code code { font-size: inherit; background: none; border: none; padding: 0; }
.copy { position: absolute; top: 6px; right: 6px; z-index: 2; border: 1px solid var(--border); background: var(--bg); color: var(--text-muted); border-radius: 5px; font-size: 0.68rem; padding: 2px 7px; cursor: pointer; opacity: 0; transition: opacity 0.12s; }
.code-wrap:hover .copy { opacity: 1; }
.copy:hover { color: var(--accent); border-color: var(--accent); }

/* ---- Callout / table ---- */
.content blockquote { border-left: 3px solid var(--accent); background: var(--accent-soft); padding: 1px 14px; margin: 12px 0; border-radius: 0 7px 7px 0; }
.content blockquote p { margin: 7px 0; }
.content hr { border: none; border-top: 1px solid var(--border); margin: 26px 0; }
.table-wrap { overflow-x: auto; margin: 12px 0; border: 1px solid var(--border); border-radius: 8px; }
.content table { border-collapse: collapse; width: 100%; font-size: 0.85rem; }
.content th, .content td { border-bottom: 1px solid var(--border-soft); padding: 6px 11px; text-align: left; vertical-align: top; }
.content tr:last-child td { border-bottom: none; }
.content th { background: var(--surface); font-weight: 650; color: var(--heading); border-bottom: 1px solid var(--border); }
.content td code, .content th code { background: none; padding: 0; border: none; }

/* ---- On this page ---- */
.toc { position: sticky; top: 82px; font-size: 0.8rem; }
.toc-label { display: block; font-size: 0.68rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); margin-bottom: 8px; }
.toc ul { list-style: none; border-left: 1px solid var(--border); }
.toc li a { display: block; padding: 3px 0 3px 11px; margin-left: -1px; color: var(--text-muted); border-left: 2px solid transparent; line-height: 1.3; }
.toc li.lvl3 a { padding-left: 22px; font-size: 0.77rem; }
.toc li a:hover { color: var(--text); text-decoration: none; }
.toc li a.active { color: var(--accent); border-left-color: var(--accent); font-weight: 600; }

/* ---- Pager ---- */
.pager-row { display: flex; justify-content: space-between; gap: 12px; margin: 36px 0 4px; padding-top: 18px; border-top: 1px solid var(--border-soft); }
.pager-gap { flex: 1; }
.pager { display: flex; flex-direction: column; gap: 2px; padding: 9px 13px; border: 1px solid var(--border); border-radius: 8px; max-width: 49%; }
.pager:hover { border-color: var(--accent); background: var(--accent-soft); text-decoration: none; }
.pager.next { text-align: right; margin-left: auto; align-items: flex-end; }
.pager-dir { font-size: 0.72rem; color: var(--text-muted); font-weight: 600; }
.pager-title { font-weight: 640; color: var(--heading); font-size: 0.9rem; }

/* ---- Footer ---- */
.site-footer { border-top: 1px solid var(--border-soft); margin-top: 16px; }
.site-footer-inner { max-width: var(--content-w); margin: 0 auto; padding: 18px 30px 34px; display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; color: var(--text-muted); font-size: 0.8rem; }

/* ---- Home ---- */
.hero { padding: 22px 0 4px; }
.hero-logo { width: 52px; height: 52px; border-radius: 13px; margin-bottom: 14px; }
.hero h1 { font-size: 2.5rem; font-weight: 800; letter-spacing: -0.03em; color: var(--heading); margin-bottom: 4px; line-height: 1; }
.hero .tagline { font-size: 1.05rem; font-weight: 600; margin-bottom: 10px; background: var(--brand-grad); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; width: max-content; }
.hero .lead { font-size: 0.98rem; line-height: 1.6; color: var(--text-muted); max-width: 560px; margin-bottom: 18px; }
.hero-actions { display: flex; gap: 9px; flex-wrap: wrap; }
.btn { display: inline-block; padding: 8px 16px; border-radius: 8px; border: 1px solid var(--border); font-weight: 600; font-size: 0.88rem; color: var(--text); }
.btn:hover { border-color: var(--accent); color: var(--accent); text-decoration: none; }
.btn.primary { background: var(--accent); border-color: var(--accent); color: #fff; }
.btn.primary:hover { background: var(--accent-hover); color: #fff; }
.hero-code { margin: 22px 0 30px; max-width: 560px; }

.features { margin-bottom: 30px; }
.features h2, .path h2, .explore h2 { border: none; padding: 0; margin-bottom: 10px; }
.feature-list { list-style: none; margin: 0; display: grid; grid-template-columns: repeat(2, 1fr); gap: 5px 24px; }
.feature-list li { font-size: 0.9rem; color: var(--text-muted); }
.feature-list b { color: var(--heading); font-weight: 640; }

.path h2, .explore h2 { margin-bottom: 3px; }
.section-sub { color: var(--text-muted); margin: 0 0 14px; max-width: 580px; font-size: 0.92rem; }
.steps { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.step { display: flex; gap: 10px; align-items: center; padding: 9px 12px; border: 1px solid var(--border); border-radius: 9px; }
.step:hover { border-color: var(--accent); background: var(--accent-soft); text-decoration: none; }
.step-n { flex: none; width: 22px; height: 22px; display: grid; place-items: center; border-radius: 50%; background: var(--brand-grad); color: #fff; font-weight: 700; font-size: 0.78rem; }
.step-body { display: flex; flex-direction: column; min-width: 0; }
.step-title { font-weight: 640; color: var(--heading); font-size: 0.9rem; }
.step-hook { font-size: 0.78rem; color: var(--text-muted); line-height: 1.3; }
.explore { margin-top: 26px; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip { border: 1px solid var(--border); border-radius: 999px; padding: 5px 13px; font-size: 0.85rem; font-weight: 550; color: var(--text); }
.chip:hover { border-color: var(--accent); color: var(--accent); background: var(--accent-soft); text-decoration: none; }

/* ---- Syntax ---- */
.kw { color: var(--kw); font-weight: 600; }
.ty { color: var(--ty); }
.st { color: var(--st); }
.nu { color: var(--nu); }
.cm { color: var(--cm); font-style: italic; }
.op { color: var(--op); }

/* ---- Responsive ---- */
@media (max-width: 1080px) {
    .layout { grid-template-columns: var(--sidebar-w) minmax(0, 1fr); }
    .rightbar { display: none; }
}
@media (max-width: 820px) {
    .layout { grid-template-columns: minmax(0, 1fr); }
    .menu-btn { display: block; }
    .sidebar { position: fixed; top: var(--topbar-h); left: 0; z-index: 40; width: 264px; max-width: 82vw; background: var(--bg); border-right: 1px solid var(--border); transform: translateX(-102%); transition: transform 0.2s ease; }
    .sidebar.open { transform: translateX(0); box-shadow: var(--shadow); }
    .sidebar-scrim.show { display: block; position: fixed; inset: var(--topbar-h) 0 0 0; z-index: 39; background: rgba(0,0,0,0.35); }
    .steps { grid-template-columns: 1fr; }
    .gh { display: none; }
}
@media (max-width: 560px) {
    .content { padding: 20px 16px; }
    .hero h1 { font-size: 2rem; }
    .pager { max-width: 100%; }
}
'''


# --------------------------------------------------------------------------- #
# Main                                                                         #
# --------------------------------------------------------------------------- #

def main():
    if os.path.isdir(OUTPUT_DIR):
        shutil.rmtree(OUTPUT_DIR)
    os.makedirs(OUTPUT_DIR)
    open(os.path.join(OUTPUT_DIR, ".nojekyll"), 'w').close()

    with open(os.path.join(OUTPUT_DIR, "favicon.svg"), 'w') as f:
        f.write(FAVICON_SVG)
    with open(os.path.join(OUTPUT_DIR, "style.css"), 'w') as f:
        f.write(generate_css())
    with open(os.path.join(OUTPUT_DIR, "app.js"), 'w') as f:
        f.write(app_js())
    print("Generated: favicon.svg, style.css, app.js")

    chapter_files = sorted(n for n in os.listdir(DOCS_DIR)
                           if re.match(r'^\d+-.*\.md$', n) and n[:-3] not in EXCLUDE)
    stems = [n[:-3] for n in chapter_files]
    raw, titles = {}, {}
    for name in chapter_files:
        with open(os.path.join(DOCS_DIR, name)) as f:
            md = f.read()
        stem = name[:-3]
        raw[stem] = md
        titles[stem] = first_heading(md)

    sections, flat = build_order(set(stems))

    search_pages = []
    for section_title, stem in flat:
        md = raw[stem]
        with open(os.path.join(OUTPUT_DIR, stem + ".html"), 'w') as f:
            f.write(chapter_page(stem, md, sections, flat, titles))
        search_pages.append((stem, titles[stem], section_title, md_to_html(strip_footer_nav(md))))
        print(f"Generated: {stem}.html")

    with open(os.path.join(OUTPUT_DIR, "index.html"), 'w') as f:
        f.write(index_page(sections, flat, titles))
    print("Generated: index.html")

    with open(os.path.join(OUTPUT_DIR, "search-index.js"), 'w') as f:
        f.write(build_search_index(search_pages))
    print("Generated: search-index.js")

    print(f"\nDone! {len(flat) + 5} files in {OUTPUT_DIR}/")


if __name__ == '__main__':
    main()
