#!/usr/bin/env python3
"""
Generate the Velo documentation website from the Markdown in ../docs.

The docs are the single source of truth: this script converts every
docs/NN-name.md chapter into a styled, standalone HTML page (with Velo syntax
highlighting) and builds the landing page from docs/README.md. Inter-chapter
`.md` links are rewritten to `.html`, so the site mirrors the docs exactly and
cannot drift from them.

Pure standard library — no third-party packages — so it runs in CI under a bare
`python3` with no install step.
"""

import os
import re
import html
import shutil

SITE_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.dirname(SITE_DIR)
DOCS_DIR = os.path.join(REPO_DIR, "docs")
OUTPUT_DIR = os.path.join(SITE_DIR, "public")


def escape(text):
    return html.escape(text, quote=False)


# --------------------------------------------------------------------------- #
# Velo syntax highlighting                                                     #
# --------------------------------------------------------------------------- #

def highlight_velo(code):
    """Lightweight syntax highlighting for a block of Velo source."""
    keywords = [
        'import', 'include', 'func', 'class', 'data', 'interface', 'native',
        'ext', 'operator', 'if', 'then', 'else', 'while', 'for', 'in', 'let',
        'new', 'null', 'void', 'true', 'false', 'return', 'break', 'continue',
        'actor', 'async', 'await',
    ]
    types = [
        'byte', 'int', 'long', 'float', 'str', 'bool', 'any',
        'array', 'dict', 'tuple', 'ptr', 'future', 'Self',
    ]

    result_lines = []
    for line in code.split('\n'):
        chars = list(line)
        tokens = []
        i = 0
        while i < len(chars):
            # Comment
            if chars[i] == '#':
                tokens.append(('comment', line[i:]))
                i = len(chars)
            # String
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
            # Number
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
            # Word (keyword, type, or identifier)
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
            # Operators
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
    url = re.sub(r':\d+$', '', url)  # drop a :line suffix
    if url.endswith('.md'):
        base = url[:-3]
        if base == 'README' or base.endswith('/README'):
            url = 'index.html'
        else:
            url = base + '.html'
    return url + anchor


def inline(text):
    """Render inline Markdown: code spans, bold, italic, links."""
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
    if lang in ('', 'velo'):
        return f'<pre class="code"><code>{highlight_velo(code)}</code></pre>'
    return f'<pre class="code"><code>{escape(code)}</code></pre>'


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

        # Fenced code
        if line.lstrip().startswith('```'):
            lang = line.lstrip()[3:].strip()
            i += 1
            code = []
            while i < n and not lines[i].lstrip().startswith('```'):
                code.append(lines[i])
                i += 1
            i += 1  # closing fence
            parts.append(render_code('\n'.join(code), lang))
            continue

        # Heading
        m = re.match(r'^(#{1,6})\s+(.*)$', line)
        if m:
            level, raw = len(m.group(1)), m.group(2).strip()
            parts.append(f'<h{level} id="{slugify(raw)}">{inline(raw)}</h{level}>')
            i += 1
            continue

        # Horizontal rule
        if re.match(r'^(-{3,}|\*{3,})\s*$', line):
            parts.append('<hr>')
            i += 1
            continue

        # Table (header row followed by a |---|--- separator)
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

        # Blockquote
        if line.lstrip().startswith('>'):
            bq = []
            while i < n and lines[i].lstrip().startswith('>'):
                bq.append(re.sub(r'^\s*>\s?', '', lines[i]))
                i += 1
            parts.append('<blockquote>' + ''.join(parse_blocks(bq)) + '</blockquote>')
            continue

        # List
        if MARKER_RE.match(line):
            ordered = bool(re.match(r'^\s*\d+\.', line))
            items, i = parse_list(lines, i)
            tag = 'ol' if ordered else 'ul'
            parts.append(f'<{tag}>' + ''.join(items) + f'</{tag}>')
            continue

        # Paragraph
        para = [line.strip()]
        i += 1
        while i < n and lines[i].strip() != '' and not is_block_start(lines[i]):
            para.append(lines[i].strip())
            i += 1
        parts.append(f'<p>{inline(" ".join(para))}</p>')

    return parts


