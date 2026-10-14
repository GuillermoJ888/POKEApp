"""Quita el fondo liso (color de la esquina) de una medalla y la recorta. Uso: python medalla_img.py entrada salida"""
import sys
from collections import deque
from PIL import Image, ImageFilter
im = Image.open(sys.argv[1]).convert('RGBA'); w, h = im.size; px = im.load()
fr, fg, fb = px[3, h // 2][:3]
TOL = int(sys.argv[3]) if len(sys.argv) > 3 else 46
def fondo(x, y):
    r, g, b, _ = px[x, y]
    return abs(r - fr) + abs(g - fg) + abs(b - fb) < TOL
visto = [[False] * h for _ in range(w)]
cola = deque([(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)])
while cola:
    x, y = cola.popleft()
    if x < 0 or y < 0 or x >= w or y >= h or visto[x][y] or not fondo(x, y):
        continue
    visto[x][y] = True
    cola.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
m = Image.new('L', (w, h), 255); mp = m.load()
for x in range(w):
    for y in range(h):
        if visto[x][y]:
            mp[x, y] = 0
m = m.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.7))
im.putalpha(m); im = im.crop(im.getbbox())
im.thumbnail((420, 420), Image.LANCZOS)
im.save(sys.argv[2]); print(sys.argv[2], im.size)
