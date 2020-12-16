/**
 *  Wiser Heating Actuator Handler
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

metadata {
    definition (name: "Wiser Heating Actuators", namespace: "pklokke", author: "pklokke", mnmn: "SmartThings", vid: "generic-radiator-thermostat-2", genericHandler: "Zigbee")
    {
        capability "Actuator"
        capability "Temperature Measurement"
        capability "Thermostat"
        capability "Thermostat Heating Setpoint"
        capability "Configuration"
        capability "Battery"
        capability "Power Source"
        capability "Health Check"
        capability "Refresh"
        capability "Sensor"
        
        command "setActualTemperature", ["number"]

        fingerprint profileId: "0104", inClusters: "0000,0001,0003,0009,0201,0702,FE02", outClusters: "0019, 0402", manufacturer: "Schneider Electric", model: "EH-ZB-HACT", deviceJoinName: "Wiser H-Relay"
    }
    
   preferences
   {
        section
        {
            input ("exerciseValve", "boolean", title: "Exercise output (for valves)", required: false)
            input ("exerciseDay", "enum", title: "Exercise Day of the Week", required: false, options: ["Monday": "Monday", "Tuesday": "Tuesday", "Wednesday": "Wednesday", "Thursday": "Thursday", "Thursday": "Thursday", "Saturday": "Saturday", "Sunday": "Sunday"])
            input ("exerciseTime", "string", title: "Exercise Time of Day", required: false)
        }
    }

    tiles
    {
        multiAttributeTile(name:"thermostatMulti", type:"thermostat", width:3, height:2, canChangeIcon: true)
        {
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL")
            {
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
                width: 2, height: 2)
                {
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

    return result
}

def updateSetpoints()
{
    log.debug "Sending Command Setpoint: ${state.commandSetpoint}"
    def tempHex = String.format("%02X%02X",((int)(state.commandSetpoint * 100))%256,(int)((state.commandSetpoint * 100)/256))

    state.pendingDispatch = false

    def cmdToSend = zigbee.command(THERMOSTAT_CLUSTER, SET_SETPOINT_CMD, "00", "01", tempHex, "FF")
    log.debug "Sending command: ${cmdToSend}"
    sendZigbeeCmds(cmdToSend, 500)
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
    } else if (descMap.command != null)
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

    descMap.additionalAttrs.each
    {
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
                //non-sleepy devices can have other clients update them, thus accept the value
                state.commandSetpoint = getTemperature(it.value)
                map.name = "heatingSetpoint"
                map.value = state.commandSetpoint
                map.unit = temperatureScale
           }
        }
        else if (it.cluster == zigbee.POWER_CONFIGURATION_CLUSTER)
        {
            if (it.attribute == BATTERY_VOLTAGE)
            {
                map = getBatteryPercentage(Integer.parseInt(it.value, 16))
            }
            else if (it.attribute == BATTERY_ALARM_STATE)
            {
                map = getPowerSource(it.value)
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
    log.debug "installed"
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])

    state.supportedThermostatModes = ["heat"]
    state.pendingDispatch = false
    state.exerciseCachedSetpoint = 20

    def startValues = zigbee.readAttribute(THERMOSTAT_CLUSTER, LOCAL_TEMPERATURE) +
                        zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_ALARM_STATE)

    //These are needed to ensure that Google Home recognises it as a thermostat which is heating and can have its thermostat set
    sendEvent(name: "supportedThermostatModes", value: JsonOutput.toJson(state.supportedThermostatModes), displayed: false)
    sendEvent(name: "heatingSetpointRange", value: heatingSetpointRange, displayed: false)
    sendEvent(name: "thermostatMode", value: "heat")

    return startValues + refresh()
}

def updated()
{
    if (exerciseValve != null && exerciseValve == "true")
    {
        log.debug "exerciseDay: $exerciseDay. exerciseTime: $exerciseTime"
        if ( exerciseDay && exerciseTime )
        {
            def scheduleTime = timeToday(exerciseTime, location.timeZone)
            schedule(scheduleTime,scheduleExercise)
            log.debug "event scheduled: $scheduleTime"
        }
    }
}

def scheduleExercise()
{
    log.debug "schedule fired"
    // Time of the day is reached, check if its the right day
    def df = new java.text.SimpleDateFormat("EEEE")
    // Ensure the new date object is set to local time zone
    df.setTimeZone(location.timeZone)
    def today = df.format(new Date())
    log.debug "today is: ${today}, trigger day is ${exerciseDay}"
    if (exerciseDay == today)
    {
        log.debug "today is the day, exercising valve"
        state.exerciseCachedSetpoint = state.commandSetpoint
        log.debug "setting maximum setpoint"
        setHeatingSetpoint(heatingSetpointRange[1])
        runIn(600, "scheduleCooldown", [overwrite: true])
    }
}

def scheduleCooldown()
{
    log.debug "setting to minimum setpoint"
    setHeatingSetpoint(heatingSetpointRange[0])
    runIn(600, "scheduleRestoreSetpoint", [overwrite: true])
}

def scheduleRestoreSetpoint()
{
    log.debug "exercise done, restoring original setpoint"
    setHeatingSetpoint(state.exerciseCachedSetpoint)
}

def refresh()
{
    return zigbee.readAttribute(THERMOSTAT_CLUSTER, HEATING_SETPOINT)
}

def ping()
{
    refresh()
}

def configure()
{
    log.debug "binding and configuring ${device.displayName}"
    def binding = zigbee.addBinding(THERMOSTAT_CLUSTER)
    def configCmds = zigbee.configureReporting(THERMOSTAT_CLUSTER, HEATING_SETPOINT, DataType.INT16, 1, 300, 0x0031)

    sendEvent(name: "battery", value: 100, descriptionText: "${device.displayName} is connected to mains")
    return binding + configCmds + refresh()
}

//This allows the temperature of the bound RTS to be shown in the UI
def setActualTemperature(value)
{
    sendEvent(name: "temperature", value: value, unit: "C")
}

def getTemperature(value)
{
    if (value != null) {
        def celsius = Integer.parseInt(value, 16) / 100
        if (temperatureScale == "C") {
            return celsius.toDouble().round(1)
        } else {
            return Math.round(celsiusToFahrenheit(celsius))
        }
    }
}

def getPowerSource(value)
{
    def result = [name: "powerSource"]
    switch (value) {
        case "40000000":
            result.value = "battery"
            result.descriptionText = "${device.displayName} is powered by batteries"
            break
        default:
            result.value = "mains"
            result.descriptionText = "${device.displayName} is connected to mains"
            break
    }
    return result
}

private setSetpoint(degrees, setpointAttr, degreesMin, degreesMax)
{
    if (degrees != null && setpointAttr != null && degreesMin != null && degreesMax != null) {
        def normalized = Math.min(degreesMax as Double, Math.max(degrees as Double, degreesMin as Double))
        def celsius = (temperatureScale == "C") ? normalized : fahrenheitToCelsius(normalized)
        celsius = (celsius as Double).round(2)

        log.debug "SAVING HEATING SETPOINT"
        state.commandSetpoint = celsius
         //Need to update the device object setpoint here to allow the UI to update
         sendEvent(name: "heatingSetpoint", value: degrees)
        state.pendingDispatch = true

        updateSetpoints()
    }
}

def setHeatingSetpoint(degrees)
{
    setSetpoint(degrees, HEATING_SETPOINT, heatingSetpointRange[0], heatingSetpointRange[1])
}

private hex(value)
{
    return new BigInteger(Math.round(value).toString()).toString(16)
}

private hexToInt(value)
{
    new BigInteger(value, 16)
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
private getHEATING_SETPOINT() { 0x0012 }
private getMIN_HEAT_SETPOINT_LIMIT() { 0x0015 }
private getMAX_HEAT_SETPOINT_LIMIT() { 0x0016 }

private getSETPOINT_RAISE_LOWER_CMD() { 0x00 }
private getSET_SETPOINT_CMD() {0xE0 }

private getPOWER_CONFIGURATION_CLUSTER() { 0x0001 }
private getBATTERY_VOLTAGE() { 0x0020 }
private getBATTERY_PERCENTAGE_REMAINING() { 0x0021 }
private getBATTERY_ALARM_STATE() { 0x003E }
