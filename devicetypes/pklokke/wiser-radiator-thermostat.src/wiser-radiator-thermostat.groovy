/**
 *  Wiser Radiator Thermostat Handler
 *
 *  Copyright 2020 P. Klokke
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
import groovy.json.JsonOutput
import physicalgraph.zigbee.zcl.DataType

metadata
{
    definition (name: "Wiser Radiator Thermostat", namespace: "pklokke", author: "pklokke", mnmn: "SmartThings", vid: "generic-radiator-thermostat-2", genericHandler: "Zigbee") {
        capability "Actuator"
        capability "Temperature Measurement"
        capability "Thermostat"
        capability "Thermostat Heating Setpoint"
        capability "Configuration"
        capability "Battery"
        capability "Health Check"
        capability "Refresh"
        capability "Sensor"

        fingerprint profileId: "0104", inClusters: "0000,0001,0003,0009,0201,0204,FE02", outClusters: "0019, 0201", manufacturer: "Schneider Electric", model: "EH-ZB-VACT", deviceJoinName: "Wiser Radiator Thermostat"
    }

   preferences
   {
        section
        {
            input ("batteryType", "enum", title: "Battery Type", displayDuringSetup: true, options: ["NiMH": "NiMH", "Alkaline": "Alkaline"])
            input ("calibrateNow", "boolean", title: "Calibrate Now?", required: false)
            input ("calibrationDay", "enum", title: "Calibration Day of the Week", required: false, options: ["Monday": "Monday", "Tuesday": "Tuesday", "Wednesday": "Wednesday", "Thursday": "Thursday", "Thursday": "Thursday", "Saturday": "Saturday", "Sunday": "Sunday"])
            input ("calibrationTime", "string", title: "Calibration Time of Day", required: false)
            input ("temperatureOffset", "decimal", title: "Temperature Offset Calibration", required: false, description: "Adjustable in half degree steps", range: "-4..4")
        }
    }

    tiles
    {
        multiAttributeTile(name:"thermostatMulti", type:"thermostat", width:3, height:2, canChangeIcon: true)
        {
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
                attributeState("temperature", label:'${currentValue}°', icon: "st.alarm.temperature.normal",
                    backgroundColors: [
                        // Celsius
                        [value: 0, color: "#153591"],
                        [value: 7, color: "#1e9cbb"],
                        [value: 15, color: "#90d2a7"],
                        [value: 23, color: "#44b621"],
                        [value: 28, color: "#f1d801"],
                        [value: 35, color: "#d04e00"],
                        [value: 37, color: "#bc2323"],
                        // Fahrenheit
                        [value: 40, color: "#153591"],
                        [value: 44, color: "#1e9cbb"],
                        [value: 59, color: "#90d2a7"],
                        [value: 74, color: "#44b621"],
                        [value: 84, color: "#f1d801"],
                        [value: 95, color: "#d04e00"],
                        [value: 96, color: "#bc2323"]
                    ]
                )
            }
            tileAttribute("device.heatingSetpoint", key: "HEATING_SETPOINT")
            {
                attributeState("default", label: '${currentValue}', unit: "°", defaultState: true)
            }
        }
        controlTile("heatingSetpoint", "device.heatingSetpoint", "slider",
                sliderType: "HEATING",
                debouncePeriod: 1500,
                range: "device.heatingSetpointRange",
                width: 2, height: 2) {
                    state "default", action:"setHeatingSetpoint", label:'${currentValue}', backgroundColor: "#E86D13"
                }
        standardTile("refresh", "device.thermostatMode", width: 2, height: 1, inactiveLabel: false, decoration: "flat")
        {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
        {
            state "battery", label:'${currentValue}% battery', unit:""
        }
        main "thermostatMulti"
        details(["thermostatMulti", "heatingSetpoint", "battery", "refresh"])
    }
}

def parse(String description)
{
    def map = zigbee.getEvent(description)
    def descMap = zigbee.parseDescriptionAsMap(description)
    def result

    if (!map)
    {
        result = parseAttrMessage(description)
        result += parseCmdMessage(description)
    }
    else
    {
        log.warn "Unexpected event: ${map}"
    }

    log.debug "Description ${description} parsed to ${result}"

    log.debug "Device Awake, checking for messages"
    if ( state.pendingDispatch )
    {
        runIn(1, "updateSetpoints", [overwrite: true])
        state.pendingDispatch = false
    }
    if (state.calibrateValve)
    {
        runIn(1, "sendCalibrateValveCmd", [overwrite: true])
        state.calibrateValve = false
    }
    if (state.dispatchTemperatureOffset )
    {
        runIn(1, "sendTemperatureOffsetAdjust", [overwrite: true])
        state.dispatchTemperatureOffset = false
    }

    return result
}

def sendTemperatureOffsetAdjust()
{
    def hexValue = hex(state.lastTemperatureOffset*10)
    log.debug "Adjusting Temperature Offset: ${state.lastTemperatureOffset} degrees, raw value: $hexValue"
    def zigbeeCmd = zigbee.writeAttribute(THERMOSTAT_CLUSTER, LOCAL_TEMPERATURE_CALIBRATION, DataType.INT8, hex(state.lastTemperatureOffset*10))
    sendZigbeeCmds(zigbeeCmd, 500)
}

def sendCalibrateValveCmd()
{
    log.debug "Calibrating Valve"
    def zigbeeCmd = zigbee.command(THERMOSTAT_CLUSTER, CALIBRATE_VALVE_CMD)
    sendZigbeeCmds(zigbeeCmd, 500)
}

def updateSetpoints()
{
    def zigbeeWriteInfo

    log.debug "SENDING HEATING SETPOINT"

    log.debug "Command Setpoint is ${state.commandSetpoint}"

    def tempHex = String.format("%02X%02X",((int)(state.commandSetpoint * 100))%256,(int)((state.commandSetpoint * 100)/256))

    zigbeeWriteInfo = zigbee.command(THERMOSTAT_CLUSTER, SET_SETPOINT_CMD, "00", "01", tempHex, "FF")

    /*zigbeeWriteInfo = zigbee.writeAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE, DataType.ENUM8, 0x04) +
                            zigbee.writeAttribute(THERMOSTAT_CLUSTER, HEATING_SETPOINT, DataType.INT16, hex(state.pendingSetpoint * 100)) +
                            zigbee.readAttribute(THERMOSTAT_CLUSTER, HEATING_SETPOINT) */

    log.debug "Attribute info is ${zigbeeWriteInfo}"

    sendZigbeeCmds(zigbeeWriteInfo, 500)
}

