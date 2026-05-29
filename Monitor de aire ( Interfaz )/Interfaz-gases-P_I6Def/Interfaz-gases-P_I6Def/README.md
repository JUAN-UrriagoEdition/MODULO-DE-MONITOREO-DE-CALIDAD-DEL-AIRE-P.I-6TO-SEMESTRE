# Interfaz-gases P.I6Def — Monitor de Gases ESP32-S3
## Requisitos
- NetBeans IDE 17+ con soporte para proyectos Java Free-Form / Ant
- JDK 17 o superior
- ESP32-S3 corriendo el firmware P_I6Def_ESP32S3_fixed.ino
  (protocolo serial 115200 baud: CO:<ppm>,NO2:<ppm>,CO2:<ppm>,VOC:<ppb>)

---

## Abrir en NetBeans (primera vez)

1. File → Open Project → seleccionar esta carpeta (Interfaz-gases-P_I6Def)
2. NetBeans detecta el build.xml de Ant automáticamente
3. **Run → Build Project (F11)**
   - Ant descarga jSerialComm-2.11.0.jar automáticamente a lib/
   - Si no hay internet: descarga el JAR manualmente de
     https://github.com/Fazecast/jSerialComm/releases
     y cópialo a lib/jSerialComm-2.11.0.jar
4. **Run → Run Project (F6)** para ejecutar la GUI

---

## Si NetBeans no reconoce el classpath

Project Properties → Libraries → Add JAR/Folder → selecciona:
  lib/jSerialComm-2.11.0.jar

---

## Protocolo Serial (ESP32-S3 → Java)

Líneas de datos (parseadas por la GUI):
  CO:85.30,NO2:0.042,CO2:512.0,VOC:180.0

Líneas de log (ignoradas por la GUI, empiezan con #):
  # [OK] CCS811 listo
  # t=5s  Vco=1.234V  Vno2=2.987V

Baud rate: 115200
Terminador: \n

---

## Funciones de la GUI

- Indicadores circulares (gauges) con colores por nivel de alerta
- Gráficas históricas de los últimos 60 segundos
- Selector de puerto serial con botón de refresco
- Modo DEMO (simula datos sin hardware conectado)
- Estado de conexión en tiempo real
