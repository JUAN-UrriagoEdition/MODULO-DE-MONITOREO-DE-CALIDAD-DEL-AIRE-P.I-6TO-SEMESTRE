/*
 * ============================================================
 *  Proyecto P.I6Def — Monitor de Gases
 *  Plataforma : ESP32-S3-WROOM-1
 *  Sensores   : SEN0564 / GM-702B  → CO   (GPIO17, ADC1_CH0)
 *               SEN0574 / GM-102B  → NO₂  (GPIO18, ADC1_CH1)
 *               CCS811             → eCO₂ + TVOC (I2C)
 *
 *  Protocolo Serial (115200 baud, compatible AirMonitorGUI):
 *    CO:<ppm>,NO2:<ppm>,CO2:<ppm>,VOC:<ppb>\n
 *  Ejemplo:
 *    CO:85.3,NO2:0.42,CO2:512.0,VOC:180.0
 *
 *  Mapa de pines:
 *    SDA_CCS811  → GPIO39
 *    SCL_CCS811  → GPIO38
 *    SEN0564 CO  → GPIO17  (ADC1_CH0)
 *    SEN0574 NO2 → GPIO18  (ADC1_CH1)
 *
 *  Notas CCS811:
 *    WAKE → conectar a GND fijo (siempre activo)
 *    ADDR → conectar a GND fijo (dirección 0x5A)
 *    INT  → sin conectar (no se usa por polling)
 *
 *  ── Notas de conversión ───────────────────────────────────
 *
 *  GM-702B (CO) — sensor resistivo DIRECTO:
 *    A mayor CO → Rs baja → Vout SUBE (más voltaje en RL).
 *    Curva log-log: Rs/R0 = A·(ppm)^B
 *    De datasheet Fig.3, puntos CO:
 *      10 ppm  → Rs/R0 ≈ 0.50
 *      100 ppm → Rs/R0 ≈ 0.25
 *      1000ppm → Rs/R0 ≈ 0.12
 *    Pendiente log-log ≈ -0.33 → B = -0.33, A = 1.0 (en aire)
 *    Con Vc=5V y RL ajustable; DFRobot fija RL para Vout 0-3.3V.
 *    Fórmula inversa: ppm_CO = (R0/Rs)^(1/|B|) · 1 ppm_ref
 *    Simplificada con Vout (DFRobot Gravity 3.3V):
 *      Rs/R0 = (Vc - Vout) / Vout  · (RL/R0)  → necesita R0 calibrado
 *    *** Usamos la curva linealizada del datasheet Fig.6: ***
 *      Vout(V) vs ppm es aproximadamente lineal en 10–1000 ppm
 *      A 150 ppm CO → Vout ≈ 1.5 V (punto de referencia Winsen)
 *      ppm_CO ≈ (Vout / V_CO_REF) · CO_REF_PPM
 *    Ajusta V_CO_REF con tu calibración.
 *
 *  GM-102B (NO₂) — sensor resistivo INVERSO:
 *    A mayor NO₂ → Rs SUBE → Vout BAJA (menos voltaje en RL).
 *    Curva (Fig.3) casi lineal en 0–10 ppm:
 *      Rs/R0 ≈ 1.0 + 0.36·ppm  (aproximación lineal de Fig.3)
 *    Con Vout = Vc · RL/(Rs+RL):
 *      Al subir Rs, Vout baja desde V0 (aire limpio).
 *      ΔV = V0 - Vout es proporcional a la concentración.
 *      ppm_NO2 = ΔV / FACTOR_NO2
 *    Ajusta V0_NO2 midiendo el voltaje en aire limpio.
 *
 *  Librería requerida: SparkFun CCS811 (Library Manager: "SparkFun CCS811")
 * ============================================================
 */

#include <Arduino.h>
#include <Wire.h>
#include <math.h>
#include <SparkFunCCS811.h>