private parseCmdMessage(description)
{
    def descMap = zigbee.parseDescriptionAsMap(description)
    def result = []
    def map = [:]

    if ( descMap.isClusterSpecific )
    {
        log.debug "Processing Command: Cluster 0x${descMap.clusterId}, CmdID 0x${descMap.command}, Data ${descMap.data}"

        if (descMap.clusterInt == THERMOSTAT_CLUSTER)
        {
            if (descMap.commandInt == SET_SETPOINT_CMD)
            {
                state.commandSetpoint = getTemperature(descMap.data[3] + descMap.data[2])
                log.debug "Device Heating Setpoint Command: ${state.commandSetpoint} degrees"

                //Trigger Local UI update
                map.name = "heatingSetpoint"
                map.value = state.commandSetpoint
                map.unit = temperatureScale
            }
        }

        if (map)
        {
            result << createEvent(map)
        }
    }
    else if (descMap.command != null)
    {
      log.warn "Unhandled Clientside Command: Cluster 0x${descMap.clusterId}, CmdID 0x${descMap.command}, Data ${descMap.data}"
    }

    return result
}

private parseAttrMessage(description)
{
    def descMap = zigbee.parseDescriptionAsMap(description)
    def result = []
    List attrData = [[cluster: descMap.clusterInt, attribute: descMap.attrInt, value: descMap.value]]

    log.debug "Desc Map: $descMap"

    descMap.additionalAttrs.each {
        attrData << [cluster: descMap.clusterInt, attribute: it.attrInt, value: it.value]
    }
    attrData.findAll( {it.value != null} ).each
    {
        def map = [:]
        if (it.cluster == THERMOSTAT_CLUSTER)
        {
            if (it.attribute == LOCAL_TEMPERATURE)
            {
                log.debug "TEMP"
                map.name = "temperature"
                map.value = getTemperature(it.value)
                map.unit = temperatureScale
            }
            else if (it.attribute == HEATING_SETPOINT)
            {
                log.debug "HEATING SETPOINT"
                if (state.commandSetpoint != getTemperature(it.value))
                {
                    //mismatch between last pending zonesetpoint and received value, update thermostat
                    log.warn "setpoint mismatch, resyncing..."
                    state.pendingDispatch = true
                }
            }
            else if (it.attribute == 0xe030)
            {
                log.debug "CURRENT VALVE POSITION"
                map.name = "valvePosition"
                map.value = it.value
            }
            else if (it.attribute == VALVE_CALIBRATION_STATUS)
            {
                log.debug "VALVE_CALIBRATION_STATUS"
                state.valveCalibrationStatus = VALVE_CALIBRATION_MAP[it.value]
                map.name = "valveCalibrationStatus"
                map.value = JsonOutput.toJson(VALVE_CALIBRATION_MAP[it.value])
            }
        }
        else if (it.cluster == zigbee.POWER_CONFIGURATION_CLUSTER)
        {
            if (it.attribute == BATTERY_VOLTAGE)
            {
                map = getBatteryPercentage(Integer.parseInt(it.value, 16))
            }
        }

        if (map)
        {
            result << createEvent(map)
        }
    }

    return result
}

