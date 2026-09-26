# 🎮 PS3 Controller Pro

> **Aplicación Android para controlar tu PlayStation 3 desbloqueada (CFW/HEN) directamente desde tu teléfono vía Wi-Fi.**

Gestiona remotamente tu PS3 con webMAN Mod habilitado: monitorea temperaturas, instala juegos, sube temas animados, explora y administra archivos por FTP, y mucho más — todo desde una interfaz moderna con diseño futurista estilo glassmorphism.

---

## 📸 Características Principales

### ⚡ Consola — Monitoreo y Control en Tiempo Real
- **Conexión directa a PS3** ingresando la IP de tu consola con webMAN Mod activo.
- **Telemetría del sistema en vivo**:
  - 🔥 Temperatura del CPU (Cell Broadband Engine)
  - ⚡ Temperatura del GPU (RSX Reality Synthesizer)
  - 🌀 Velocidad actual del ventilador (%)
  - 💾 Espacio libre en disco duro (HDD)
- **Auto-Refresh**: Actualización automática cada 5 segundos con un solo toggle.
- **Control de Energía**: Reiniciar o apagar tu PS3 remotamente.

### 📦 PKG & Juegos — Instalación y Montaje
- **Instalador de PKG en segundo plano**: Sube archivos `.pkg` a `/dev_hdd0/packages/` mediante FTP con barra de progreso en tiempo real. La subida continúa incluso si minimizas la app gracias al **Foreground Service** con notificación persistente.
- **Montaje de juegos**: Monta ISOs o carpetas de juegos directamente indicando la ruta (`/dev_hdd0/GAMES/BLES01234`), o desmonta el juego activo.

### 🎨 Temas & Fondos de Pantalla
- **Subir temas animados (.p3t)**: Sube un archivo `.p3t` directamente a `/dev_hdd0/theme/` en tu PS3 por FTP. Después solo ve a *Ajustes > Ajustes de Temas > Tema* y selecciónalo.
- **Subir fondos de pantalla (JPG/PNG)**: Sube imágenes a cualquier carpeta de la PS3 (por defecto `/dev_hdd0/theme/`).
- **Guía integrada paso a paso**: Instrucciones para aplicar temas:
  1. **Vía app FTP** → Sube el `.p3t` y aplícalo desde ajustes.
  2. **Vía USB FAT32** → Copia a `PS3/THEME/` e instala desde la XMB.

### 📁 FTP Explorer — Gestor de Archivos Completo para PS3
Un explorador de archivos **interactivo y dinámico**, inspirado en apps populares como **ZArchiver**, **Solid Explorer** o **Filza** en iOS:

| Función | Descripción |
|---------|-------------|
| 🗂️ **Navegación de directorios** | Toca cualquier carpeta para entrar. Usa `⬆️ Subir (..)` para retroceder. |
| 📍 **Barra de ruta activa** | Muestra la ruta FTP actual en todo momento. |
| ⚡ **Accesos rápidos** | Botones directos a: `dev_hdd0`, `GAMES`, `PS3ISO`, `packages`, `theme`. |
| ➕ **Subir cualquier archivo** | Selecciona cualquier archivo de tu Android y súbelo a la carpeta actual. |
| 📁 **Crear carpetas** | Crea nuevas carpetas remotamente en el disco de la PS3. |
| ✏️ **Renombrar** | Renombra archivos o carpetas directamente en la PS3. |
| 🗑️ **Eliminar** | Elimina archivos o directorios vacíos con confirmación antes de borrar. |
| ⬇️ **Descargar al teléfono** | Descarga cualquier archivo de la PS3 directamente a la carpeta *Descargas* de tu Android. |
| 🎯 **Iconos contextuales** | Diferencia visual por tipo: `.pkg` 📦, `.iso` 💿, `.p3t` 🎨, imágenes 🖼️, audio 🎵, vídeo 🎬, ejecutables ⚙️, documentos 📄. |

### ⚙️ Controles & TV
- **📺 Notificación en pantalla TV**: Envía mensajes emergentes (popup) que aparecen en tiempo real en la TV mientras juegas o ves películas.
- **🌀 Ajuste manual del ventilador (SYSCON)**: Controla manualmente la velocidad del ventilador con un slider de 0% a 100%.
- **🔊 Buzzer (Pitido)**: Emite un doble pitido en la consola (útil para localización o confirmación remota).

---

## 👈👉 Navegación por Deslizamiento (Swipe)

La app utiliza **ViewPager2 + TabLayout** para navegar entre las 5 secciones:

