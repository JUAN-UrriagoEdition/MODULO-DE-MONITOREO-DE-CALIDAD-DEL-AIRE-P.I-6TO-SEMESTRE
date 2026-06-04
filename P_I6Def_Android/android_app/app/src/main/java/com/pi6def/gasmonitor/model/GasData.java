package com.pi6def.gasmonitor.model;

public class GasData {
    public double co;
    public double no2;
    public double co2;
    public double voc;
    public String estado;
    public long t;

    // Umbrales
    public static final double CO_ADV  = 35,   CO_PEL  = 200;
    public static final double NO2_ADV = 0.5,  NO2_PEL = 1.0;
    public static final double CO2_ADV = 1000, CO2_PEL = 2000;
    public static final double VOC_ADV = 220,  VOC_PEL = 660;

    public static final double CO_MAX  = 1200;
    public static final double NO2_MAX = 10;
    public static final double CO2_MAX = 5000;
    public static final double VOC_MAX = 1000;

    public boolean esAlarma()       { return "ALARMA".equals(estado); }
    public boolean esPeligro()      { return "PELIGRO".equals(estado); }
    public boolean esAdvertencia()  { return "ADVERTENCIA".equals(estado); }
}
