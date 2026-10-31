#!/usr/bin/env python3
"""Bundle the interpreter, UI and story files into one self-contained page.

    python3 zork/build.py [fragment-output]

Writes zork/index.html (a complete page you can open from your phone's files), the
Android app's copy in zorkapp/src/main/assets/ (fonts bundled instead of loaded from
Google Fonts, so it works offline) and, if a path is given, the page without the
<html>/<head>/<body> wrapper.
"""
import base64
import json
import pathlib
import re
import sys

here = pathlib.Path(__file__).resolve().parent
page = (here / "src" / "page.html").read_text()
zmachine = (here / "src" / "zmachine.js").read_text()
stories = {
    p.stem: base64.b64encode(p.read_bytes()).decode()
    for p in sorted((here / "stories").glob("*.z3"))
}

fragment = page.replace("/*ZMACHINE*/", zmachine).replace("/*STORIES*/", json.dumps(stories))

LOCAL_FONTS = """<style>
@font-face { font-family: "Courier Prime"; font-weight: 400; src: url(fonts/CourierPrime-Regular.ttf); }
@font-face { font-family: "Courier Prime"; font-weight: 700; src: url(fonts/CourierPrime-Bold.ttf); }
@font-face { font-family: "IM Fell English SC"; font-weight: 400; src: url(fonts/IMFellEnglishSC-Regular.ttf); }
@font-face { font-family: "IBM Plex Sans Condensed"; font-weight: 400; src: url(fonts/IBMPlexSansCondensed-Regular.ttf); }
@font-face { font-family: "IBM Plex Sans Condensed"; font-weight: 500; src: url(fonts/IBMPlexSansCondensed-Medium.ttf); }
@font-face { font-family: "IBM Plex Sans Condensed"; font-weight: 600; src: url(fonts/IBMPlexSansCondensed-SemiBold.ttf); }
</style>
"""


def wrap(body, theme_color=True):
    return (
        '<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
        + ('<meta name="theme-color" content="#12181c">\n' if theme_color else "")
        + "<style>:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}"
        "body{margin:0}[hidden]{display:none!important}</style>\n"
        "</head>\n<body>\n" + body + "\n</body>\n</html>\n"
    )


standalone = wrap(fragment)
(here / "index.html").write_text(standalone)
print(f"wrote {here / 'index.html'} ({len(standalone) // 1024} KB)")

app_page = here.parent / "zorkapp" / "src" / "main" / "assets" / "index.html"
app_fragment = re.sub(r"<!--FONTS-->.*?<!--/FONTS-->\n", lambda m: LOCAL_FONTS, fragment, flags=re.S)
app_page.write_text(wrap(app_fragment, theme_color=False))
print(f"wrote {app_page}")

if len(sys.argv) > 1:
    pathlib.Path(sys.argv[1]).write_text(fragment)
    print(f"wrote {sys.argv[1]}")