def installed()
{
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "0"])

    state.supportedThermostatModes = ["heat"]
    state.pendingDispatch = false
    state.commandSetpoint = 20
    state.calibrateValve = false

    //These are needed to ensure that Google Home recognises it as a thermostat which is heating and can have its thermostat set
    sendEvent(name: "heatingSetpointRange", value: heatingSetpointRange, displayed: false)
    sendEvent(name: "thermostatMode", value: "heat")

    return  zigbee.readAttribute(THERMOSTAT_CLUSTER, LOCAL_TEMPERATURE) +
            zigbee.readAttribute(THERMOSTAT_CLUSTER, HEATING_SETPOINT) +
            zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_VOLTAGE)
}

def updated()
{
    if (calibrateNow == "true")
    {
        device.updateSetting("calibrateNow", false)
        state.calibrateValve = true
    }

    log.debug "temperatureOffset: $temperatureOffset. lastTemperatureOffset: ${state.lastTemperatureOffset}"
    if(temperatureOffset.toDouble().round(1) != state.lastTemperatureOffset)
    {
        log.debug "Temperature Offset Updated"
        state.lastTemperatureOffset = temperatureOffset.toDouble().round(1)
        state.dispatchTemperatureOffset = true
    }

    log.debug "calibrationTime: $calibrationTime. calibrationDay: $calibrationDay"
    if ( calibrationTime && calibrationDay )
    {
        def scheduleTime = timeToday(calibrationTime, location.timeZone)
        schedule(scheduleTime,scheduleCalibration)
        log.debug "event scheduled: $scheduleTime"
    }
}

def scheduleCalibration()
{
    log.debug "schedule fired"
    // Time of the day is reached, check if its the right day
    def df = new java.text.SimpleDateFormat("EEEE")
    // Ensure the new date object is set to local time zone
    df.setTimeZone(location.timeZone)
    def today = df.format(new Date())
    log.debug "today is: ${today}, trigger day is ${calibrationDay}"
    //Does the preference input Days, i.e., days-of-week, contain today?
    if (calibrationDay == today)
    {
        log.debug "today is the day, calibrating valve"
        state.calibrateValve = true
    }
}

def refresh()
{
}

def ping()
{
}


def configure()
{
    def binding = zigbee.addBinding(THERMOSTAT_CLUSTER) + zigbee.addBinding(zigbee.POWER_CONFIGURATION_CLUSTER) + zigbee.addBinding(zigbee.BASIC_CLUSTER)

    def configCmds = zigbee.batteryConfig() +
        zigbee.configureReporting(THERMOSTAT_CLUSTER, LOCAL_TEMPERATURE, DataType.INT16, 30, 3600, 0x0031) +
        zigbee.configureReporting(THERMOSTAT_CLUSTER, HEATING_SETPOINT, DataType.INT16, 600, 600, 0x0031)

    log.debug "binding thermostat: binding"

    return binding + configCmds
}

def getBatteryPercentage(rawValue)
{
    def result = [:]

    result.name = "battery"

    def volts = rawValue / 10
    def minVolts = voltageRange.minVolts
    def maxVolts = voltageRange.maxVolts
    def pct = (volts - minVolts) / (maxVolts - minVolts)
    def roundedPct = Math.round(pct * 100)
    if (roundedPct < 0)
    {
        roundedPct = 0
    }
    result.value = Math.min(100, roundedPct)

    return result
}

