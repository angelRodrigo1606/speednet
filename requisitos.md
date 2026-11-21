**1. Métricas a medir**

- Latencia (ping/RTT) y jitter
- Pérdida de paquetes
- Velocidad de descarga y subida
- Tipo de red (Wi-Fi, 4G, 5G) y fuerza de señal (RSSI/RSRP)

**2. Cómo medirlas**

- Latencia/jitter/pérdida: varios pings (sockets TCP o ICMP) a servidores como 1.1.1.1 y 8.8.8.8
- Velocidad: descargar y subir un archivo de prueba desde un servidor propio o de terceros (Cloudflare, M-Lab) midiendo bytes/segundo
- Señal: `ConnectivityManager`, `NetworkCapabilities`, `WifiManager` y `TelephonyManager`

**3. Sistema de calificación**
Puntaje de 0 a 100 ponderado, por ejemplo: descarga 30%, latencia 25%, subida 15%, jitter 15%, pérdida 15%. Se traduce a niveles: Excelente, Buena, Regular, Mala. Además, un veredicto por uso: videollamadas, streaming, juegos y navegación.

**4. Pantallas (Compose)**

- **Inicio:** botón "Medir ahora", indicador circular con el puntaje animado
- **Resultados:** tarjetas con cada métrica y su calificación
- **Historial:** lista de mediciones con fecha y lugar

**5. Arquitectura**

- MVVM con `ViewModel` + `StateFlow`
- Navigation Compose
- Estados de UI: Inactivo, Midiendo (con progreso por etapa), Resultado, Error

**6. Permisos**
`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `INTERNET`, `ACCESS_FINE_LOCATION` (también necesaria para leer el SSID y la señal) y `READ_PHONE_STATE` si se quiere detalle celular.

**7. Requisitos no funcionales**

- Aviso del consumo de datos antes de medir
- Cancelar la prueba en cualquier momento
- Manejo de sin conexión y de permisos denegados
- Min SDK 26+ y Material 3 con modo oscuro
