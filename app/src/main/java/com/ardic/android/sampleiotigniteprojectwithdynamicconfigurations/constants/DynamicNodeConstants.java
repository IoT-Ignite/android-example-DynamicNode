package com.ardic.android.sampleiotigniteprojectwithdynamicconfigurations.constants;

/**
 * Created by yavuz.erzurumlu on 30.11.2016.
 */

public class DynamicNodeConstants {
    public static final String TYPE = "DYNAMIC NODE - DHT11 SENSOR";
    public static final String TEMPERATURE_SENSOR = "DHT11 Temperature Sensor";
    public static final String HUMIDITY_SENSOR = "DHT11 Humidity Sensor";
    public static final String ACTUATOR_BLUE_LED = "Blue LED Actuator";
    public static final String LED_ON_ACTION = "{\"status\":1.0}";
    public static final String LED_OFF_ACTION = "{\"status\":0.0}";


    private DynamicNodeConstants() {
    }
}
