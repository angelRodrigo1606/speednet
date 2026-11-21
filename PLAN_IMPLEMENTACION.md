# Plan de Implementación — Speednet

> Aplicación Android (Kotlin + Jetpack Compose) para medir la calidad de la conexión de red:
> latencia, jitter, pérdida de paquetes, velocidad de bajada/subida y señal.

---

## 0. Decisiones cerradas (base del plan)

| Decisión | Valor | Motivo / Nota |
|---|---|---|
| `minSdk` | **31** (Android 12) | Decisión del proyecto (no bajar a 26). |
| `targetSdk` / `compileSdk` | 37 | Ya configurado. |
| Persistencia del historial | **Room** | Decisión del proyecto. |
| Método de ping | **TCP connect** a `1.1.1.1:443` y `8.8.8.8:443` | ICMP puro no es accesible sin root. |
| Fuente de velocidad | **Cloudflare** (`speed.cloudflare.com`) | HTTP plano, sin WebSockets ni dependencias extra. |
| Calificación | Pesos 30/25/15/15/15 + umbrales (ver §5) | Decisión de este plan. |
| Veredictos por uso | 3 niveles por uso (ver §5) | Decisión de este plan. |
| Inyección de dependencias | **Manual** (contenedor en `Application`) | Evita Hilt; el proyecto es chico. |

---

## 1. Stack tecnológico y dependencias

### 1.1 Dependencias a añadir en `gradle/libs.versions.toml`

> ⚠️ Las versiones exactas deben verificarse contra **Kotlin 2.2.10** y **AGP 9.5.2** ya configurados. Se indican versiones de referencia; ajustar a la última estable compatible al momento de implementar.

| Librería | Grupo/Artefacto | Versión ref. | Uso |
|---|---|---|---|
| Lifecycle ViewModel Compose | `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.9.x | `viewModel()` en Compose |
| Lifecycle ViewModel KTX | `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.9.x | `viewModelScope` |
| Lifecycle Runtime Compose | `androidx.lifecycle:lifecycle-runtime-compose` | 2.9.x | `collectAsStateWithLifecycle` |
| Navigation Compose | `androidx.navigation:navigation-compose` | 2.9.x | Navegación entre pantallas |
| Room Runtime | `androidx.room:room-runtime` | 2.7.x | Persistencia |
| Room KTX | `androidx.room:room-ktx` | 2.7.x | Coroutines / Flow en DAO |
| Room Compiler | `androidx.room:room-compiler` | 2.7.x | Procesador de anotaciones |
| Kotlinx Coroutines Android | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.x | Concurrencia |
| OkHttp | `com.squareup.okhttp3:okhttp` | 4.12.x | Cliente HTTP de velocidad |
| Play Services Location | `com.google.android.gms:play-services-location` | 21.3.x | Geolocalización ("lugar") |
| KSP plugin | `com.google.devtools.ksp` | `2.2.10-2.0.x` | Compilador de Room (reemplaza kapt) |

### 1.2 Plugins a añadir

- `ksp` → aplicado en `app/build.gradle.kts` (`alias(libs.plugins.ksp)`).
- Añadir `room` como dependencia `ksp(...)` (no `implementation`).

### 1.3 Cambios en `app/build.gradle.kts`

- `plugins { ... alias(libs.plugins.ksp) }`.
- Añadir las dependencias anteriores.
- (Opcional) subir `sourceCompatibility/targetCompatibility` a Java 17 si el toolchain lo permite; no es bloqueante.

---

## 2. Arquitectura general

**MVVM + Clean-ish en 3 capas** (suficiente para el tamaño del proyecto):

```
UI (Compose + ViewModel + StateFlow)
        │  (estado inmutable, eventos)
Domain (modelos, motores de medición, cálculo de puntaje)
        │  (interfaces de repositorio)
Data (Room, OkHttp, lectores de señal/ubicación)
```

- Flujo unidireccional: UI → eventos → `ViewModel` → repositorios/motores → nuevo `UiState` → UI.
- `StateFlow` para el estado; `SharedFlow` (o eventos) para efectos one-shot (navegación, diálogos).
- Los motores de medición corren en coroutines **cancelables** (`viewModelScope`).

---

## 3. Estructura de paquetes

