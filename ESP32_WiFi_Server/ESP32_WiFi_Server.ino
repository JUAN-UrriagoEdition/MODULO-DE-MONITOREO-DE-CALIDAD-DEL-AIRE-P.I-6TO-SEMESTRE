/* ============================================================
 *
 *  Mapa de pines:
 *    SDA_CCS811 → GPIO39
 *    SCL_CCS811 → GPIO38
 *    SEN0564 CO  → GPIO17
 *    SEN0574 NO2 → GPIO18
 *
 * ============================================================
 */

#include <Arduino.h>
#include <Wire.h>
#include <math.h>
#include <WiFi.h>
#include <WebServer.h>
#include <SparkFunCCS811.h>

// ─── WiFi ─────────────────────────────────────────────────
#define WIFI_SSID "Familia Urriago" 
#define WIFI_PASS "3113506859"     

// ─── Pines ────────────────────────────────────────────────
#define PIN_SDA 39
#define PIN_SCL 38
#define PIN_CO 17
#define PIN_NO2 18

// ─── ADC ──────────────────────────────────────────────────
#define VREF 3.3f
#define ADC_MAX 4095.0f

// ─── Calibración sensores ──────────────────────────────────
#define CO_REF_PPM 150.0f
#define V_CO_REF 1.50f

// GM-102B (NO2) — sensor INVERSO: Vout BAJA cuando sube NO2
// ──────────────────────────────────────────────────────────
#define V0_NO2 3.10f      
#define FACTOR_NO2 0.31f  

// ─── Umbrales ─────────────────────────────────────────────
#define U_CO_ADV 35.0f
#define U_CO_PEL 200.0f
#define U_NO2_ADV 0.5f
#define U_NO2_PEL 1.0f
#define U_CO2_ADV 1000
#define U_CO2_PEL 2000
#define U_VOC_ADV 220
#define U_VOC_PEL 660

// ─── Diagrama de estados ───────────────────────────────────
typedef enum { REPOSO,
               ADVERTENCIA,
               PELIGRO,
               ALARMA } Estado;
Estado estadoActual = REPOSO;
unsigned long tiempoPeligro = 0;  
#define TIEMPO_ALARMA_MS 30000   

// ─── Servidor HTTP ─────────────────────────────────────────
WebServer server(80);

// ─── Objetos globales ─────────────────────────────────────
CCS811 ccs(0x5A);
bool ccsOK = false;
unsigned long ultimaLectura = 0;
#define INTERVALO_MS 1000

// ─── Valores globales (actualizados en loop) ───────────────
float g_co = 0, g_no2 = 0, g_co2 = 415, g_voc = 0;

// ══════════════════════════════════════════════════════════
//  CONVERSIONES
// ══════════════════════════════════════════════════════════
float rawAV(int raw) {
  return (raw / ADC_MAX) * VREF;
}
float vACO(float v) {
  return v <= 0 ? 0 : (v / V_CO_REF) * CO_REF_PPM;
}
float vANO2(float v) {
  float d = V0_NO2 - v;
  return d < 0 ? 0 : d / FACTOR_NO2;
}

// ══════════════════════════════════════════════════════════
//  DIAGRAMA DE ESTADOS
// ══════════════════════════════════════════════════════════
bool enPeligro(float co, float no2, float co2, float voc) {
  return co >= U_CO_PEL || no2 >= U_NO2_PEL || co2 >= U_CO2_PEL || voc >= U_VOC_PEL;
}
bool enAdvertencia(float co, float no2, float co2, float voc) {
  return co >= U_CO_ADV || no2 >= U_NO2_ADV || co2 >= U_CO2_ADV || voc >= U_VOC_ADV;
}
int gasesEnPeligro(float co, float no2, float co2, float voc) {
  return (co >= U_CO_PEL ? 1 : 0) + (no2 >= U_NO2_PEL ? 1 : 0) + (co2 >= U_CO2_PEL ? 1 : 0) + (voc >= U_VOC_PEL ? 1 : 0);
}

void actualizarEstado(float co, float no2, float co2, float voc) {
  bool pel = enPeligro(co, no2, co2, voc);
  bool adv = enAdvertencia(co, no2, co2, voc);
  int nPel = gasesEnPeligro(co, no2, co2, voc);

  switch (estadoActual) {

    case REPOSO:
      if (pel) {
        estadoActual = PELIGRO;
        tiempoPeligro = millis();
      } else if (adv) {
        estadoActual = ADVERTENCIA;
      }
      break;

    case ADVERTENCIA:
      if (pel) {
        estadoActual = PELIGRO;
        tiempoPeligro = millis();
      } else if (!adv) {
        estadoActual = REPOSO;
      }
      break;

    case PELIGRO:
      if (!pel) {
        estadoActual = adv ? ADVERTENCIA : REPOSO;
        tiempoPeligro = 0;
      } else if (nPel >= 2 || (millis() - tiempoPeligro >= TIEMPO_ALARMA_MS)) {
        estadoActual = ALARMA;
      }
      break;

    case ALARMA:
      if (!pel && !adv) {
        estadoActual = REPOSO;
        tiempoPeligro = 0;
      } else if (!pel) {
        estadoActual = ADVERTENCIA;
        tiempoPeligro = 0;
      }
      break;
  }
}

