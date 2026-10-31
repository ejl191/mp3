#!/usr/bin/env python3
"""Bundle the interpreter, UI and story files into one self-contained page.

    python3 zork/build.py [fragment-output]

Writes zork/index.html (a complete page you can open from your phone's files) and,
if a path is given, the same page without the <html>/<head>/<body> wrapper.
"""
import base64
import json
import pathlib
import sys

here = pathlib.Path(__file__).resolve().parent
page = (here / "src" / "page.html").read_text()
zmachine = (here / "src" / "zmachine.js").read_text()
stories = {
    p.stem: base64.b64encode(p.read_bytes()).decode()
    for p in sorted((here / "stories").glob("*.z3"))
}

fragment = page.replace("/*ZMACHINE*/", zmachine).replace("/*STORIES*/", json.dumps(stories))

standalone = (
    '<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n'
    '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
    '<meta name="theme-color" content="#12181c">\n'
    "<style>:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}"
    "body{margin:0}[hidden]{display:none!important}</style>\n"
    "</head>\n<body>\n" + fragment + "\n</body>\n</html>\n"
)
(here / "index.html").write_text(standalone)
print(f"wrote {here / 'index.html'} ({len(standalone) // 1024} KB)")

if len(sys.argv) > 1:
    pathlib.Path(sys.argv[1]).write_text(fragment)
    print(f"wrote {sys.argv[1]}")