```
com.angel.speednet
├── SpeednetApplication.kt        # contenedor de dependencias (DI manual)
├── MainActivity.kt               # setContent + NavHost
│
├── di/
│   └── AppContainer.kt           # construye DB, repos, motores; lazy singletons
│
├── domain/
│   ├── model/
│   │   ├── Measurement.kt        # resultado completo de una medición
│   │   ├── NetworkSignal.kt      # tipo de red, RSSI/RSRP, SSID
│   │   ├── TestStage.kt          # enum de etapas
│   │   ├── ScoreLevel.kt         # enum Excelente/Buena/Regular/Mala
│   │   └── UseVerdict.kt         # veredicto por uso
│   ├── repository/
│   │   ├── SpeedTestRepository.kt      # interfaz: correr medición
│   │   └── MeasurementRepository.kt    # interfaz: CRUD historial
│   ├── engine/
│   │   ├── SpeedTestEngine.kt    # orquesta las etapas (StateFlow de progreso)
│   │   ├── LatencyMeasurer.kt    # ping TCP + jitter + pérdida
│   │   ├── ThroughputMeasurer.kt # download/upload (OkHttp)
│   │   ├── SignalReader.kt       # ConnectivityManager/WifiManager/TelephonyManager
│   │   └── LocationProvider.kt   # FusedLocationProvider
│   └── scoring/
│       ├── ScoreCalculator.kt    # puntaje 0-100 ponderado
│       └── VerdictCalculator.kt  # veredictos por uso
│
├── data/
│   ├── local/
│   │   ├── SpeednetDatabase.kt
│   │   ├── entity/MeasurementEntity.kt
│   │   └── dao/MeasurementDao.kt
│   ├── network/
│   │   ├── CloudflareClient.kt   # download/upload HTTP
│   │   └── TcpPinger.kt          # connect() TCP y RTT
│   └── repository/
│       ├── SpeedTestRepositoryImpl.kt
│       └── MeasurementRepositoryImpl.kt
│
└── ui/
    ├── navigation/
    │   ├── Routes.kt             # rutas selladas + argumentos
    │   └── SpeednetNavHost.kt
    ├── home/
    │   ├── HomeScreen.kt
    │   └── HomeViewModel.kt
    ├── results/
    │   ├── ResultsScreen.kt
    │   └── ResultsViewModel.kt
    ├── history/
    │   ├── HistoryScreen.kt
    │   └── HistoryViewModel.kt
    ├── components/               # ScoreRing, MetricCard, VerdictCard, ...
    └── theme/                    # (existente) Color.kt, Theme.kt, Type.kt
```

---

## 4. Capa de dominio — modelos

### 4.1 `TestStage` (etapas de la medición)

```text
Idle → CheckingNetwork → MeasuringLatency → Downloading
     → Uploading → ComputingScore → Done | Failed
```

- Cada etapa emite **progreso** (0..1 o mensaje) para la UI.

### 4.2 `NetworkSignal`

- `networkType`: `WIFI`, `CELLULAR_4G`, `CELLULAR_5G`, `UNKNOWN`.
- `signalDbm`: `Int?` (RSSI en Wi-Fi, RSRP en celular).
- `ssid`: `String?` (solo Wi-Fi, requiere permiso de ubicación).
- `carrierName`: `String?` (celular, opcional).

### 4.3 `Measurement`

```text
id: Long?
timestamp: Long
downloadMbps: Double
uploadMbps: Double
latencyMs: Double        # promedio
jitterMs: Double
packetLossPct: Double
score: Int               # 0..100
level: ScoreLevel
network: NetworkSignal
location: LatLng?        # lat/lng (null si no hay permiso)
verdicts: Map<Use, UseVerdict>
```

### 4.4 `ScoreLevel` y `UseVerdict`

- `ScoreLevel`: `EXCELLENT`, `GOOD`, `FAIR`, `POOR`.
- `Use`: `VIDEO_CALL`, `STREAMING`, `GAMING`, `BROWSING`.
- `UseVerdict`: `OPTIMAL`, `ACCEPTABLE`, `NOT_RECOMMENDED`.

---

## 5. Sistema de calificación (detallado)

### 5.1 Pesos

| Métrica | Peso |
|---|---|
| Descarga | 30% |
| Latencia | 25% |
| Subida | 15% |
| Jitter | 15% |
| Pérdida | 15% |

