"""Quita el fondo de un personaje pixel art: rellena desde los bordes todo lo que parezca cielo, pasto o camino
y se detiene en el contorno oscuro del personaje. Uso: python recortar.py entrada.png salida.png [vista.png]"""
import sys
from collections import deque
from PIL import Image, ImageFilter

entrada, salida = sys.argv[1], sys.argv[2]
# Ajustes opcionales: CIELO_HASTA = hasta qué fracción de la altura hay cielo; RAYA = alto máximo de rayas a borrar
CIELO_HASTA = float(sys.argv[4]) if len(sys.argv) > 4 else 0.45
RAYA = int(sys.argv[5]) if len(sys.argv) > 5 else 4
# CAMINO_DESDE = desde qué fracción de la altura hay camino café (subirlo si se come las manos)
CAMINO_DESDE = float(sys.argv[6]) if len(sys.argv) > 6 else 0.45
im = Image.open(entrada).convert('RGBA')
w, h = im.size
px = im.load()


def es_fondo(x, y):
    r, g, b, _ = px[x, y]
    lum = 0.3 * r + 0.59 * g + 0.11 * b
    if b > r + 40 and b > g + 10 and y < h * CIELO_HASTA:   # rayos azules del fondo, aunque sean oscuros
        return True
    if lum < 105:                     # contorno / ropa oscura / cabello
        return False
    cielo = lum > 140 and b >= g - 25 and b >= r - 6 and y < h * CIELO_HASTA   # azules y blancos, solo en la parte de arriba
    pasto = g > r + 12 and g > b + 12                          # verdes
    camino = r > g > b and r - b > 45 and lum > 120 and g > r * 0.66 and y > h * CAMINO_DESDE
    return cielo or pasto or camino


visto = [[False] * h for _ in range(w)]
cola = deque()
for x in range(w):
    for y in (0, h - 1):
        cola.append((x, y))
for y in range(h):
    for x in (0, w - 1):
        cola.append((x, y))
while cola:
    x, y = cola.popleft()
    if x < 0 or y < 0 or x >= w or y >= h or visto[x][y] or not es_fondo(x, y):
        continue
    visto[x][y] = True
    cola.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))

# Huecos encerrados por el personaje (entre las piernas, bajo el brazo): pasto o camino aunque no toquen el borde
for x in range(w):
    for y in range(h):
        if not visto[x][y]:
            r, g, b, _ = px[x, y]
            pasto = g > r + 12 and g > b + 12
            camino = (r > g > b and r - b > 45 and g > r * 0.66 and 0.3 * r + 0.59 * g + 0.11 * b > 120
                      and y > h * max(0.62, CAMINO_DESDE))
            lum = 0.3 * r + 0.59 * g + 0.11 * b
            cielo = b > r + 80 and b >= g and lum > 165 and y < h * CIELO_HASTA   # azul claro encerrado (bajo un brazo)
            if pasto or camino or cielo:
                visto[x][y] = True

mascara = Image.new('L', (w, h), 255)
mp = mascara.load()
for x in range(w):
    for y in range(h):
        if visto[x][y]:
            mp[x, y] = 0
# Quita el halo claro de 1 px que queda pegado al contorno
mascara = mascara.filter(ImageFilter.MinFilter(3))
# Borra rayas delgadas (4 px o menos de alto) que salen del personaje, como la línea del horizonte
mp = mascara.load()
for x in range(w):
    y = 0
    while y < h:
        if mp[x, y]:
            fin = y
            while fin < h and mp[x, fin]:
                fin += 1
            if fin - y <= RAYA:
                for k in range(y, fin):
                    mp[x, k] = 0
            y = fin
        else:
            y += 1
# Se queda solo con la pieza más grande (borra rayitas sueltas del horizonte)
mp = mascara.load()
etiqueta = {}
mejor, mejor_n = None, 0
for x0 in range(w):
    for y0 in range(h):
        if mp[x0, y0] and (x0, y0) not in etiqueta:
            pila, n = [(x0, y0)], 0
            etiqueta[(x0, y0)] = (x0, y0)
            while pila:
                x, y = pila.pop()
                n += 1
                for q in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= q[0] < w and 0 <= q[1] < h and mp[q] and q not in etiqueta:
                        etiqueta[q] = (x0, y0)
                        pila.append(q)
            if n > mejor_n:
                mejor, mejor_n = (x0, y0), n
for q, raiz in etiqueta.items():
    if raiz != mejor:
        mp[q] = 0
im.putalpha(mascara)
caja = im.getbbox()
im = im.crop(caja)
im.save(salida)
print('guardado', salida, im.size)

if len(sys.argv) > 3:   # vista sobre fondo magenta para revisar el recorte
    vista = Image.new('RGBA', im.size, (255, 0, 255, 255))
    vista.alpha_composite(im)
    vista.save(sys.argv[3])
