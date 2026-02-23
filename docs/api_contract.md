# Contrato API — Middleware (MDW)

Base URL: `http://167.234.226.219:8000/`

---

## Roles válidos

| Rol                            | Uso                          |
|-------------------------------|------------------------------|
| `Limpieza`                    | Limpieza de terminales       |
| `QA`                          | Control de calidad           |
| `Revisión inicial`            | Revisión inicial             |
| `Programador (carga de firmwares)` | Carga de firmware / inyección |
| `Reparación`                  | Reparación técnica           |
| `Recovery`                    | Recuperación de repuestos    |

> Los valores deben coincidir exactamente (case-sensitive). Si no coinciden, el MDW devuelve error sin llegar a SF.

---

## Endpoints

### POST /terminal/event

#### ASSIGN
```json
{
  "action": "ASSIGN",
  "serial": "SN123456789",
  "role": "Limpieza",
  "technicianName": "Juan García"
}
```

#### COMPLETE
```json
{
  "action": "COMPLETE",
  "serial": "SN123456789",
  "role": "Limpieza",
  "failureObservations": ["Batería defectuosa", "Pantalla agrietada"]
}
```
- `failureObservations`: opcional, puede ser string o array. Solo aplica para `Revisión inicial`.
- `technicianName`: **NO debe enviarse** en COMPLETE (el MDW lo rechaza con 422).

#### MODIFY
```json
{
  "action": "MODIFY",
  "serial": "SN123456789",
  "targetStatus": "Reparación Técnica",
  "targetSubstatus": "Carga de firmware"
}
```
- Campos opcionales según status: `failureObservations`, `recoveredParts`, `technicianName`.
- `technicianName`: solo se permite cuando `targetStatus = "Pendiente de facturación"`.

**targetStatus válidos:**
- `Reparación Técnica`
- `Irreparable`
- `Revisión inicial`
- `Limpieza`
- `Testeo`
- `Pendiente de facturación`

**targetSubstatus válidos** (solo para `Reparación Técnica`):
- `Carga de firmware`
- `Reparación`
- `Carga de firmware + Inyección`

#### REJECT
```json
{
  "action": "REJECT",
  "serial": "SN123456789",
  "role": "QA",
  "qaObservations": "Display descalibrado"
}
```

---

### GET /terminal/lookup?serial={serial}

Devuelve información del terminal: estado actual, subestado, observaciones QA, fallas, técnicos que intervinieron, cuenta, etc.

---

### POST /auth/login
```json
{ "username": "...", "pin": "..." }
```
Devuelve `accessToken` + `refreshToken` (JWT). El JWT incluye `technicianName` y `allowedRoles`.

### POST /auth/refresh
```json
{ "refreshToken": "..." }
```

### POST /print/label
```json
{ "serial": "...", "darkness": 10, "lsMm": 4 }
```
Devuelve ZPL para imprimir etiqueta.

### PATCH /recovery/{id}
```json
{ "recoveredParts": "..." }
```

### GET /app/version
Devuelve la versión más reciente disponible de la app.

---

## Notas

- Todos los valores de rol y estado son **case-sensitive**.
- El MDW valida el schema antes de enviar a Salesforce. Errores de validación devuelven HTTP 422.
- Los campos `null` no se serializan en el payload (Gson por defecto).