def md_to_html(md):
    return '\n'.join(parse_blocks(md.split('\n')))


def first_heading(md):
    for line in md.split('\n'):
        m = re.match(r'^#\s+(.*)$', line)
        if m:
            return m.group(1).strip()
    return "Velo Lang"


# --------------------------------------------------------------------------- #
# Page shells                                                                  #
# --------------------------------------------------------------------------- #

def page_html(title, body, active_is_index=False):
    home = '' if active_is_index else '<a href="index.html">Velo Lang</a>'
    return f'''<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{escape(title)} — Velo Lang</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <header class="topbar">
        <div class="topbar-inner">
            {home}
            <a class="gh" href="https://github.com/solkin/velo-lang">GitHub</a>
        </div>
    </header>
    <main class="content">
{body}
    </main>
    <footer class="site-footer">
        <p>by <a href="https://github.com/solkin/velo-lang">Velo Lang</a> &middot;
        <a href="https://github.com/solkin/velo-lang">source</a> &middot;
        <a href="https://github.com/solkin/velo-lang/blob/main/LICENSE">license</a></p>
    </footer>
</body>
</html>'''


def generate_index(readme_md):
    body = f'''        <div class="hero">
            <h1>Velo Lang</h1>
            <p class="tagline">Simple. Embeddable. Yours.</p>
            <p class="lead">A functional, strictly-typed, compilable language that runs on a
            lightweight stack-based virtual machine — strict typing, higher-order functions,
            generics, actors, and easy embedding into JVM applications.</p>
        </div>
{md_to_html(readme_md)}'''
    return page_html("Documentation", body, active_is_index=True)


def generate_chapter(md):
    title = first_heading(md)
    return page_html(title, md_to_html(md))


# --------------------------------------------------------------------------- #
# CSS                                                                          #
# --------------------------------------------------------------------------- #