`puntaje = 0.30·S_descarga + 0.25·S_latencia + 0.15·S_subida + 0.15·S_jitter + 0.15·S_pérdida` (redondeado a entero, 0–100).

### 5.2 Puntaje por métrica (0–100)

| Descarga (Mbps) | Subida (Mbps) | Latencia (ms) | Jitter (ms) | Pérdida (%) | Puntos |
|---|---|---|---|---|---|
| ≥ 100 | ≥ 50 | < 20 | < 5 | 0 | 100 |
| 50–100 | 20–50 | 20–40 | 5–10 | 0–0.5 | 90 |
| 25–50 | 10–20 | 40–60 | 10–20 | 0.5–1 | 75 |
| 10–25 | 5–10 | 60–100 | 20–30 | 1–2 | 55 |
| 5–10 | 2–5 | 100–150 | 30–50 | 2–3 | 35 |
| 2–5 | 1–2 | 150–200 | 50–75 | 3–5 | 15 |
| < 2 | < 1 | > 200 | > 75 | > 5 | 0 |

> Implementación: interpolación lineal entre bandas o búsqueda de banda. Mantener la lógica **pura** (sin Android) para unit-testing.

### 5.3 Niveles globales

| Puntaje | Nivel |
|---|---|
| 80–100 | Excelente |
| 60–79 | Buena |
| 40–59 | Regular |
| 0–39 | Mala |

### 5.4 Veredictos por uso

| Uso | Óptima | Aceptable | No recomendada |
|---|---|---|---|
| **Videollamadas** | subida ≥ 5 ∧ bajada ≥ 5 ∧ latencia < 50 ∧ jitter < 20 ∧ pérdida < 1% | subida ≥ 1.5 ∧ bajada ≥ 1.5 ∧ latencia < 100 ∧ pérdida < 3% | resto |
| **Streaming** | bajada ≥ 25 (4K) | bajada ≥ 5 (HD) | bajada < 5 |
| **Juegos** | latencia < 30 ∧ jitter < 10 ∧ pérdida < 0.5% | latencia < 60 ∧ jitter < 20 ∧ pérdida < 1% | resto |
| **Navegación** | bajada ≥ 5 ∧ latencia < 100 | bajada ≥ 1 ∧ latencia < 200 | resto |

> El veredicto se evalúa de arriba hacia abajo: si no cumple "Óptima" se evalúa "Aceptable"; si tampoco, "No recomendada".

---

## 6. Motor de medición (detallado por etapa)

### 6.1 Flujo general (`SpeedTestEngine`)

1. **CheckingNetwork**: validar conectividad con `ConnectivityManager`/`NetworkCapabilities`. Si no hay red → `Failed` con motivo "sin conexión".
2. **Signal** (en paralelo/previo): leer `NetworkSignal` (§6.4) y ubicación (§6.5).
3. **MeasuringLatency**: ping TCP (§6.2).
4. **Downloading**: descarga (§6.3).
5. **Uploading**: subida (§6.3).
6. **ComputingScore**: calcular puntaje, nivel y veredictos.
7. **Done**: emitir `Measurement`; el repositorio lo persiste en Room.
8. Emitir progreso por etapa a un `StateFlow<TestProgress>`.

- **Cancelación**: cada etapa chequea `coroutineContext.isActive` (o `ensureActive()`). El `ViewModel` expone `cancel()` que cancela el `Job`.
- **Timeouts**: cada etapa con su propio timeout para no colgar la prueba.

### 6.2 Latencia / jitter / pérdida (`LatencyMeasurer` + `TcpPinger`)

- Servidores: `1.1.1.1:443` y `8.8.8.8:443`.
- **Ping TCP**: abrir `Socket`, medir el tiempo del `connect()` (RTT), cerrar. Sin datos de aplicación.
- Parámetros:
  - `N = 12` intentos por servidor (24 total).
  - Timeout por intento: `2 s`.
  - Intervalo entre intentos: `~250 ms`.
- Resultados:
  - **Latencia** = promedio de los RTT exitosos.
  - **Jitter** = media de `|RTT_i − RTT_{i−1}|`.
  - **Pérdida (%)** = `timeouts / intentos × 100`.
- Ejecutar los dos servidores en paralelo y combinar muestras (una única métrica de latencia/jitter/pérdida agregada).

### 6.3 Velocidad (`ThroughputMeasurer` + `CloudflareClient`)

