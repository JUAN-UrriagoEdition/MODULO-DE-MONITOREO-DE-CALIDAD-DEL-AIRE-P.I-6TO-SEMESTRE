# 🌬️ Estación Modular de Calidad del Aire (AQ)
### Módulo de Gases Electroquímicos — Ingeniería Electrónica, UCEVA 2026

<div align="center">

![Estado](https://img.shields.io/badge/estado-prototipo%20funcional-brightgreen)
![Plataforma](https://img.shields.io/badge/plataforma-ESP32--S3--WROOM--1U-blue)
![Licencia](https://img.shields.io/badge/licencia-académica-orange)
![Semestre](https://img.shields.io/badge/semestre-6to-lightgrey)

</div>

---

## 📋 Descripción General

Este repositorio documenta el **Módulo de Gases Electroquímicos** de la Estación Modular de Calidad del Aire (AQ), desarrollado como proyecto integrador de 6to semestre en la **Facultad de Ingeniería Electrónica de la Unidad Central del Valle del Cauca (UCEVA)**.

El módulo mide en tiempo real las concentraciones de **monóxido de carbono (CO)**, **dióxido de nitrógeno (NO₂)**, **CO₂ equivalente** y **compuestos orgánicos volátiles (COV)** tanto en entornos interiores como exteriores, publicando los datos a un dashboard web mediante WiFi.

> 📄 **[Descargar Informe Técnico Completo (WORD)](./ESTACION_AQ_ACTUALIZADO.docx)**
> 📄 **[Descargar Repositorio (.bat)](./Descargar_Repo_Monitor_de_Gases.bat)**

---

## 🎯 Motivación

La contaminación del aire representa uno de los mayores problemas de salud pública a nivel mundial. En el municipio de **Tuluá y el Valle del Cauca**, la ausencia de redes densas de monitoreo continuo limita la detección oportuna de episodios críticos de contaminación, especialmente en corredores viales de alto tráfico y en espacios interiores de instituciones educativas.

Los sistemas convencionales de monitoreo tienen costos de entre **USD 50.000 y 200.000** por estación. Este módulo propone una alternativa de **bajo costo** basada en sensores MEMS, con criterios rigurosos de instrumentación, calibración y validación.

---

## ⚙️ Arquitectura del Sistema

La cadena de instrumentación sigue el flujo completo:

```
Sensor MEMS → Filtro RC (fc ≈ 10.6 Hz) → Buffer LMV321 → ADC 12-bit ESP32-S3 → Procesamiento → WiFi → Hub → Dashboard
```

El sensor digital **CCS811** se conecta directamente por bus **I²C**, sin requerir acondicionamiento analógico externo.

---

## 🔬 Sensores y Componentes Principales

| Componente | Función | Interfaz |
|---|---|---|
| **SEN0564** (DFRobot) | CO — 5 a 5000 ppm | Analógica (0.4–2.0 V) |
| **SEN0574** (DFRobot) | NO₂ — 0.1 a 10 ppm | Analógica |
| **CCS811** (ScioSense) | CO₂eq + TVOC | Digital I²C |
| **ESP32-S3-WROOM-1U** | MCU, ADC 12-bit, WiFi | — |
| **LMV321** | Buffer seguidor de voltaje | — |
| **REF2033A** | Referencia de voltaje dual 3.3/1.65 V | — |
| **LP38692MP-3.3** | Regulador LDO 3.3 V | — |
| **LED RGB** | Indicación visual de calidad del aire | PWM |

---

## 📐 Diseño de Acondicionamiento Analógico

- **Filtro RC pasabajas** de primer orden: `R = 10 kΩ`, `C = 1.5 µF` → `fc ≈ 10.6 Hz`
- **Buffer LMV321** en configuración seguidor unitario (ganancia 1) para desacoplar la impedancia del ADC
- **Referencia REF2033A** con baja deriva térmica para estabilidad en la conversión ADC
- **ADC SAR de 12 bits** del ESP32-S3: resolución teórica de `≈ 0.806 mV/bit`
- **Sobremuestreo y promediado** (N = 32–64 muestras) para mejorar la resolución efectiva (ENOB ≥ 11 bits)

---

## 💡 Indicación Visual

El LED RGB indica el nivel de calidad del aire mediante colores controlados por PWM (5 kHz, 8 bits):

| Color | Estado | Umbral |
|---|---|---|
| 🟢 Verde | Limpio | < 40% de escala |
| 🟡 Ámbar | Advertencia | 40–70% de escala |
| 🔴 Rojo | Crítico | > 70% de escala |

---

## 📡 Formato de Mensajes JSON (Contrato de Datos)

El módulo publica al hub central mensajes con la siguiente estructura mínima:

```json
{
  "module": "gas_module_01",
  "environment": "indoor",
  "location": "UCEVA - Aula 201",
  "ts": 1745000000,
  "metrics": {
    "co_ppm": 1.2,
    "no2_ppm": 0.05,
    "eco2_ppm": 520,
    "tvoc_ppb": 35,
    "quality": "ok",
    "temp_comp": 24.5,
    "rh_comp": 58.0
  }
}
```

---

## 🔄 Estados del Firmware

| Estado | Descripción |
|---|---|
| `warmup` | Calentamiento inicial del sensor (< 2 min) |
| `ok` | Operación normal |
| `fault` | Sensor desconectado o señal fuera de rango |
| `saturation` | Señal en los extremos del rango ADC |
| `suspect` | Alta variabilidad o lecturas inconsistentes |

---

## 📦 Lista de Materiales (BOM)

Los componentes fueron adquiridos mediante el pedido **Mouser N.° 279751168** (enviado el 21 de abril de 2026). El costo total del pedido fue de aproximadamente **USD 48** más aranceles de importación colombianos.

Los sensores SEN0564, SEN0574 y el LED RGB se adquirieron de forma local/separada.

---

## 🧪 Pruebas y Criterios de Aceptación

| ID | Prueba | Criterio |
|---|---|---|
| CP01 | Rango analógico SEN0564 (CO) | V_out varía de forma monotónica en 0.4–2.0 V |
| CP04 | Respuesta del filtro RC | Atenuación a fc = −3 dB ± 1 dB; a 10·fc ≥ −20 dB |
| CP06 | Publicación de mensajes JSON | ≥ 5 mensajes válidos en 5 minutos |
| CP07 | Estado warmup al encender | `quality = warmup` durante ≥ 60 s |
| CP08 | Estado fault ante desconexión | `quality = fault` en ≤ 3 ciclos de publicación |

---

## 👥 Equipo

| Nombre | Código |
|---|---|
| David Ortega Ruiz | 240232001 |
| Juan Fernando Urriago Hernández | 240232002 |
| Sergio Peñaranda Agudelo | 240232004 |
| Miguel Ángel Parra Gutiérrez | 240232013 |

**Asignatura integradora:** Instrumentación I  
**Docente:** Álvaro Hernando Salazar Victoria  
**Facultad de Ingeniería Electrónica — UCEVA, 2026**

---

## 📚 Referencias Clave

- Mead et al. (2013) — *Atmospheric Environment* — Redes urbanas con sensores electroquímicos
- Kumar et al. (2015) — *Environment International* — Sensores de bajo costo para calidad del aire
- Spinelle et al. (2015) — *Sensors and Actuators B* — Calibración en campo con MLR
- Zimmerman et al. (2018) — *Atmospheric Measurement Techniques* — Calibración con Random Forest

---

> 📄 **[Descargar Informe Técnico Completo (PDF)](./ESTACIÓN_MODULAR_DE_CALIDAD_DEL_AIRE.pdf)**
