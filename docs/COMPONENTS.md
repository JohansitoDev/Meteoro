# Componentes y sintaxis `.mtr`

Los comandos usan texto entre comillas JSON. Los identificadores de pantalla
deben ser únicos. Meteoro comprueba los destinos de navegación al compilar.
Cada contenedor abierto con `card`, `row`, `column`, `list` o `scroll` se cierra
con `end`. Las reglas `style` se aplican al componente inmediatamente anterior.

## Pantallas

```text
screen "inicio" "Inicio"
text "Hola"

screen "perfil" "Perfil"
text "Tu perfil"
```

La primera pantalla declarada es la inicial.

## Texto, botones y navegación

```text
text "Bienvenido a Meteoro"
button "Abrir perfil" navigate "perfil"
navigation "inicio" "Inicio"
navigation "perfil" "Perfil"
```

El destino del botón debe coincidir con el ID de una pantalla declarada.
`navigation` (o el alias `tab`) crea un elemento del menú inferior y debe
apuntar a una pantalla existente.

## Tarjetas

Una tarjeta agrupa componentes. Cierra cada tarjeta con `end`:

```text
card "Perfil"
text "Completa tus datos"
input "nombre" "Escribe tu nombre"
end
```

## Campos de entrada

```text
input "correo" "Tu correo electrónico"
```

El primer argumento es el ID del campo y el segundo es el placeholder. Los
valores se conservan durante la sesión de preview, pero todavía no se envían a
un backend.

## Imágenes

```text
image "https://example.com/foto.jpg"
style "size" "180"
style "rounded" "12"
```

La URL debe ser accesible desde el dispositivo. `size` define la altura en dp
en Android y en píxeles CSS en el navegador.

## Filas, columnas, listas y scroll

```text
row
text "Primera columna"
text "Segunda columna"
end

column
text "Elemento superior"
text "Elemento inferior"
end

list horizontal
card "Uno"
text "Desliza a los lados"
end
card "Dos"
text "Otro elemento"
end
end

list vertical
text "Elemento desplazable"
text "Otro elemento"
end
```

`row` y `column` organizan sus hijos. `list horizontal` y `list vertical`
añaden desplazamiento en la dirección indicada. `scroll horizontal` y
`scroll vertical` crean contenedores desplazables sin semántica de lista.

## Estilos

```text
text "Hola, Meteoro"
style "size" "24"
style "color" "#0A1931"
style "align" "center"

card "Perfil"
style "background" "#FFFFFF"
style "padding" "16"
style "rounded" "20"
input "nombre" "Tu nombre"
end
```

Estilos disponibles: `color`, `background` (`backgroundColor`), `size`
(`font-size`), `padding`, `rounded` (`radius`), `spacing` (`gap`), `align`,
`direction`, `fontWeight` y `scroll` (`true` o `false`). Los valores numéricos
aceptan números y el sufijo `px`. Aplica los estilos del título de una tarjeta
inmediatamente después de declararla.

## Menú inferior

Declara un elemento de navegación por pantalla después de declarar sus
pantallas:

```text
navigation "inicio" "Inicio"
navigation "perfil" "Perfil"
```

El menú inferior aparece en las previews web y Android. El destino de cada
pestaña debe corresponder a una pantalla existente; no se permiten dos
pestañas para la misma pantalla.

## Ejemplo completo

```text
screen "inicio" "Inicio"
text "Bienvenido"
card "Tu primera tarjeta"
text "Contenido de ejemplo"
end
button "Ver perfil" navigate "perfil"

screen "perfil" "Perfil"
card "Datos personales"
input "nombre" "Tu nombre"
end
button "Volver al inicio" navigate "inicio"

navigation "inicio" "Inicio"
navigation "perfil" "Perfil"
```

Los componentes son `screen`, `text`, `button`, `input`, `image`, `card`,
`row`, `column`, `list`, `scroll` y `navigation`. `tab` es un alias de
`navigation`; `end` cierra el contenedor abierto.
