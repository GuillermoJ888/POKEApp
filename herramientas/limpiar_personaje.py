"""Retoques a mano de un personaje ya recortado.
Uso: python limpiar.py imagen.png vista.png  caja...   (caja = modo:x0,y0,x1,y1; modo t=todo, c=claros, r=rayas delgadas)"""
import sys
from PIL import Image
im = Image.open(sys.argv[1]); px = im.load(); w, h = im.size
for caja in sys.argv[3:]:
    modo, nums = caja.split(':')
    x0, y0, x1, y1 = [int(v) for v in nums.split(',')]
    for x in range(max(0, x0), min(w, x1)):
        if modo == 'r':
            y = max(0, y0)
            while y < min(h, y1):
                if px[x, y][3]:
                    f = y
                    while f < h and px[x, f][3]:
                        f += 1
                    if f - y <= 6:
                        for k in range(y, f):
                            px[x, k] = px[x, k][:3] + (0,)
                    y = f
                else:
                    y += 1
            continue
        for y in range(max(0, y0), min(h, y1)):
            r, g, b, a = px[x, y]
            if modo == 't' or (modo == 'c' and 0.3 * r + 0.59 * g + 0.11 * b > 150):
                px[x, y] = (r, g, b, 0)
# solo la pieza más grande
et = {}; mejor = None; mn = 0
for xa in range(w):
    for ya in range(h):
        if px[xa, ya][3] and (xa, ya) not in et:
            pila = [(xa, ya)]; et[(xa, ya)] = (xa, ya); n = 0
            while pila:
                x, y = pila.pop(); n += 1
                for q in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= q[0] < w and 0 <= q[1] < h and px[q][3] and q not in et:
                        et[q] = (xa, ya); pila.append(q)
            if n > mn:
                mejor, mn = (xa, ya), n
for q, r in et.items():
    if r != mejor:
        px[q] = px[q][:3] + (0,)
im = im.crop(im.getbbox()); im.save(sys.argv[1]); print(im.size)
v = Image.new('RGBA', im.size, (255, 0, 255, 255)); v.alpha_composite(im)
v.resize((v.width * 2, v.height * 2), Image.NEAREST).save(sys.argv[2])