- **Descarga**: `GET https://speed.cloudflare.com/__down?bytes=25000000` (25 MB) en **6 conexiones paralelas**, midiendo bytes recibidos/tiempo durante una ventana fija (`~8 s`). Descartar los bytes (no escribir en disco).
- **Subida**: `POST https://speed.cloudflare.com/__up` con cuerpo aleatorio en **4 conexiones paralelas**, ventana fija (`~6 s`).
- **Cálculo**: `Mbps = bytes × 8 / segundos / 1_000_000`, agregado entre conexiones.
- **Warm-up**: ignorar el primer segundo para evitar el "burst" inicial (o usar ventana móvil y tomar la mejor).
- Usar `OkHttp` con un `Dispatcher` dedicado; desactivar caché y compresión para no falsear la medida.
- Los tamaños/duración se ajustan dinámicamente si la conexión es muy rápida (p. ej. pedir `bytes` mayores) para saturar el enlace.

### 6.4 Señal (`SignalReader`)

- **Tipo de red**:
  - `NetworkCapabilities` → transporte `WIFI` / `CELLULAR`.
  - `TelephonyManager.dataNetworkType` → refinar a 4G (`LTE`) / 5G (`NR`).
- **Wi-Fi**: `WifiManager.connectionInfo` → RSSI (dBm) y SSID (requiere `ACCESS_FINE_LOCATION`/`NEARBY_WIFI_DEVICES` y ubicación activada).
- **Celular**: `TelephonyManager.allCellInfo` → `CellSignalStrengthLte.rsrp` / `CellSignalStrengthNr.ssRsrp`.
- Manejar `SecurityException` y valores `UNKNOWN`/`MAX_VALUE` (señal inválida).

### 6.5 Ubicación ("lugar")

- `FusedLocationProviderClient.getCurrentLocation(...)` con `Priority.PRIORITY_BALANCED_POWER_ACCURACY`.
- Solo si se concedió `ACCESS_FINE_LOCATION`.
- Guardar `lat`/`lng`. (Opcional/extra: reverse geocoding con `Geocoder` para mostrar un nombre de lugar; no bloqueante.)
- Si no hay permiso o falla → `location = null`, la medición continúa igual.

---

## 7. Capa de datos

### 7.1 Room — `MeasurementEntity`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `Long` | PK, autoGenerate |
| `timestamp` | `Long` | índice (para ordenar) |
| `downloadMbps` | `Double` | |
| `uploadMbps` | `Double` | |
| `latencyMs` | `Double` | |
| `jitterMs` | `Double` | |
| `packetLossPct` | `Double` | |
| `score` | `Int` | 0–100 |
| `level` | `String` | nombre del `ScoreLevel` |
| `networkType` | `String` | |
| `signalDbm` | `Int?` | |
| `ssid` | `String?` | |
| `carrierName` | `String?` | |
| `latitude` | `Double?` | |
| `longitude` | `Double?` | |
| `verdictsJson` | `String` | veredictos serializados (o columnas por uso) |

> Alternativa a `verdictsJson`: columnas `vCall`, `vStreaming`, `vGaming`, `vBrowsing` (String). Elegir según preferencia; la versión JSON es más flexible.

### 7.2 `MeasurementDao`

- `insert(MeasurementEntity): Long` (suspend).
- `getAll(): Flow<List<MeasurementEntity>>` ordenado por `timestamp DESC`.
- `getById(id): MeasurementEntity?` (suspend).
- `deleteAll()` (para "limpiar historial", opcional).

### 7.3 `SpeednetDatabase`

- `@Database(entities = [MeasurementEntity::class], version = 1)`.
- Singleton creado en `AppContainer` (Room.databaseBuilder).

### 7.4 Repositorios

- `MeasurementRepositoryImpl` → mapea `MeasurementEntity` ↔ `Measurement` (`domain.model`).
- `SpeedTestRepositoryImpl` → usa `SpeedTestEngine` y delega la persistencia en `MeasurementRepository`.

---

## 8. Capa de UI

### 8.1 Navegación (`navigation-compose`)

Rutas selladas:

```text
"home"  →  HomeScreen
"results?measurementId={id}"  →  ResultsScreen  (o pasar el id recién guardado)
"history"  →  HistoryScreen
```

- Flujo: Inicio → (medir) → Resultados; desde Resultados/Inicio → Historial; al tocar un ítem del historial → Resultados.

