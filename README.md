# 🎮 PS3 Controller Pro (Kotlin)

Aplicación Android moderna escrita en **Kotlin** para el control remoto, monitoreo de telemetría y gestión de archivos en consolas **PlayStation 3** equipadas con **webMAN MOD** y servidor **FTP** activo.

---

## ✨ Características Principales

### 📊 Telemetría y Monitoreo en Tiempo Real
- Monitoreo continuo de la temperatura de la **CPU** y la **GPU (RSX)**.
- Indicador de velocidad del **ventilador** y espacio disponible en el disco interno (**HDD**).
- Opción de refresco automático configurable (cada 5 segundos).

### 📦 Gestión e Instalación de PKG (Foreground Service)
- Subida de juegos y aplicaciones en formato `.pkg` directamente a la ruta `/dev_hdd0/packages/`.
- **Servicio en segundo plano (Foreground Service)** con notificación persistente en la barra de estado: la subida continúa de manera ininterrumpida incluso si la app se minimiza o la pantalla se apaga.

### 🖼️ Subida de Fondos de Pantalla y Temas (Wallpapers)
- Selector de imágenes integradas (formatos **JPG, PNG, BMP**).
- Transferencia por FTP en tiempo real a la carpeta de temas de la PS3 (`/dev_hdd0/theme/`) o rutas personalizadas.
- Barra de progreso interactiva con porcentaje en vivo.

### 📁 Explorador FTP Integrado
- Acceso directo a directorios clave de la consola:
  - `/dev_hdd0/GAMES`
  - `/dev_hdd0/PS3ISO`
  - `/dev_hdd0/packages`
- Listado navegable con diferenciación de carpetas y archivos.

### 📺 Notificaciones OSD en TV y Controles de Consola
- Proyección de mensajes de texto en la pantalla de la TV conectada a la PS3.
- Control manual del ventilador (nivel de velocidad personalizado).
- Activación de pitidos de prueba (**Buzzer**).
- Control de energía: **Reiniciar** y **Apagar** la consola remotamente.

---

## 🛠️ Requisitos Técnicos

- **Dispositivo Móvil**: Android 8.0 (API 26) o superior.
- **Consola PS3**: PlayStation 3 con Custom Firmware (CFW) o PS3HEN con **webMAN MOD** instalado y servicio FTP habilitado (puerto 21).
- **Conectividad**: La consola y el dispositivo móvil deben estar conectados a la misma red local (Wi-Fi / Ethernet).

---

## 🚀 Instalación y Compilación

1. Clonar el repositorio:
   ```bash
   git clone https://github.com/victgolozkiv/PS3ControllerKotlin.git
   ```
2. Abrir en **Android Studio** (Jellyfish / Koala o superior).
3. Compilar el archivo APK:
   ```bash
   ./gradlew assembleDebug
   ```
4. El ejecutable se generará en:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 🛠️ Tecnologías Utilizadas

- **Lenguaje**: Kotlin
- **Asincronía**: Kotlin Coroutines & Flow
- **Cliente HTTP**: OkHttp3
- **Cliente FTP**: Apache Commons Net FTP
- **UI Design**: Modern Dark Neon / Cyberpunk Aesthetic (XML Layouts, Custom Drawables, DataBinding support)
- **Servicios**: Android Foreground Service & Custom Notification Channels

---

## 📄 Licencia

Este proyecto se distribuye bajo la licencia MIT.
