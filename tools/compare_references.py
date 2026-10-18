#!/usr/bin/env python3
"""Create review artifacts ONLY from screenshots pulled from a running APK.
Usage: python3 tools/compare_references.py /path/to/adb-or-instrumentation/screens
Missing screenshots stay missing; no Compose previews or generated UI substitutes.
"""
from pathlib import Path
import argparse
import shutil
from PIL import Image, ImageChops, ImageDraw

p=argparse.ArgumentParser()
p.add_argument('screenshots',type=Path)
a=p.parse_args()
root=Path(__file__).resolve().parents[1]
for ref in sorted((root/'design/references').glob('*.png')):
    directory=root/'design/review'/ref.stem
    directory.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(ref,directory/'reference.png')
    actual=a.screenshots/ref.name
    if not actual.exists():
        print(f'BLOCKED {ref.stem}: no real APK screenshot')
        continue
    shutil.copyfile(actual,directory/'actual.png')
    original=Image.open(ref).convert('RGB')
    screenshot=Image.open(actual).convert('RGB')
    resized=screenshot.resize(original.size,Image.Resampling.LANCZOS)
    comparison=Image.new('RGB',(original.width*2,original.height+44),'white')
    comparison.paste(original,(0,44));comparison.paste(resized,(original.width,44))
    draw=ImageDraw.Draw(comparison)
    draw.text((15,15),'SUPPLIED REFERENCE',fill='black')
    draw.text((original.width+15,15),'ACTUAL APK SCREENSHOT (scaled for comparison)',fill='black')
    comparison.save(directory/'side-by-side.png')
    ImageChops.difference(original,resized).save(directory/'diff.png')
    print(f'CAPTURED {ref.stem}: manual structural review still required')