### 8.2 Estados de UI (sealed class en cada `ViewModel`)

```text
sealed interface UiState {
    object Idle
    data class Measuring(stage: TestStage, progress: Float)
    data class Result(measurement: Measurement)
    data class Error(message: String, recoverable: Boolean)
}
```

- `HomeViewModel`: gestiona `Idle → Measuring → Result`/`Error`, el aviso de consumo, la cancelación y los permisos.
- `ResultsViewModel`: carga la medición (por id) desde Room.
- `HistoryViewModel`: expone `StateFlow<List<Measurement>>` desde el DAO (`Flow` → `stateIn`).

### 8.3 Pantallas

**HomeScreen**
- Indicador circular animado del puntaje (`ScoreRing` con animación de progreso 0→puntaje).
- Botón **"Medir ahora"**.
- Estado de etapa actual + progreso durante la medición.
- Botón **Cancelar** mientras mide.
- Diálogo de aviso de consumo de datos antes de iniciar (ver §9.1).
- Mensajes de error / sin conexión / permisos denegados (con acción para reintentar o abrir ajustes).

**ResultsScreen**
- `ScoreRing` grande con nivel (color según Excelente/Buena/Regular/Mala).
- Tarjetas por métrica (`MetricCard`): Descarga, Subida, Latencia, Jitter, Pérdida — con valor y calificación.
- Tarjeta de señal: tipo de red, RSSI/RSRP, SSID.
- Sección **"Veredictos por uso"**: videollamadas, streaming, juegos, navegación.
- Fecha y lugar.

**HistoryScreen**
- `LazyColumn` de mediciones (fecha, puntaje, descarga/bajada, nivel con color).
- Tap → navegar a Resultados.
- (Opcional) botón "limpiar historial".

### 8.4 Componentes compartidos

- `ScoreRing`: arco circular animado (`Canvas` + `animateFloatAsState`) con puntaje centrado.
- `MetricCard`: título, valor, unidad, etiqueta de calidad.
- `VerdictCard`: icono + texto del veredicto por uso.
- `LevelBadge`: chip con color según nivel.

---

## 9. Permisos

### 9.1 Declaración en `AndroidManifest.xml`

| Permiso | Tipo | Notas |
|---|---|---|
| `INTERNET` | normal | imprescindible |
| `ACCESS_NETWORK_STATE` | normal | conectividad |
| `ACCESS_WIFI_STATE` | normal | RSSI |
| `ACCESS_FINE_LOCATION` | runtime | SSID, señal y "lugar" |
| `READ_PHONE_STATE` | runtime | detalle celular (opcional) |

### 9.2 Flujo runtime

- Usar `ActivityResultContracts.RequestMultiplePermissions` (sin dependencias extra).
- Antes de medir, pedir `ACCESS_FINE_LOCATION` (+ `READ_PHONE_STATE` si se quiere detalle celular).
- **Permiso denegado**: la prueba **continúa** sin SSID/señal/ubicación y se muestra una nota en Resultados; no bloquear la medición.
- **Denegado permanentemente**: ofrecer enlace a ajustes de la app.

---

## 10. Requisitos no funcionales

### 10.1 Aviso de consumo de datos

- `AlertDialog` previo a iniciar: "La prueba puede consumir ~50 MB. ¿Continuar?".
- Configurable: mostrar siempre o recordar la elección (persistir en `SharedPreferences`/DataStore si se desea).

### 10.2 Cancelación

- `ViewModel.cancel()` cancela el `Job` de medición.
- La UI vuelve a `Idle` (o `Error` con "cancelado por el usuario").
- Cerrar/rotar pantalla: el `ViewModel` sobrevive a la rotación; el `Job` sigue o se cancela según política (recomendado: cancelar al salir de la pantalla).

### 10.3 Sin conexión y permisos

- Sin red → `Error("Sin conexión")` con botón "Reintentar".
- Permisos denegados → medir igual (degradado) + nota.

### 10.4 Material 3 y modo oscuro

- `Theme.kt` ya soporta `darkColorScheme`/`dynamicColor`; verificar paleta (reemplazar colores morados por defecto).
- **Cambio importante**: `themes.xml` hoy usa `android:Theme.Material.Light.NoActionBar` (fuerza claro). Cambiar a un tema base `DayNight` (o `Theme.Material3.DayNight.NoActionBar`) para que el splash y la barra de estado sigan el modo oscuro del sistema.