def generate_css():
    return '''*,
*::before,
*::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
    --bg: #ffffff;
    --bg-code: #f7f7f9;
    --text: #252525;
    --text-muted: #6b7280;
    --accent: #2563eb;
    --accent-hover: #1d4ed8;
    --border: #e5e7eb;
    --kw: #7c3aed;
    --ty: #0891b2;
    --st: #16a34a;
    --nu: #dc2626;
    --cm: #9ca3af;
    --op: #6b7280;
    --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    --font-mono: "SF Mono", "Fira Code", "Fira Mono", "Roboto Mono", Menlo, Consolas, monospace;
}

@media (prefers-color-scheme: dark) {
    :root {
        --bg: #1a1a1e;
        --bg-code: #26262c;
        --text: #e5e5e7;
        --text-muted: #9ca3af;
        --accent: #60a5fa;
        --accent-hover: #93c5fd;
        --border: #34343c;
        --kw: #c4b5fd;
        --ty: #67e8f9;
        --st: #86efac;
        --nu: #fca5a5;
        --cm: #6b7280;
        --op: #9ca3af;
    }
}

html { font-size: 15px; -webkit-font-smoothing: antialiased; }
body { font-family: var(--font-sans); color: var(--text); background: var(--bg); line-height: 1.65; }
a { color: var(--accent); text-decoration: none; }
a:hover { color: var(--accent-hover); text-decoration: underline; }
code { font-family: var(--font-mono); font-size: 0.88em; }

/* ---------- Top bar ---------- */
.topbar { border-bottom: 1px solid var(--border); background: var(--bg); position: sticky; top: 0; z-index: 10; }
.topbar-inner { max-width: 820px; margin: 0 auto; padding: 12px 24px; display: flex; justify-content: space-between; align-items: center; }
.topbar-inner a { font-weight: 600; }
.topbar-inner .gh { font-weight: 400; color: var(--text-muted); }

/* ---------- Content ---------- */
.content { max-width: 820px; margin: 0 auto; padding: 32px 24px 64px; }

.content h1 { font-size: 2rem; font-weight: 700; letter-spacing: -0.02em; margin: 8px 0 20px; }
.content h2 { font-size: 1.4rem; font-weight: 650; margin: 40px 0 14px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.content h3 { font-size: 1.12rem; font-weight: 600; margin: 28px 0 10px; }
.content h4 { font-size: 1rem; font-weight: 600; margin: 22px 0 8px; }

.content p { margin: 12px 0; }
.content ul, .content ol { margin: 12px 0 12px 1.5em; }
.content li { margin: 5px 0; }
.content li > ul, .content li > ol { margin: 5px 0 5px 1.2em; }

.content a { overflow-wrap: anywhere; }

.content :not(pre) > code {
    background: var(--bg-code);
    padding: 1.5px 5px;
    border-radius: 4px;
    font-size: 0.85em;
}

.content pre.code {
    background: var(--bg-code);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 14px 16px;
    overflow-x: auto;
    margin: 14px 0;
    line-height: 1.55;
}
.content pre.code code { font-size: 0.85rem; }

.content blockquote {
    border-left: 3px solid var(--accent);
    background: var(--bg-code);
    padding: 4px 16px;
    margin: 16px 0;
    border-radius: 0 6px 6px 0;
    color: var(--text);
}
.content blockquote p { margin: 8px 0; }

.content hr { border: none; border-top: 1px solid var(--border); margin: 32px 0; }

.table-wrap { overflow-x: auto; margin: 16px 0; }
.content table { border-collapse: collapse; width: 100%; font-size: 0.92rem; }
.content th, .content td { border: 1px solid var(--border); padding: 7px 12px; text-align: left; vertical-align: top; }
.content th { background: var(--bg-code); font-weight: 600; }
.content td code, .content th code { background: none; padding: 0; }

/* ---------- Landing hero ---------- */
.hero { margin-bottom: 8px; }
.hero h1 { font-size: 2.4rem; margin-bottom: 2px; }
.hero .tagline { font-size: 1.1rem; color: var(--text-muted); margin-bottom: 18px; }
.hero .lead { font-size: 1.02rem; line-height: 1.7; margin-bottom: 8px; }

/* ---------- Footer ---------- */
.site-footer { max-width: 820px; margin: 0 auto; padding: 20px 24px 40px; border-top: 1px solid var(--border); color: var(--text-muted); font-size: 0.85rem; }

/* ---------- Syntax highlighting ---------- */
.kw { color: var(--kw); font-weight: 600; }
.ty { color: var(--ty); }
.st { color: var(--st); }
.nu { color: var(--nu); }
.cm { color: var(--cm); font-style: italic; }
.op { color: var(--op); }
'''


# --------------------------------------------------------------------------- #
# Main                                                                         #
# --------------------------------------------------------------------------- #

def main():
    # Fresh output directory (drops the old hand-written example pages).
    if os.path.isdir(OUTPUT_DIR):
        shutil.rmtree(OUTPUT_DIR)
    os.makedirs(OUTPUT_DIR)

    # Serve the files verbatim on GitHub Pages (no Jekyll processing).
    open(os.path.join(OUTPUT_DIR, ".nojekyll"), 'w').close()

    with open(os.path.join(OUTPUT_DIR, "style.css"), 'w') as f:
        f.write(generate_css())
    print("Generated: style.css")

    chapters = sorted(
        name for name in os.listdir(DOCS_DIR)
        if re.match(r'^\d+-.*\.md$', name)
    )

    # Landing page from docs/README.md.
    with open(os.path.join(DOCS_DIR, "README.md")) as f:
        readme = f.read()
    with open(os.path.join(OUTPUT_DIR, "index.html"), 'w') as f:
        f.write(generate_index(readme))
    print("Generated: index.html")

    for name in chapters:
        with open(os.path.join(DOCS_DIR, name)) as f:
            md = f.read()
        out_name = name[:-3] + ".html"
        with open(os.path.join(OUTPUT_DIR, out_name), 'w') as f:
            f.write(generate_chapter(md))
        print(f"Generated: {out_name}")

    print(f"\nDone! Generated {len(chapters) + 2} files in {OUTPUT_DIR}/")


if __name__ == '__main__':
    main()
