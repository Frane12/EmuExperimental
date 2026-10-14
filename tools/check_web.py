#!/usr/bin/env python3
"""Extract standalone inlined JS so node --check can syntax-verify the UI."""
from pathlib import Path
import re
import subprocess
import tempfile
text=(Path(__file__).resolve().parents[1]/"web-prototype/index.html").read_text()
m=re.search(r"<script>\s*(.*?)\s*</script>",text,re.S|re.I)
assert m, "UI inline script missing"
assert text.count("</script>")==1, "unexpected script count"
with tempfile.TemporaryDirectory() as d:
    js=Path(d)/"ui.js"
    js.write_text(m.group(1))
    subprocess.run(["node","--check",str(js)],check=True)
print("UI JS syntax: PASS")
