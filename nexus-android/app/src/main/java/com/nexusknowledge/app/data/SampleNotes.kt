package com.nexusknowledge.app.data

/**
 * Conceptos iniciales de logística y transporte (España / UE), enlazados entre sí con [[...]].
 * Son editables. Las cifras normativas deben contrastarse en BOE y EUR-Lex antes de citarlas.
 */
object SampleNotes {
    private fun n(title: String, category: String, tags: String, content: String) =
        Note(id = 0, title = title, content = content.trimIndent(), category = category, tags = Links.parseTags(tags))

    fun all(): List<Note> = listOf(
        n("Guía de Nexus", "Guía", "ayuda", """
            Nexus combina dos espacios:

            • Comunidad: preguntas y respuestas con votos, reputación, historia y proyectos (los datos se guardan en el móvil).
            • Mis notas: tu cuaderno de conceptos. Escribe el título de otra nota entre dobles corchetes para enlazarlas, por ejemplo [[Picking]]. Si la nota no existe, al pulsar el enlace se abre el editor para crearla.
            • Repaso: fichas con el título como pregunta y el contenido como respuesta. Sale primero lo que menos dominas.

            Empieza por [[SGA / WMS]], [[Tiempos de conducción y descanso]] o [[Incoterms 2020]].
        """),

        // ---- Almacén
        n("SGA / WMS", "Almacén", "software, ubicaciones, trazabilidad", """
            Sistema de Gestión de Almacenes (Warehouse Management System). Controla ubicaciones, recepción, ubicación, [[Picking]], expedición e inventario en tiempo real.

            Funciones clave: lectura de códigos ([[SSCC y GS1-128]]), reglas de rotación ([[FIFO, FEFO y LIFO]]), ubicación por [[Análisis ABC]] y [[Inventario cíclico]].
            Se integra con el ERP (pedidos, compras) y con el TMS (transporte).
        """),
        n("Picking", "Almacén", "preparación de pedidos, productividad", """
            Preparación de pedidos: extraer de su ubicación las unidades que pide el cliente. Suele ser la operación más cara del almacén (gran parte del coste es el desplazamiento).

            Tipos: por pedido, por lotes (varios pedidos a la vez), por olas y por zonas. Tecnologías: radiofrecuencia, pick-to-light, voice picking.
            Lo gestiona el [[SGA / WMS]]; colocar las referencias A del [[Análisis ABC]] cerca de expedición reduce recorridos. Errores de picking empeoran el [[OTIF]].
        """),
        n("Cross-docking", "Almacén", "flujo, sin stock", """
            La mercancía recibida se clasifica y se expide sin almacenarse (idealmente en menos de 24 h). Reduce stock y manipulaciones, pero exige sincronizar llegadas y salidas.

            Necesita etiquetado fiable ([[SSCC y GS1-128]]) y un [[SGA / WMS]] que asigne muelles.
        """),
        n("FIFO, FEFO y LIFO", "Almacén", "rotación, caducidad", """
            • FIFO (First In, First Out): sale primero lo que entró primero.
            • FEFO (First Expired, First Out): sale primero lo que caduca antes. Habitual en alimentación y farmacia.
            • LIFO (Last In, First Out): sale primero lo último en entrar; físicamente se da en estanterías drive-in con un solo acceso.

            Ojo: como método de valoración contable, LIFO no está admitido en el PGC español ni en NIC 2. El [[SGA / WMS]] aplica la regla al asignar ubicaciones en el [[Picking]].
        """),
        n("Palet europeo", "Almacén", "EPAL, unidad de carga", """
            Palet de 1200 × 800 mm (EUR/EPAL), estándar en Europa. El palet universal o americano mide 1200 × 1000 mm.

            En un semirremolque estándar caben 33 europalets en una sola altura. Cada palet expedido se identifica con un [[SSCC y GS1-128]].
        """),
        n("SSCC y GS1-128", "Almacén", "etiquetas, códigos de barras, trazabilidad", """
            SSCC (Serial Shipping Container Code): código de 18 dígitos que identifica de forma única una unidad logística (por ejemplo un [[Palet europeo]]).
            Se representa con la simbología GS1-128 en la etiqueta logística, junto a otros datos con identificadores de aplicación (lote, caducidad, cantidad).

            Permite recepción por escaneo en el [[SGA / WMS]] y [[Cross-docking]] ágil.
        """),
        n("Inventario cíclico", "Almacén", "recuento, exactitud de stock", """
            Recuento de una parte del almacén cada día o semana en lugar de un inventario anual completo con el almacén parado.

            Se cuentan más a menudo las referencias A del [[Análisis ABC]]. Mejora la exactitud del stock del [[SGA / WMS]].
        """),

        // ---- Gestión de stock
        n("Análisis ABC", "Gestión de stock", "Pareto, clasificación", """
            Clasifica las referencias según su peso (en valor o en movimientos), siguiendo el principio de Pareto:
            • A: pocas referencias (aprox. 20 %) que suponen la mayor parte (aprox. 80 %).
            • B: intermedias.
            • C: muchas referencias con poco peso.

            Se usa para ubicar (A cerca de expedición, ver [[Picking]]), fijar la frecuencia del [[Inventario cíclico]] y el nivel de [[Stock de seguridad]].
        """),
        n("Stock de seguridad", "Gestión de stock", "fórmula, nivel de servicio", """
            Stock extra para cubrir variaciones de la demanda o del [[Lead time]].

            Fórmula habitual (demanda variable): SS = z × σd × √LT
            • z: factor del nivel de servicio (95 % → 1,65).
            • σd: desviación típica de la demanda diaria.
            • LT: plazo de reposición en días.

            Forma parte del [[Punto de pedido]].
        """),
        n("Punto de pedido", "Gestión de stock", "fórmula, reposición", """
            Nivel de stock en el que hay que lanzar un pedido de reposición.

            Punto de pedido = demanda media diaria × [[Lead time]] + [[Stock de seguridad]]

            Ejemplo: 40 uds/día, plazo 5 días, SS 60 uds → 40 × 5 + 60 = 260 uds. La cantidad a pedir puede ser el [[Lote económico (EOQ)]].
        """),
        n("Lote económico (EOQ)", "Gestión de stock", "fórmula, Wilson", """
            Cantidad de pedido que minimiza la suma de costes de emisión de pedidos y de almacenamiento (modelo de Wilson).

            EOQ = √(2 × D × S / H)
            • D: demanda anual (uds).
            • S: coste de emitir un pedido (€).
            • H: coste anual de mantener una unidad en stock (€).

            Ejemplo: D = 12.000, S = 50 €, H = 3 € → √(400.000) ≈ 632 uds. Ver [[Punto de pedido]].
        """),
        n("Lead time", "Gestión de stock", "plazo, reposición", """
            Plazo desde que se lanza un pedido hasta que la mercancía está disponible para la venta (incluye preparación del proveedor, transporte y recepción).

            Cuanto más largo y variable, mayor [[Stock de seguridad]] y más alto el [[Punto de pedido]].
        """),

        // ---- KPIs
        n("OTIF", "KPIs", "nivel de servicio, entregas", """
            On Time In Full: % de pedidos entregados a tiempo y completos.

            OTIF = pedidos a tiempo y completos / pedidos totales × 100

            Un pedido que llega a tiempo pero incompleto no cuenta. Le afectan los errores de [[Picking]] y la falta de stock ([[Stock de seguridad]]).
        """),
        n("Rotación de stock", "KPIs", "fórmula, eficiencia", """
            Veces que se renueva el stock en un periodo.

            Rotación = coste de las ventas / stock medio (ambos a coste)

            Rotación alta: poco stock inmovilizado. Su inversa, expresada en días, es la [[Cobertura de stock]].
        """),
        n("Cobertura de stock", "KPIs", "días, fórmula", """
            Días que se podría atender la demanda con el stock actual.

            Cobertura (días) = stock actual / consumo medio diario

            Ejemplo: 3.000 uds / 150 uds al día = 20 días. Relacionada con la [[Rotación de stock]].
        """),

        // ---- Normativa
        n("Tiempos de conducción y descanso", "Normativa", "Reglamento 561/2006, conductor", """
            Reglamento (CE) 561/2006 (modificado por el Reglamento (UE) 2020/1054):
            • Conducción diaria: 9 h, ampliable a 10 h dos veces por semana.
            • Semanal: máximo 56 h; en dos semanas consecutivas, máximo 90 h.
            • Pausa: 45 min tras 4 h 30 min de conducción (se puede dividir en 15 + 30 min, en ese orden).
            • Descanso diario: 11 h (reducible a 9 h un máximo de 3 veces entre descansos semanales; o fraccionado 3 + 9 h).
            • Descanso semanal: normal 45 h; reducido mínimo 24 h, a compensar antes del final de la tercera semana.

            Se controla con el [[Tacógrafo]]. Verificar en EUR-Lex.
        """),
        n("Tacógrafo", "Normativa", "Reglamento 165/2014, control", """
            Aparato que registra conducción, otros trabajos, disponibilidad y descanso. Regulado por el Reglamento (UE) 165/2014; los vehículos nuevos llevan tacógrafo inteligente (versión 2).

            Descarga de datos (Reglamento (UE) 581/2010): tarjeta del conductor como máximo cada 28 días y unidad del vehículo como máximo cada 90 días.
            Sirve para comprobar los [[Tiempos de conducción y descanso]].
        """),
        n("LOTT y ROTT", "Normativa", "autorizaciones, España", """
            • LOTT: Ley 16/1987, de Ordenación de los Transportes Terrestres.
            • ROTT: su Reglamento, Real Decreto 1211/1990.

            Regulan el acceso a la profesión y las autorizaciones de transporte en España (por ejemplo, la autorización MDP para mercancías). En el ámbito UE se complementan con las normas de [[Cabotaje]] y documentos como el [[CMR]].
        """),
        n("CMR", "Transporte", "carta de porte, internacional, responsabilidad", """
            Convenio de Ginebra de 1956 relativo al contrato de transporte internacional de mercancías por carretera, y nombre de su carta de porte.

            • Se aplica cuando origen y destino están en países distintos y al menos uno es parte del Convenio.
            • Ejemplares originales: remitente, porteador y destinatario.
            • Límite de responsabilidad del porteador: 8,33 DEG por kg de peso bruto que falte.
            • Reservas: daños aparentes en la entrega; no aparentes en 7 días (sin domingos ni festivos); retraso en 21 días.

            Ver [[Incoterms 2020]] (reparto de costes y riesgos) y [[ADR]] si la carga es peligrosa.
        """),
        n("ADR", "Transporte", "mercancías peligrosas, seguridad", """
            Acuerdo europeo sobre transporte internacional de mercancías peligrosas por carretera (en España se aplica también al transporte nacional).

            • 9 clases de peligro (1 explosivos … 9 materias diversas).
            • Panel naranja: arriba el número de identificación de peligro; abajo el número ONU.
            • Exención por cantidades limitadas por unidad de transporte (sección 1.1.3.6, «regla de los 1000 puntos»).

            Documentos: carta de porte con datos ADR (puede ir junto al [[CMR]]) e instrucciones escritas.
        """),
        n("Cabotaje", "Transporte", "UE, Reglamento 1072/2009", """
            Transporte nacional realizado por un transportista no residente en ese país.

            Reglamento (CE) 1072/2009, modificado por el Reglamento (UE) 2020/1055: hasta 3 operaciones en 7 días tras una entrega internacional; después, periodo de carencia de 4 días con el mismo vehículo en ese país.
            Ver [[LOTT y ROTT]].
        """),

        // ---- Comercio internacional
        n("Incoterms 2020", "Comercio internacional", "ICC, costes, riesgos", """
            Reglas de la Cámara de Comercio Internacional (ICC) que fijan qué costes, riesgos y trámites asume vendedor y comprador. Son 11:

            • Cualquier modo de transporte: EXW, FCA, CPT, CIP, DAP, DPU, DDP.
            • Solo marítimo / vías navegables: FAS, FOB, CFR, CIF.

            Novedades 2020: DPU sustituye a DAT; CIP exige cobertura de seguro amplia (cláusulas A) y CIF mínima (cláusulas C).
            No regulan el contrato de transporte ([[CMR]]) ni la propiedad. Aduanas: [[DUA]].
        """),
        n("DUA", "Comercio internacional", "aduanas, declaración", """
            Documento Único Administrativo: declaración en aduana para exportar, importar o acoger mercancías a regímenes aduaneros en intercambios con países fuera de la UE.

            En los intercambios dentro de la UE no hay DUA. Quién lo tramita y paga depende del [[Incoterms 2020]] pactado (por ejemplo, en DDP el vendedor asume la importación).
        """),
    )
}