// ─── Pines ────────────────────────────────────────────────
#define PIN_SDA         39   // SDA — libre en N16R8
#define PIN_SCL         38   // SCL
// CCS811: WAKE y ADDR conectados a GND fijo en PCB (sin control por GPIO)

#define PIN_CO          17   // SEN0564/GM-702B — ADC1_CH0
#define PIN_NO2         18   // SEN0574/GM-102B — ADC1_CH1

// ─── ADC ──────────────────────────────────────────────────
#define VREF            3.3f
#define ADC_MAX         4095.0f

// ─── GM-702B — CO (Monóxido de Carbono) ───────────────────
// Punto de referencia de la curva lineal (Fig. 6 datasheet):
//   A 150 ppm CO → Vout ≈ 1.5 V (condiciones estándar Winsen)
// Ajusta V_CO_REF midiendo con gas de referencia conocido.
#define CO_REF_PPM      150.0f
#define V_CO_REF        1.50f    // ← voltaje a CO_REF_PPM (calibrar)

// ─── GM-102B — NO₂ (Dióxido de Nitrógeno) ─────────────────
// El sensor es INVERSO: Vout BAJA cuando sube NO₂.
// V0_NO2: voltaje en aire limpio (0 ppm). Medir antes de usar.
// FACTOR_NO2: caída de voltaje por ppm (de Fig. 6, ~0.2 V/ppm)
#define V0_NO2          3.10f    // ← voltaje en aire limpio (calibrar)
#define FACTOR_NO2      0.20f    // ← V/ppm  (calibrar con ref)

// ─── Umbrales de alerta ────────────────────────────────────
// CO  (NIOSH: TWA 35 ppm, STEL 200 ppm, IDLH 1200 ppm)
#define UMBRAL_CO_ADV    35.0f
#define UMBRAL_CO_PEL   200.0f

// NO₂ (OMS: 0.2 ppm anual; NIOSH STEL 1 ppm, IDLH 20 ppm)
#define UMBRAL_NO2_ADV    0.5f
#define UMBRAL_NO2_PEL    1.0f

// CO₂ (ASHRAE: 1000 ppm recirculación; NIOSH: 5000 ppm TWA)
#define UMBRAL_CO2_ADV  1000
#define UMBRAL_CO2_PEL  2000

// TVOC (ppb — IAQ: <220 buena, <660 moderada, >660 mala)
#define UMBRAL_VOC_ADV   220
#define UMBRAL_VOC_PEL   660

// ─── Intervalo ────────────────────────────────────────────
#define INTERVALO_MS    1000

// ─── Objetos ──────────────────────────────────────────────
CCS811 ccs(0x5B);           // SparkFunCCS811 — ADDR pin LOW → 0x5A
bool ccsDisponible = false;
unsigned long ultimaLectura = 0;

// ══════════════════════════════════════════════════════════
//  CONVERSIONES
// ══════════════════════════════════════════════════════════

float rawAVoltaje(int raw) {
  return (raw / ADC_MAX) * VREF;
}

/**
 * GM-702B → ppm CO
 * Modelo lineal derivado de Fig.6 datasheet: Vout proporcional a ppm.
 * Al concentración 0 el Vout teórico es ~0 V.
 */
float voltajeACO(float v) {
  if (v <= 0.0f) return 0.0f;
  return (v / V_CO_REF) * CO_REF_PPM;
}

/**
 * GM-102B → ppm NO₂
 * Sensor inverso: Vout baja cuando sube NO₂.
 * ΔV = V0 - Vout; ppm = ΔV / FACTOR_NO2
 * Si Vout > V0 (ruido/offset), devuelve 0.
 */
float voltajeANO2(float v) {
  float delta = V0_NO2 - v;
  if (delta < 0.0f) return 0.0f;
  return delta / FACTOR_NO2;
}

