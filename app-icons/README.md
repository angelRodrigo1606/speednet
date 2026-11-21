# Iconos de Speednet (SVG)

Cuatro propuestas de icono dibujadas a mano en SVG. Todas usan `viewBox` de
512×512 y esquinas redondeadas (estilo icono de app).

| Archivo | Concepto | Paleta |
|---|---|---|
| `01-gauge.svg` | Velocímetro con aguja y arco de progreso | Azul → azul oscuro, acento amarillo |
| `02-signal-bolt.svg` | Ondas de señal + rayo de velocidad | Cian → teal, acento amarillo |
| `03-ring-bars.svg` | Anillo de progreso + barras ascendentes | Violeta → índigo, barras amarillas |
| `04-globe-bolt.svg` | Globo de red + rayo central | Esmeralda → verde oscuro, rayo amarillo |

> **Icono de la app:** `04-globe-bolt.svg` es el icono elegido. Está convertido
> al icono adaptativo de Android en
> `app/src/main/res/drawable/ic_launcher_{background,foreground}.xml`
> (referenciados desde `mipmap-anydpi/ic_launcher.xml` y `ic_launcher_round.xml`).

## Previsualizar

Abre cualquier `.svg` en el navegador, Inkscape o Figma.

## Exportar a PNG

Con Inkscape:

```bash
inkscape 01-gauge.svg --export-type=png --export-width=512 --export-filename=01-gauge.png
```

O con `rsvg-convert`:

```bash
rsvg-convert -w 512 -h 512 01-gauge.svg -o 01-gauge.png
```

## Usar como icono adaptativo de Android

Estos SVG incluyen fondo; para un `AdaptiveIcon` conviene:

1. Usar solo el glifo (arco, ondas, barras, globo) como *foreground*.
2. Rellenar el *background* con el color/gradiente.
3. Dejar un margen de seguridad del ~25 % alrededor del glifo.

También se pueden convertir a `VectorDrawable` con herramientas como
[svg2android](https://github.com/avianey/svg2android) o el importador de
Android Studio.
