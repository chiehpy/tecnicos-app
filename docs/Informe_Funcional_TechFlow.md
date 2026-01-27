# Informe Funcional - TechFlow (Tecnicos App)

## 1. Descripción General

Aplicación Android para técnicos de reparación de terminales POS. Permite gestionar el flujo de trabajo de revisión, reparación, limpieza, control de calidad y recuperación de repuestos de terminales.

---

## 2. Autenticación

### 2.1 Login
- **Usuario**: nombre de usuario (se convierte a minúsculas)
- **PIN**: código de 4 dígitos
- Autenticación contra middleware (MDW) que valida con Salesforce
- Gestión automática de tokens JWT (access + refresh)
- Renovación automática de token cuando expira

### 2.2 Sesión
- Persistencia de sesión (no requiere login cada vez)
- Cierre de sesión manual desde menú
- Cierre automático si falla la renovación de token

---

## 3. Roles de Usuario

La app soporta **5 roles** (asignados por administrador en Salesforce):

| Rol | Nombre en UI | Función |
|-----|--------------|---------|
| Revisión inicial | Revisión inicial | Diagnóstico inicial de fallas |
| Reparación | Reparación | Reparación técnica |
| Limpieza | Limpieza | Limpieza de terminales |
| QA | QA | Control de calidad |
| Recovery | Parts Recovery | Recuperación de repuestos |

---

## 4. Flujo de Trabajo Principal

### 4.1 Pantalla de Trabajo (WorkActivity)
- Muestra rol activo del técnico
- Ingreso de número de serie (manual o escaneo)
- Escaneo de código de barras con cámara
- Orientación de escáner configurable (vertical/horizontal)
- Al procesar: asigna terminal al técnico (ASSIGN)

### 4.2 Pantalla de Detalles (TerminalDetailsActivity)
Muestra información del terminal:
- Número de serie
- IMEI / IMEI2
- Modelo (detecta N910 Plus vs N910 A5 por IMEI2)
- Estado actual
- Fallas observadas
- Observaciones QA (si existen)
- Cantidad de rechazos QA

---

## 5. Funcionalidades por Rol

### 5.1 Revisión Inicial

**Objetivo**: Diagnosticar fallas del terminal

**Acciones disponibles**:
- **Seleccionar observaciones de falla** (picklist múltiple)
- **Enviar a Reparación Técnica** (COMPLETE)
- **Marcar como Irreparable** (requiere fallas que lo justifiquen)

**Catálogo de fallas** (27 opciones):
- Sin falla
- Carcasa posterior/frontal rota
- Sin tapa de batería/impresora
- Display roto, Táctil roto
- Impresora rota
- Tapa de batería/impresora rota
- Cucarachas en placa principal
- Placa dañada (no bootea, no enciende, no anda impresora, sobrecalienta, sulfatada, tamper permanente)
- Cámara frontal/trasera rota
- Entrada USB dañada, Pin de carga dañado
- Botón home
- USB sucio
- Lectora chip IC sucio/dañada
- No comunica, No bootea, Tamper

**Validación Irreparable**: Solo se habilita si las fallas seleccionadas justifican que el terminal es irreparable.

---

### 5.2 Reparación

**Objetivo**: Reparar terminal

**Acciones disponibles**:
- **Finalizar proceso** (COMPLETE)
- **Cambiar estado** con subestados:
  - Reparación
  - Programación (Carga de firmware + Inyección)

---

### 5.3 Limpieza

**Objetivo**: Limpiar terminal

**Acciones disponibles**:
- **Finalizar proceso** (COMPLETE)
- **Cambiar estado** (a cualquier estado válido)

---

### 5.4 QA (Control de Calidad)

**Objetivo**: Verificar calidad del terminal reparado

**Acciones disponibles**:
- **Aprobar terminal** (COMPLETE) - Terminal pasa QA
- **Rechazar terminal** (MODIFY + REJECT) - Requiere seleccionar observaciones QA

**Catálogo de observaciones QA** (18 opciones):
- Falta de limpieza: Carcasa posterior/frontal, Tapa batería/impresora
- Daño estético: Carcasa posterior/frontal, Dientes impresora, Tapa batería/impresora
- Daño estético (amarilla): Carcasa frontal/posterior, Tapa batería/impresora
- Faltan tornillos
- Tamper
- Cámara trasera/frontal
- Sin audio

**Información adicional mostrada**:
- Observaciones QA previas
- Cantidad de rechazos QA acumulados

---

### 5.5 Parts Recovery

**Objetivo**: Recuperar repuestos de terminales irreparables

**Acciones disponibles**:
- **Cargar repuestos** (picklist múltiple + guardar)
- **Procesar terminal** - Cambia estado a "Pendiente de facturación"
- **Revertir estado**

**Catálogo de repuestos recuperables** (14 opciones):
- Carcasa frontal
- Carcasa posterior
- Batería
- Tapa batería
- Tapa impresora
- Rodillo
- Display
- Impresora
- Pila
- Pila IO
- Placa IO
- Cámara delantera
- Cámara trasera
- Lectora magnética

---

## 6. Historial

- Registro local de terminales procesados en el día
- Muestra: Hora, Serial, Rol
- Solo cuenta terminales con COMPLETE exitoso
- Se reinicia cada día

---

## 7. Configuración (Settings)

- **Selección de rol activo**: Dropdown con roles permitidos para el usuario
- **Orientación del escáner**: Vertical (landscape) / Horizontal (portrait)

---

## 8. Integración Backend

### Endpoints utilizados (MDW -> Salesforce):

| Endpoint | Método | Función |
|----------|--------|---------|
| /auth/login | POST | Autenticación |
| /auth/refresh | POST | Renovar token |
| /terminal/lookup | GET | Consultar info de terminal |
| /terminal/event | POST | ASSIGN, COMPLETE, MODIFY, REJECT |
| /recovery/{id} | PATCH | Guardar repuestos recuperados |

---

## 9. Características Técnicas

- **Plataforma**: Android (Kotlin)
- **Requisito mínimo**: Android 5.1.1 o superior
- **Arquitecturas soportadas**: armeabi-v7a, arm64-v8a
- **Arquitectura de código**: Activities + Retrofit + OkHttp
- **Autenticación**: JWT con refresh automático
- **Persistencia local**: SharedPreferences (sesión + historial)
- **Escaneo de códigos**: ZXing (cámara) + Newland NSDK (escáner físico)