// ── Impresión con nivel de alerta ─────────────────────────
void imprimirEstado(const char* nombre, float valor,
                    const char* unidad, float adv, float pel) {
  const char* nivel;
  if      (valor >= pel) nivel = "[PELIGRO]    ";
  else if (valor >= adv) nivel = "[ADVERTENCIA]";
  else                   nivel = "[OK]         ";
  Serial.printf("# %s  %-6s %8.3f %s\n", nivel, nombre, valor, unidad);
}

// ══════════════════════════════════════════════════════════
//  SETUP
// ══════════════════════════════════════════════════════════
void setup() {
  Serial.begin(115200);
  unsigned long t0 = millis();
  while (!Serial && (millis() - t0 < 3000));

  Serial.println(F("# ═══════════════════════════════════════════"));
  Serial.println(F("# Monitor de Gases P.I6Def — ESP32-S3"));
  Serial.println(F("# SEN0564/GM-702B CO | SEN0574/GM-102B NO2 | CCS811"));
  Serial.println(F("# Formato datos: CO:<ppm>,NO2:<ppm>,CO2:<ppm>,VOC:<ppb>"));
  Serial.println(F("# ═══════════════════════════════════════════"));

  // CCS811
  Wire.begin(PIN_SDA, PIN_SCL);

  if (ccs.begin() == false) {
    Serial.println(F("# [ERROR] CCS811 no detectado — revisa I2C"));
    ccsDisponible = false;
  } else {
    Serial.println(F("# [OK] CCS811 listo"));
    ccsDisponible = true;
  }

  // ADC
  analogReadResolution(12);
  analogSetAttenuation(ADC_11db);
  Serial.println(F("# [OK] ADC 12-bit, 0-3.3V"));
  Serial.println(F("# ⚠  CALIBRAR: V_CO_REF, V0_NO2, FACTOR_NO2"));
  Serial.println(F("# ⚠  CCS811 necesita ~20 min calentamiento"));
  Serial.println(F("# ═══════════════════════════════════════════"));
}

// ══════════════════════════════════════════════════════════
//  LOOP
// ══════════════════════════════════════════════════════════
void loop() {
  unsigned long ahora = millis();

  if (ahora - ultimaLectura >= INTERVALO_MS) {
    ultimaLectura = ahora;

    // ── GM-702B — CO ──────────────────────────────────
    float vCO   = rawAVoltaje(analogRead(PIN_CO));
    float ppmCO = voltajeACO(vCO);

    // ── GM-102B — NO₂ ─────────────────────────────────
    float vNO2   = rawAVoltaje(analogRead(PIN_NO2));
    float ppmNO2 = voltajeANO2(vNO2);

    // ── CCS811 — eCO₂ / TVOC ──────────────────────────
    float eco2 = 0.0f, tvoc = 0.0f;
    if (ccsDisponible && ccs.dataAvailable()) {
      ccs.readAlgorithmResults();
      eco2 = (float)ccs.getCO2();
      tvoc = (float)ccs.getTVOC();
    }

    // ── Línea de datos para la GUI (parseable) ─────────
    Serial.printf("CO:%.2f,NO2:%.3f,CO2:%.1f,VOC:%.1f\n",
                  ppmCO, ppmNO2, eco2, tvoc);

    // ── Log de estado para monitoreo serial ────────────
    Serial.printf("# t=%lus  Vco=%.3fV  Vno2=%.3fV\n",
                  ahora / 1000, vCO, vNO2);
    imprimirEstado("CO  ", ppmCO,  "ppm", UMBRAL_CO_ADV,  UMBRAL_CO_PEL);
    imprimirEstado("NO2 ", ppmNO2, "ppm", UMBRAL_NO2_ADV, UMBRAL_NO2_PEL);
    imprimirEstado("CO2 ", eco2,   "ppm", UMBRAL_CO2_ADV, UMBRAL_CO2_PEL);
    imprimirEstado("VOC ", tvoc,   "ppb", UMBRAL_VOC_ADV, UMBRAL_VOC_PEL);
    Serial.println(F("#"));
  }
}
