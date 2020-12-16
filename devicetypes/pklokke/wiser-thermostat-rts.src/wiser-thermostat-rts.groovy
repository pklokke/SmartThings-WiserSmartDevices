/*
 *  Wiser Thermostat RTS Device Handler
 *
 *  Copyright 2020 P. Klokke
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy
 *  of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */
import physicalgraph.zigbee.zcl.DataType

metadata
{
    definition(name: "Wiser Thermostat RTS", namespace: "pklokke", author: "pklokke", mnmn: "SmartThings", vid: "generic-temperature-measurement", genericHandler: "Zigbee")
    {
        capability "Configuration"
        capability "Battery"
        capability "Refresh"
        capability "Temperature Measurement"
        capability "Health Check"
        capability "Sensor"
        
        command "bind", ["string", "string"]
        command "unbind", ["string", "string"]

        fingerprint profileId: "0104", deviceId: "0104", manufacturer: "Schneider Electric", model: "EH-ZB-RTS", deviceJoinName: "Thermostat RTS"
    }

    tiles(scale: 2)
    {
        multiAttributeTile(name: "temperature", type: "generic", width: 6, height: 4)
        {
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
                attributeState("temperature", label:'${currentValue}°')
            }
        }
        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
        {
            state "battery", label: 'Battery: ${currentValue}%', unit: ""
        }

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
        {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main "temperature"
        details(["temperature", "battery", "refresh"])
    }
}

private List<Map> collectAttributes(Map descMap)
{
    List<Map> descMaps = new ArrayList<Map>()

    descMaps.add(descMap)

    if (descMap.additionalAttrs) {
        descMaps.addAll(descMap.additionalAttrs)
    }

    return  descMaps
}

def parse(String description)
{
    log.debug "description: $description"
    
    if(state.bindQueue != [])
    {
        log.debug "Bind Queue: ${state.bindQueue}"
        sendZigbeeCmds(state.bindQueue)
        state.bindQueue = []
    }

    // getEvent will handle temperature
    Map map = zigbee.getEvent(description)
    if (!map)
    {
        Map descMap = zigbee.parseDescriptionAsMap(description)

        if (descMap?.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.commandInt != 0x07 && descMap.value)
        {
            List<Map> descMaps = collectAttributes(descMap)

            def battMap = descMaps.find { it.attrInt == BATTERY_VOLTAGE_ATTR }
            if (battMap)
            {
                map = getBatteryResult(Integer.parseInt(battMap.value, 16))
            }
        }
        else if (descMap?.clusterInt == zigbee.TEMPERATURE_MEASUREMENT_CLUSTER && descMap.commandInt == 0x07)
        {
            if (descMap.data[0] == "00")
            {
                log.debug "TEMP REPORTING CONFIG RESPONSE: $descMap"
                sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
            }
            else
            {
                log.warn "TEMP REPORTING CONFIG FAILED- error code: ${descMap.data[0]}"
            }
        }
    }
    else if (map.name == "temperature")
    {
        map.value = new BigDecimal(map.value as float).setScale(1, BigDecimal.ROUND_HALF_UP)
        map.descriptionText = temperatureScale == 'C' ? '{{ device.displayName }} was {{ value }}°C' : '{{ device.displayName }} was {{ value }}°F'
        map.translatable = true
    }

    log.debug "Parse returned $map"
    def result = map ? createEvent(map) : [:]

    return result
}

private Map getBatteryResult(rawValue)
{
    log.debug "Battery rawValue = ${rawValue}"
    def linkText = getLinkText(device)

    def result = [:]

    def volts = rawValue / 10

    if (!(rawValue == 0 || rawValue == 255)) {
        result.name = 'battery'
        result.translatable = true
        result.descriptionText = "{{ device.displayName }} battery was {{ value }}%"
        def minVolts = 2.7
        def maxVolts = 4.5
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0)
        roundedPct = 1
        result.value = Math.min(100, roundedPct)
    }

    return result
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping()
{
}

def refresh()
{
}

def installed()
{
    log.debug "installed"
    state.bindQueue = []

    sendZigbeeCmds(zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_VOLTAGE_ATTR))
}

def configure()
{
    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 5 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])

    def bindingCmds = zigbee.addBinding(zigbee.POWER_CONFIGURATION_CLUSTER) + zigbee.addBinding(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER) + zigbee.addBinding(zigbee.BASIC_CLUSTER)

    log.debug "Configuring Reporting"
    def configCmds = []

    configCmds += zigbee.batteryConfig() + zigbee.temperatureConfig(30, 600, 10)// min 30 seconds, max 10 minutes, reportable change 0.1degC

    return configCmds + refresh() + bindingCmds // send refresh cmds as part of config
}

def unbind(tartgetZigbeeId,targetEndpointId)
{
    //zdo unbind unicast [target:2] [source eui64:8] [source endpoint:1] [clusterID:2] [destinationEUI64:8] [destEndpoint:1]
    state.bindQueue << "zdo unbind unicast 0x${device.deviceNetworkId} {${device.zigbeeId}} 0x${device.endpointId} 0x0201 {${tartgetZigbeeId}} 0x${targetEndpointId}"
    state.bindQueue << "zdo unbind unicast 0x${device.deviceNetworkId} {${device.zigbeeId}} 0x${device.endpointId} 0x0402 {${tartgetZigbeeId}} 0x${targetEndpointId}"
    log.debug "New Queue: ${state.bindQueue}"
}

def bind(tartgetZigbeeId,targetEndpointId)
{
    //zdo bind [destination:2] [source Endpoint:1] [destEndpoint:1] [cluster:2] [remoteEUI64:8] [destEUI64:8]
    state.bindQueue << "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x${targetEndpointId} 0x0201 {${device.zigbeeId}} {${tartgetZigbeeId}}"
    state.bindQueue << "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x${targetEndpointId} 0x0402 {${device.zigbeeId}} {${tartgetZigbeeId}}"
    log.debug "New Queue: ${state.bindQueue}"
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

def getBATTERY_VOLTAGE_ATTR() { 0x0020 }
