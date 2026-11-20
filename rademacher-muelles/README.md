# Rademacher SL · Gestión de muelles y turnos (Girona)

Aplicación web para asignar los **14 muelles** a los chóferes, gestionar los **turnos por muelle** y permitir que el chófer avise desde su móvil de que ha **terminado la carga/descarga**, para que el siguiente sea llamado automáticamente. Interfaz del chófer en **16 idiomas**.

## Flujo operativo

| Paso | Quién | Acción en la app |
|---|---|---|
| 1 | Chófer | Llega a oficina y entrega documentación (CMR, albarán, e-DA si lleva bebidas alcohólicas) |
| 2 | Oficina | `/oficina` → registra matrícula, transportista, operación y **muelle** (la app sugiere el más libre) |
| 3 | Oficina | Se genera un **ticket con código + QR** (imprimible) |
| 4 | Chófer | Escanea el QR → ve su muelle, su posición en la cola y quién está ahora en el muelle, **en su idioma** |
| 5 | Sistema | Cuando el muelle queda libre, el primero de la cola pasa a **"¡ES SU TURNO!"** (pantalla verde, sonido, vibración) |
| 6 | Chófer | Pulsa **"Estoy en el muelle"** y al terminar **"Carga/descarga finalizada"** |
| 7 | Sistema | Libera el muelle y llama automáticamente al siguiente chófer de la cola |

Estados: `En espera` → `Llamado (su turno)` → `En muelle` → `Finalizado` (o `Cancelado`).

## Pantallas

- **`/oficina`** (PIN): registro de llegadas, vista de los 14 muelles con su cola, mover chófer a otro muelle, adelantar en la cola, cerrar/abrir muelle (avería, limpieza…), finalizar/cancelar, reimprimir ticket y **enviar mensajes predefinidos** que el chófer recibe traducidos (acuda a oficina, documentos listos, retraso, abra puertas, motor/freno/calzos).
  KPIs del día: finalizados, cargas, descargas, **tiempo medio de espera** y **tiempo medio en muelle** (útiles para analizar el *dock-to-stock* y la saturación de muelles).
- **`/c/CÓDIGO`** (móvil del chófer): sin instalación ni registro. Idioma detectado del teléfono y seleccionable.
- **`/pantalla`**: para una TV en la sala de chóferes; muestra los 14 muelles y rota el idioma cada 8 s.

Idiomas: español, catalán, inglés, francés, alemán, italiano, portugués, neerlandés, polaco, rumano, búlgaro, ruso, ucraniano, checo, lituano y turco (`public/i18n.js`). Son traducciones fijas, no traducción automática en línea: funcionan sin internet externo y no envían datos a terceros. **Conviene que un hablante nativo revise cada idioma** antes de usarlo en producción.

## Puesta en marcha

Requisitos: Node.js 18 o superior. Sin dependencias.

```bash
cd rademacher-muelles
OFFICE_PIN=un_pin_seguro npm start      # http://localhost:3000
npm test                                 # pruebas de la API
```

Variables: `PORT` (3000), `OFFICE_PIN` (1234 por defecto: **cámbielo**), `DOCKS` (14), `RETENTION_DAYS` (7), `DATA_FILE` (`data/state.json`).

Para que los chóferes accedan con sus móviles, el servidor debe estar publicado en una URL con **HTTPS** (p. ej. un VPS o servicio cloud con proxy inverso Nginx/Caddy) o en la wifi de invitados del centro. El HTTPS es necesario para notificaciones y bloqueo de pantalla activa en el móvil.

## Protección de datos (RGPD / LOPDGDD)

- Solo la matrícula es obligatoria; nombre y teléfono son opcionales.
- Las visitas cerradas se borran automáticamente a los `RETENTION_DAYS` días.
- La pantalla pública solo muestra matrículas, nunca nombres.
- Recomendado: cartel informativo en oficina sobre el tratamiento de datos.

## Limitaciones y siguientes pasos posibles

- Los avisos sonoros funcionan con la página abierta; para avisos con el móvil bloqueado habría que añadir notificaciones *push* (Web Push) o SMS/WhatsApp.
- Datos en un fichero JSON: suficiente para un centro de 14 muelles. Si se integra con el SGA/WMS, se puede migrar a base de datos.
- Se puede empaquetar como app Android reutilizando el proyecto WebView de `nexus-android.zip` apuntando a la URL del servidor.