| # | Pestaña | Icono |
|---|---------|-------|
| 1 | Consola | ⚡ |
| 2 | PKG & Juegos | 📦 |
| 3 | Temas & Fondos | 🎨 |
| 4 | FTP Explorer | 📁 |
| 5 | Controles & TV | ⚙️ |

Solo **desliza a la izquierda o derecha** con el dedo para cambiar de pestaña de forma fluida.

---

## 🛠️ Requisitos

| Requisito | Detalle |
|-----------|---------|
| **PS3** | Con Custom Firmware (CFW) o HEN habilitado |
| **webMAN Mod** | Instalado y activo en la PS3 para la API HTTP y FTP |
| **Red Wi-Fi** | PS3 y Android deben estar en la misma red local |
| **Android** | Versión 7.0+ (API 24) |

---

## 📲 Instalación

### Opción 1: Descargar APK directamente
1. Ve a la carpeta [`releases/`](releases/) de este repositorio.
2. Descarga `PS3ControllerPro-v2.0-debug.apk`.
3. Instálalo en tu Android (habilita "Orígenes desconocidos" si es necesario).

### Opción 2: Compilar desde el código fuente
```bash
git clone https://github.com/victgolozkiv/PS3ControllerKotlin.git
cd PS3ControllerKotlin
./gradlew assembleDebug --no-daemon
```
El APK generado estará en: `app/build/outputs/apk/debug/app-debug.apk`

---

## 🏗️ Stack Tecnológico

| Componente | Tecnología |
|------------|------------|
| Lenguaje | **Kotlin** |
| UI Framework | Android Views + ViewPager2 + TabLayout (Material Design) |
| Networking HTTP | OkHttp 4.12 |
| Networking FTP | Apache Commons Net 3.10 |
| Concurrencia | Kotlin Coroutines |
| Subida en segundo plano | Android Foreground Service + NotificationCompat |
| Diseño | Glassmorphism dark theme con acentos neón |

---

## 📂 Estructura del Proyecto

```
app/src/main/
├── java/com/antigravity/ps3controller/
│   ├── MainActivity.kt          # Actividad principal con ViewPager2 y toda la lógica de UI
│   ├── FtpManager.kt            # Cliente FTP: listFiles, upload, download, mkdir, delete, rename
│   ├── FtpAdapter.kt            # RecyclerView Adapter para el explorador FTP interactivo
│   └── PkgUploadService.kt      # Foreground Service para subida de PKG en segundo plano
├── res/
│   ├── layout/
│   │   ├── activity_main.xml    # Layout principal (Header + TabLayout + ViewPager2)
│   │   ├── page_console.xml     # Pestaña: Consola (conexión, telemetría, energía)
│   │   ├── page_pkg.xml         # Pestaña: PKG & Juegos (instalador, montaje)
│   │   ├── page_themes.xml      # Pestaña: Temas & Fondos (.p3t, wallpapers, guía)
│   │   ├── page_ftp.xml         # Pestaña: Explorador FTP interactivo
│   │   ├── page_controls.xml    # Pestaña: Controles & TV (popup, fan, buzzer)
│   │   └── item_ftp_file.xml    # Item row del RecyclerView FTP
│   ├── drawable/                # Fondos glassmorphism, botones neón, progress bars
│   └── values/
│       ├── colors.xml           # Paleta de colores (neón, dark theme, badges)
│       ├── strings.xml
│       └── themes.xml
```

---

## 🔐 Permisos

| Permiso | Razón |
|---------|-------|
| `INTERNET` | Comunicación HTTP con webMAN y FTP con la PS3 |
| `FOREGROUND_SERVICE` | Subida de PKG en segundo plano con notificación |
| `POST_NOTIFICATIONS` | Notificación de progreso de subida (Android 13+) |
| `WRITE_EXTERNAL_STORAGE` | Descarga de archivos FTP a la carpeta Descargas |

---

## ⚠️ Aviso Legal

Esta aplicación está diseñada para uso personal y educativo con consolas PlayStation 3 que tengan Custom Firmware instalado legítimamente por el usuario. No promueve la piratería ni la distribución ilegal de contenido protegido por derechos de autor. El usuario es responsable del uso que le dé a esta herramienta.

---

## 👨‍💻 Autor

**Victor** — Desarrollado con Kotlin + Android Studio

---

## 📄 Licencia

Este proyecto es de código abierto. Puedes usarlo, modificarlo y distribuirlo libremente.
