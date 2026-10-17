"""Verify project-owned XInput bridge exports and packaging sources."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
src=(root/"tools/graphics-tests/DrnasXInput.c").read_text()
d=(root/"tools/graphics-tests/xinput1_3.def").read_text()
pro=(root/"tools/graphics-tests/XInputProbe.c").read_text()
for name in ("XInputGetState", "XInputSetState", "XInputGetCapabilities",
             "XInputEnable", "XInputGetBatteryInformation", "XInputGetKeystroke"):
    assert name in d, name
    assert f" {name}(" in src, name
assert "XInputGetStateEx @100" in d
assert "CreateFileMappingW" in src and "MapViewOfFile" in src
assert "GetProcAddress" in pro and "XInputGetState" in pro
print("DRNAS source-owned XInput bridge checks PASS")