def getVoltageRange()
{
    if(batteryType == null || batteryType == "Alkaline")
    {
        [minVolts: 2.0, maxVolts: 3.2]
    }
    else if (batteryType == "NiMH")
    {
        [minVolts: 2.1, maxVolts: 2.8]
    }
}

def getTemperature(value)
{
    if (value != null) {
        def celsius = Integer.parseInt(value, 16) / 100
        if (temperatureScale == "C")
        {
            return celsius.toDouble().round(1)
        }
        else
        {
            return Math.round(celsiusToFahrenheit(celsius))
        }
    }
}

private setSetpoint(degrees, setpointAttr, degreesMin, degreesMax)
{
    if (degrees != null && setpointAttr != null && degreesMin != null && degreesMax != null)
    {
        def normalized = Math.min(degreesMax as Double, Math.max(degrees as Double, degreesMin as Double))
        def celsius = (temperatureScale == "C") ? normalized : fahrenheitToCelsius(normalized)
        celsius = (celsius as Double).round(2)

        log.debug "SAVING HEATING SETPOINT"
        state.commandSetpoint = celsius
         //Need to update the device object setpoint here to allow the UI to update
         sendEvent(name: "heatingSetpoint", value: degrees)

        //Actual device will update on next wakeup/report
        state.pendingDispatch = true
    }
}

def setHeatingSetpoint(degrees)
{
    setSetpoint(degrees, HEATING_SETPOINT, heatingSetpointRange[0], heatingSetpointRange[1])
}

def calibrateValve()
{
    state.calibrateValve = true
}

private hex(value)
{
    return new BigInteger(Math.round(value).toString()).toString(16)
}

private hexToInt(value)
{
    new BigInteger(value, 16)
}

private boolean isVACT()
{
    device.getDataValue("model") == "EH-ZB-VACT"
}

// TODO: Get these from the thermostat; for now they are set to match the UI metadata
def getHeatingSetpointRange()
{
    (getTemperatureScale() == "C") ? [4, 35] : [39, 95]
}

def sendZigbeeCmds(cmds, delay = 2000)
{
    // remove zigbee library added "delay 2000" after each command
    // the new sendHubCommand won't honor these, instead it'll take the delay as argument
    cmds.removeAll { it.startsWith("delay") }
    // convert each command into a HubAction
    cmds = cmds.collect { new physicalgraph.device.HubAction(it) }
    def info = sendHubCommand(cmds, delay)
}

private getTHERMOSTAT_CLUSTER() { 0x0201 }
private getLOCAL_TEMPERATURE() { 0x0000 }
private getLOCAL_TEMPERATURE_CALIBRATION() {0x0010}
private getHEATING_SETPOINT() { 0x0012 }
private getMIN_HEAT_SETPOINT_LIMIT() { 0x0015 }
private getMAX_HEAT_SETPOINT_LIMIT() { 0x0016 }

private getSETPOINT_RAISE_LOWER_CMD() { 0x00 }
private getSET_SETPOINT_CMD() {0xE0 }
private getCALIBRATE_VALVE_CMD() {0xE2 }

private getBASIC_CLUSTER() { 0x0000 }
private getPOWER_CONFIGURATION_CLUSTER() { 0x0001 }
private getBATTERY_VOLTAGE() { 0x0020 }
private getBATTERY_PERCENTAGE_REMAINING() { 0x0021 }
private getBATTERY_ALARM_STATE() { 0x003E }

private getVALVE_CALIBRATION_STATUS()	 {0xe031}

private getVALVE_CALIBRATION_GOING()     { 0x00 }
private getVALVE_CALIBRATION_SUCCESS()   { 0x01 }
private getVALVE_NOT_CALIBRATED() 		 { 0x02 }
private getVALVE_CALIBRATION_FAILED_E1() { 0x03 }
private getVALVE_CALIBRATION_FAILED_E2() { 0x04 }
private getVALVE_CALIBRATION_FAILED_E3() { 0x05 }

private getVALVE_CALIBRATION_MAP() {
    [
        "00":"Calibrating...",
        "01":"Calibrated",
        "02":"Requires Calibration",
        "03":"Calibration Error (E1)",
        "04":"Calibration Error (E2)",
        "05":"Calibration Error (E3)"
    ]
}