---

## 11. Estrategia de pruebas

| Capa | Tipo | Qué probar |
|---|---|---|
| `scoring` | Unit (JUnit) | Puntajes por banda, pesos, niveles, veredictos. |
| `engine` | Unit (con fakes) | Jitter/pérdida con muestras simuladas; cálculo de Mbps. |
| `data` | Instrumented (Room) | Insertar/consultar/ordenar historial. |
| `repository` | Unit | Mapeo entidad↔modelo. |
| UI | Compose UI test | Estados Idle/Measuring/Result/Error; navegación. |

- Interfaces de repositorio para inyectar fakes en tests de `ViewModel`.

---

## 12. Plan por fases (orden de trabajo)

### Fase 0 — Configuración
- [ ] Añadir dependencias y plugin KSP (`libs.versions.toml`, `build.gradle.kts`).
- [ ] Permisos en `AndroidManifest.xml`.
- [ ] Tema `DayNight` en `themes.xml`.
- [ ] `SpeednetApplication` + `AppContainer` (DI manual).
- [ ] Esqueleto de navegación (`NavHost` + rutas + pantallas vacías).
- **Criterio de salida**: compila y navega entre 3 pantallas vacías.

### Fase 1 — Dominio y scoring
- [ ] Modelos (`Measurement`, `NetworkSignal`, `TestStage`, `ScoreLevel`, `UseVerdict`).
- [ ] `ScoreCalculator` + `VerdictCalculator` + tests unitarios.
- **Criterio**: tests de scoring en verde.

### Fase 2 — Motor de medición
- [ ] `TcpPinger` + `LatencyMeasurer`.
- [ ] `CloudflareClient` + `ThroughputMeasurer`.
- [ ] `SignalReader` + `LocationProvider`.
- [ ] `SpeedTestEngine` (orquestación, progreso, cancelación).
- **Criterio**: medir latencia/bajada/subida real en un dispositivo.

### Fase 3 — Datos (Room)
- [ ] `MeasurementEntity`, `MeasurementDao`, `SpeednetDatabase`.
- [ ] `MeasurementRepositoryImpl`, `SpeedTestRepositoryImpl`.
- **Criterio**: una medición se persiste y se lista.

### Fase 4 — UI (Inicio y Resultados)
- [ ] `HomeViewModel` + `HomeScreen` (ScoreRing animado, "Medir ahora", progreso por etapa, cancelar).
- [ ] `ResultsViewModel` + `ResultsScreen` (tarjetas, veredictos, señal, lugar).
- **Criterio**: flujo completo medir → ver resultados.

### Fase 5 — UI (Historial)
- [ ] `HistoryViewModel` + `HistoryScreen`.
- [ ] Navegación historial → detalle.
- **Criterio**: historial persistente entre reinicios.

### Fase 6 — No funcionales
- [ ] Diálogo de consumo de datos.
- [ ] Manejo de permisos runtime (con degradación).
- [ ] Manejo de sin conexión.
- [ ] Modo oscuro y paleta final.

### Fase 7 — Pruebas y pulido
- [ ] Tests de Room, repositorios y UI.
- [ ] Revisión de estados de borde (cancelar a mitad, timeout, rotación).

---

## 13. Riesgos y consideraciones

- **Compatibilidad de versiones**: AGP 9.5.2 / Kotlin 2.2.10 son muy recientes; verificar que Room, Navigation y KSP tengan versiones compatibles (especialmente KSP `2.2.10-*`).
- **Cloudflare**: los endpoints `__down`/`__up` no son API oficial documentada; si cambian, considerar M-Lab como plan B. Centralizar las URLs en una única clase para facilitar el reemplazo.
- **SSID en Android 12+**: leer SSID/RSSI requiere ubicación activada y permiso; sin ello, degradar la info de señal.
- **Señal celular**: `allCellInfo` puede devolver `UNKNOWN`/valores máximos; ignorarlos para no falsear la métrica.
- **Consumo de datos**: fijar un tope total (p. ej. ~50–100 MB) y respetarlo con la duración/tamaño de las ventanas de medición.
- **Batería/rotación**: decidir política de cancelación al salir de la pantalla para evitar trabajo en segundo plano.
