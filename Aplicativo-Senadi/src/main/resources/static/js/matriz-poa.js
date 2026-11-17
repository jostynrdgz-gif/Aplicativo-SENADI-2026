(() => {
    "use strict";

    const barraSuperior = document.getElementById(
        "desplazamiento-superior"
    );

    const contenidoSuperior = document.getElementById(
        "contenido-desplazamiento"
    );

    const contenedorTabla = document.getElementById(
        "tabla-matriz"
    );

    if (!barraSuperior || !contenidoSuperior || !contenedorTabla) {
        return;
    }

    const tabla = contenedorTabla.querySelector("table");

    function actualizarBarra() {
        contenidoSuperior.style.width =
            contenedorTabla.scrollWidth + "px";

        barraSuperior.scrollLeft = contenedorTabla.scrollLeft;
    }

    // Mover la barra superior desplaza la matriz.
    barraSuperior.addEventListener("scroll", () => {
        if (
            Math.abs(
                contenedorTabla.scrollLeft - barraSuperior.scrollLeft
            ) > 1
        ) {
            contenedorTabla.scrollLeft = barraSuperior.scrollLeft;
        }
    });

    // Mantiene ambas barras sincronizadas.
    contenedorTabla.addEventListener("scroll", () => {
        if (
            Math.abs(
                barraSuperior.scrollLeft - contenedorTabla.scrollLeft
            ) > 1
        ) {
            barraSuperior.scrollLeft = contenedorTabla.scrollLeft;
        }
    });

    // Al escribir, limpia la selección de esa misma columna.
    document.querySelectorAll(".filtro-columna").forEach(caja => {
        caja.addEventListener("input", () => {
            const indice = caja.dataset.columna;
            const combo = document.getElementById("o" + indice);

            if (combo) {
                combo.value = "";
            }
        });
    });

    // Al seleccionar, limpia el texto de esa misma columna.
    document.querySelectorAll(".combo-columna").forEach(combo => {
        combo.addEventListener("change", () => {
            const indice = combo.dataset.columna;
            const caja = document.getElementById("f" + indice);

            if (caja) {
                caja.value = "";
            }
        });
    });

    window.addEventListener("resize", actualizarBarra);
    window.addEventListener("load", actualizarBarra);

    if ("ResizeObserver" in window && tabla) {
        const observador = new ResizeObserver(actualizarBarra);

        observador.observe(tabla);
        observador.observe(contenedorTabla);
    }

    requestAnimationFrame(actualizarBarra);
})();