const char* nombreEstado() {
  switch (estadoActual) {
    case REPOSO: return "REPOSO";
    case ADVERTENCIA: return "ADVERTENCIA";
    case PELIGRO: return "PELIGRO";
    case ALARMA: return "ALARMA";
  }
  return "REPOSO";
}

// ══════════════════════════════════════════════════════════
//  MANEJADORES HTTP
// ══════════════════════════════════════════════════════════
void handleDatos() {
  server.sendHeader("Access-Control-Allow-Origin", "*");

  String json = "{";
  json += "\"co\":" + String(g_co, 2) + ",";
  json += "\"no2\":" + String(g_no2, 3) + ",";
  json += "\"co2\":" + String(g_co2, 1) + ",";
  json += "\"voc\":" + String(g_voc, 1) + ",";
  json += "\"estado\":\"" + String(nombreEstado()) + "\",";
  json += "\"t\":" + String(millis());
  json += "}";

  server.send(200, "application/json", json);
}

void handleRoot() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  String html = "<html><body style='font-family:monospace;background:#0d1117;color:#e8f4ff;padding:24px'>";
  html += "<h2>P.I6Def — Monitor de Gases</h2>";
  html += "<p>Endpoint de datos: <a style='color:#00d9ff' href='/datos'>/datos</a></p>";
  html += "<p>Estado actual: <b>" + String(nombreEstado()) + "</b></p>";
  html += "<p>CO: " + String(g_co, 2) + " ppm &nbsp;|&nbsp; ";
  html += "NO2: " + String(g_no2, 3) + " ppm &nbsp;|&nbsp; ";
  html += "CO2: " + String(g_co2, 1) + " ppm &nbsp;|&nbsp; ";
  html += "VOC: " + String(g_voc, 1) + " ppb</p>";
  html += "</body></html>";
  server.send(200, "text/html", html);
}

void handleNotFound() {
  server.send(404, "text/plain", "No encontrado");
}

// ══════════════════════════════════════════════════════════
//  SETUP
// ══════════════════════════════════════════════════════════
void setup() {
  Serial.begin(115200);
  delay(500);

  Serial.println(F("\n# P.I6Def — Monitor de Gases con WiFi"));

  // ── CCS811 ────────────────────────────────────────────
  Wire.begin(PIN_SDA, PIN_SCL);
  if (ccs.begin() == false) {
    Serial.println(F("# [ERROR] CCS811 no detectado"));
    ccsOK = false;
  } else {
    Serial.println(F("# [OK] CCS811 listo"));
    ccsOK = true;
  }

  // ── ADC ───────────────────────────────────────────────
  analogReadResolution(12);
  analogSetAttenuation(ADC_11db);

  // ── WiFi ──────────────────────────────────────────────
  Serial.printf("# Conectando a %s", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 30) {
    delay(500);
    Serial.print(".");
    intentos++;
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.printf("# [OK] WiFi conectado — IP: %s\n",
                  WiFi.localIP().toString().c_str());
    Serial.printf("# Endpoint: http://%s/datos\n",
                  WiFi.localIP().toString().c_str());
  } else {
    Serial.println(F("\n# [ERROR] No se pudo conectar al WiFi"));
  }

  // ── Servidor HTTP ──────────────────────────────────────
  server.on("/", handleRoot);
  server.on("/datos", handleDatos);
  server.onNotFound(handleNotFound);
  server.begin();
  Serial.println(F("# [OK] Servidor HTTP iniciado en puerto 80"));
  Serial.println(F("# ─────────────────────────────────────────"));
}

// ══════════════════════════════════════════════════════════
//  LOOP
// ══════════════════════════════════════════════════════════
void loop() {
  server.handleClient(); 

  unsigned long ahora = millis();
  if (ahora - ultimaLectura >= INTERVALO_MS) {
    ultimaLectura = ahora;

    float vCO = rawAV(analogRead(PIN_CO));
    float vNO2 = rawAV(analogRead(PIN_NO2));
    g_co = vACO(vCO);
    g_no2 = vANO2(vNO2);
    if (ccsOK && ccs.dataAvailable()) {
      ccs.readAlgorithmResults();
      g_co2 = (float)ccs.getCO2();
      g_voc = (float)ccs.getTVOC();
    }

    // Actualizar estado
    actualizarEstado(g_co, g_no2, g_co2, g_voc);

    // Serial — ppm + voltajes crudos para calibración
    Serial.printf("CO:%.2f,NO2:%.3f,CO2:%.1f,VOC:%.1f,EST:%s\n",
                  g_co, g_no2, g_co2, g_voc, nombreEstado());
    Serial.printf("# Vco=%.3fV  Vno2=%.3fV  (V0_NO2=%.2f FACTOR=%.3f)\n",
                  vCO, vNO2, V0_NO2, FACTOR_NO2);
  }
}
