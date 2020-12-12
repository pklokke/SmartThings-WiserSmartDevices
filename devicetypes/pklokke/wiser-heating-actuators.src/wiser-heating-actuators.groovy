/**
 *  Wiser Thermostat Actuator Handler
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
	definition (name: "Wiser Heating Actuators", namespace: "pklokke", author: "pklokke", mnmn: "SmartThings", vid: "generic-thermostat-1", genericHandler: "Zigbee") {
		capability "Actuator"
		capability "Temperature Measurement"
		capability "Thermostat"
		capability "Thermostat Mode"
		capability "Thermostat Cooling Setpoint"
		capability "Thermostat Heating Setpoint"
		capability "Thermostat Operating State"
		capability "Configuration"
		capability "Battery"
		capability "Power Source"
		capability "Health Check"
		capability "Refresh"
		capability "Sensor"
        
        command "setActualTemperature", ["number"]

        fingerprint profileId: "0104", inClusters: "0000,0001,0003,0009,0201,0702,FE02", outClusters: "0019, 0402", manufacturer: "Schneider Electric", model: "EH-ZB-HACT", deviceJoinName: "Wiser H-Relay"
	}
    
tiles {
		multiAttributeTile(name:"thermostatMulti", type:"thermostat", width:3, height:2, canChangeIcon: true) {
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
			tileAttribute("device.thermostatOperatingState", key: "OPERATING_STATE") {
				attributeState("idle", backgroundColor: "#cccccc")
				attributeState("heating", backgroundColor: "#E86D13")
				attributeState("cooling", backgroundColor: "#00A0DC")
			}
			tileAttribute("device.thermostatMode", key: "THERMOSTAT_MODE") {
				attributeState("off", action: "setThermostatMode", label: "Off", icon: "st.thermostat.heating-cooling-off")
				attributeState("cool", action: "setThermostatMode", label: "Cool", icon: "st.thermostat.cool")
				attributeState("heat", action: "setThermostatMode", label: "Heat", icon: "st.thermostat.heat")
				attributeState("auto", action: "setThermostatMode", label: "Auto", icon: "st.tesla.tesla-hvac")
				attributeState("emergency heat", action:"setThermostatMode", label: "Emergency heat", icon: "st.thermostat.emergency-heat")
			}
			tileAttribute("device.heatingSetpoint", key: "HEATING_SETPOINT") {
				attributeState("default", label: '${currentValue}', unit: "°", defaultState: true)
			}
			tileAttribute("device.coolingSetpoint", key: "COOLING_SETPOINT") {
				attributeState("default", label: '${currentValue}', unit: "°", defaultState: true)
			}
		}
		controlTile("thermostatMode", "device.thermostatMode", "enum", width: 2 , height: 2, supportedStates: "device.supportedThermostatModes") {
			state("off", action: "setThermostatMode", label: 'Off', icon: "st.thermostat.heating-cooling-off")
			state("cool", action: "setThermostatMode", label: 'Cool', icon: "st.thermostat.cool")
			state("heat", action: "setThermostatMode", label: 'Heat', icon: "st.thermostat.heat")
			state("auto", action: "setThermostatMode", label: 'Auto', icon: "st.tesla.tesla-hvac")
			state("emergency heat", action:"setThermostatMode", label: 'Emergency heat', icon: "st.thermostat.emergency-heat")
		}
		controlTile("heatingSetpoint", "device.heatingSetpoint", "slider",
				sliderType: "HEATING",
				debouncePeriod: 1500,
				range: "device.heatingSetpointRange",
				width: 2, height: 2) {
					state "default", action:"setHeatingSetpoint", label:'${currentValue}', backgroundColor: "#E86D13"
				}
		controlTile("coolingSetpoint", "device.coolingSetpoint", "slider",
				sliderType: "COOLING",
				debouncePeriod: 1500,
				range: "device.coolingSetpointRange",
				width: 2, height: 2) {
					state "default", action:"setCoolingSetpoint", label:'${currentValue}', backgroundColor: "#00A0DC"
				}
		controlTile("thermostatFanMode", "device.thermostatFanMode", "enum", width: 2 , height: 2, supportedStates: "device.supportedThermostatFanModes") {
			state "auto", action: "setThermostatFanMode", label: 'Auto', icon: "st.thermostat.fan-auto"
			state "on",	action: "setThermostatFanMode", label: 'On', icon: "st.thermostat.fan-on"
		}
		standardTile("refresh", "device.thermostatMode", width: 2, height: 1, inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		valueTile("powerSource", "device.powerSource", width: 2, heigh: 1, inactiveLabel: true, decoration: "flat") {
			state "powerSource", label: 'Power Source: ${currentValue}', backgroundColor: "#ffffff"
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:""
		}
		main "thermostatMulti"
		details(["thermostatMulti", "thermostatMode", "heatingSetpoint", "coolingSetpoint", "thermostatFanMode", "battery", "powerSource", "refresh"])
	}
}

def parse(String description) {
	def map = zigbee.getEvent(description)
    def descMap = zigbee.parseDescriptionAsMap(description)
	def result


	if (!map) {
		result = parseAttrMessage(description)
        result += parseCmdMessage(description)
	} else {
		log.warn "Unexpected event: ${map}"
	}

	log.debug "Description ${description} parsed to ${result}"

	log.debug "Device Awake, checking for messages"
    if ( state.pendingDispatch )
    {
		runIn(1, "updateSetpoints", [overwrite: true])
    }

	return result
}

def updateSetpoints() {

    def zigbeeWriteInfo

    log.debug "SENDING HEATING SETPOINT"

    log.debug "Command Setpoint is ${state.commandSetpoint}"

    def tempHex = String.format("%02X%02X",((int)(state.commandSetpoint * 100))%256,(int)((state.commandSetpoint * 100)/256))
    
    state.pendingDispatch = false

    zigbeeWriteInfo = zigbee.command(THERMOSTAT_CLUSTER, SET_SETPOINT_CMD, "00", "01", tempHex, "FF")

    /*zigbeeWriteInfo = zigbee.writeAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE, DataType.ENUM8, 0x04) +
        					zigbee.writeAttribute(THERMOSTAT_CLUSTER, HEATING_SETPOINT, DataType.INT16, hex(state.pendingSetpoint * 100)) + 
        					zigbee.readAttribute(THERMOSTAT_CLUSTER, HEATING_SETPOINT) */

    log.debug "Attribute info is ${zigbeeWriteInfo}" 

	sendZigbeeCmds(zigbeeWriteInfo, 500)    
}

private parseCmdMessage(description) {
	def descMap = zigbee.parseDescriptionAsMap(description)
	def result = []
    def map = [:]
    
	if ( descMap.isClusterSpecific ) {
    	log.debug "Processing Command: Cluster 0x${descMap.clusterId}, CmdID 0x${descMap.command}, Data ${descMap.data}"
        
        if (descMap.clusterInt == THERMOSTAT_CLUSTER) {
        	if (descMap.commandInt == SET_SETPOINT_CMD) {
                state.commandSetpoint = getTemperature(descMap.data[3] + descMap.data[2])
                log.debug "Device Heating Setpoint Command: ${state.commandSetpoint} degrees"
                
                //Trigger Local UI update
				map.name = "heatingSetpoint"
				map.value = state.commandSetpoint
				map.unit = temperatureScale
            }
        } 
        
		if (map) {
			result << createEvent(map)
		}
    } else if (descMap.command != null) {
      log.warn "Unhandled Clientside Command: Cluster 0x${descMap.clusterId}, CmdID 0x${descMap.command}, Data ${descMap.data}"
    }

    return result
}

private parseAttrMessage(description) {
	def descMap = zigbee.parseDescriptionAsMap(description)
	def result = []
	List attrData = [[cluster: descMap.clusterInt, attribute: descMap.attrInt, value: descMap.value]]

	log.debug "Desc Map: $descMap"

	descMap.additionalAttrs.each {
		attrData << [cluster: descMap.clusterInt, attribute: it.attrInt, value: it.value]
	}
	attrData.findAll( {it.value != null} ).each {
		def map = [:]
		if (it.cluster == THERMOSTAT_CLUSTER) {
			if (it.attribute == LOCAL_TEMPERATURE) {
				log.debug "TEMP"
				map.name = "temperature"
				map.value = getTemperature(it.value)
				map.unit = temperatureScale
			} else if (it.attribute == COOLING_SETPOINT) {
				log.debug "COOLING SETPOINT"
				map.name = "coolingSetpoint"
				map.value = getTemperature(it.value)
				map.unit = temperatureScale
			} else if (it.attribute == HEATING_SETPOINT) {
				log.debug "HEATING SETPOINT"
                //non-sleepy devices can have other clients update them, thus accept the value
                state.commandSetpoint = getTemperature(it.value)
				map.name = "heatingSetpoint"
				map.value = state.commandSetpoint
				map.unit = temperatureScale
			} else if (it.attribute == THERMOSTAT_MODE || it.attribute == THERMOSTAT_RUNNING_MODE) {
				log.debug "MODE"
				map.name = "thermostatMode"
				map.value = THERMOSTAT_MODE_MAP[it.value]
				map.data = [supportedThermostatModes: state.supportedThermostatModes]
			} else if (it.attribute == THERMOSTAT_RUNNING_STATE) {
				log.debug "RUNNING STATE"
				def intValue = hexToInt(it.value) as int
				/**
				 * Zigbee Cluster Library spec 6.3.2.2.3.7
				 * Bit	Description
				 *  0	Heat State
				 *  1	Cool State
				 *  2	Fan State
				 *  3	Heat 2nd Stage State
				 *  4	Cool 2nd Stage State
				 *  5	Fan 2nd Stage State
				 *  6	Fan 3rd Stage Stage
				 **/
				map.name = "thermostatOperatingState"
				if (intValue & 0x01) {
					map.value = "heating"
				} else if (intValue & 0x02) {
					map.value = "cooling"
				} else if (intValue & 0x04) {
					map.value = "fan only"
				} else {
					map.value = "idle"
				}
			} else if (it.attribute == CONTROL_SEQUENCE_OF_OPERATION) {
				log.debug "CONTROL SEQUENCE OF OPERATION"
				state.supportedThermostatModes = CONTROL_SEQUENCE_OF_OPERATION_MAP[it.value]
				map.name = "supportedThermostatModes"
				map.value = JsonOutput.toJson(CONTROL_SEQUENCE_OF_OPERATION_MAP[it.value])
			} else if (it.attribute == 0xe030) {
				log.debug "CURRENT VALVE POSITION"
				map.name = "valvePosition"
				map.value = it.value
			}
			// Thermostat System Config is an optional attribute, but is supported by the LUX KONOz and is more informative.
			else if (it.attribute == THERMOSTAT_SYSTEM_CONFIG) {
				log.debug "THERMOSTAT SYSTEM CONFIG"
				def intValue = hexToInt(it.value) as int
				/**
				 *
				 * Table 6-12. HVAC System Type Configuration Values
				 * Bit Number	Description
				 * 	0 – 1		Cooling System Stage
				 * 					00 – Cool Stage 1
				 * 					01 – Cool Stage 2
				 * 					10 – Cool Stage 3
				 * 					11 – Reserved
				 * 	2 – 3		Heating System Stage
				 * 					00 – Heat Stage 1
				 * 					01 – Heat Stage 2
				 * 					10 – Heat Stage 3
				 * 					11 – Reserved
				 * 	4			Heating System Type
				 * 					0 – Conventional
				 * 					1 – Heat Pump
				 * 	5			Heating Fuel Source
				 * 					0 – Electric / B
				 * 					1 – Gas / O
				 */
				def cooling = 		 intValue & 0b00000011
				def heating = 		(intValue & 0b00001100) >>> 2
				def heatingType = 	(intValue & 0b00010000) >>> 4
				def supportedModes = ["off"]

				if (cooling != 0x03) {
					supportedModes << "cool"
				}
				if (heating != 0x03) {
					supportedModes << "heat"
				}
				if ((heating == 0x01 || heating == 0x02) && heatingType == 1) {
					supportedModes << "emergency heat"
				}
				log.debug "supported modes: $supportedModes"
				state.supportedThermostatModes = supportedModes
				map.name = "supportedThermostatModes"
				map.value = JsonOutput.toJson(supportedModes)
			}
		} else if (it.cluster == zigbee.POWER_CONFIGURATION_CLUSTER) {
			if (it.attribute == BATTERY_VOLTAGE) {
				map = getBatteryPercentage(Integer.parseInt(it.value, 16))
			} else if (it.attribute == BATTERY_ALARM_STATE) {
				map = getPowerSource(it.value)
			}
		}

		if (map) {
			result << createEvent(map)
		}
	}

	return result
}

def installed() {
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])

	state.supportedThermostatModes = ["heat"]
    state.pendingDispatch = false
    state.commandSetpoint = 20
	sendEvent(name: "supportedThermostatModes", value: JsonOutput.toJson(state.supportedThermostatModes), displayed: false)
	sendEvent(name: "heatingSetpointRange", value: heatingSetpointRange, displayed: false)
    sendEvent(name: "thermostatMode", value: "heat") 
}

def refresh() {
	if(isVACT()) {
    	sendEvent(name: "powerSource", value: "battery")
    }
    else {
    	sendEvent(name: "powerSource", value: "mains")
    }
    
	// THERMOSTAT_SYSTEM_CONFIG is an optional attribute. It we add other thermostats we need to determine if they support this and behave accordingly.
	return zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_SYSTEM_CONFIG) +
			zigbee.readAttribute(FAN_CONTROL_CLUSTER, FAN_MODE_SEQUENCE) +
			zigbee.readAttribute(THERMOSTAT_CLUSTER, LOCAL_TEMPERATURE) +
			zigbee.readAttribute(THERMOSTAT_CLUSTER, COOLING_SETPOINT) +
			zigbee.readAttribute(THERMOSTAT_CLUSTER, HEATING_SETPOINT) +
			zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE) +
			zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_RUNNING_STATE) +
			zigbee.readAttribute(FAN_CONTROL_CLUSTER, FAN_MODE) +
			zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_ALARM_STATE) +
			getBatteryRemainingCommand()
}

def getBatteryRemainingCommand() {
	zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_VOLTAGE)
}

def ping() {
	refresh()
}


def configure() {
	def binding = zigbee.addBinding(THERMOSTAT_CLUSTER)

    def configCmds = zigbee.configureReporting(THERMOSTAT_CLUSTER, HEATING_SETPOINT, DataType.INT16, 30, 30, 0x0064)

    // setting Min/Max HeatSetPointLimits for Thermostats - MinHeatSetpointLimit: 500 (0x01F4), MaxHeatSetpointLimit: 3000 (0x0BB8)
    def startValues = zigbee.writeAttribute(THERMOSTAT_CLUSTER, HEATING_SETPOINT, DataType.INT16, 0x07D0) +
    		zigbee.writeAttribute(THERMOSTAT_CLUSTER, MIN_HEAT_SETPOINT_LIMIT, DataType.INT16, 0x01F4) +
			zigbee.writeAttribute(THERMOSTAT_CLUSTER, MAX_HEAT_SETPOINT_LIMIT, DataType.INT16, 0x0BB8)
            
    log.debug "binding thermostat: binding"

	return binding + configCmds + startValues + zigbee.batteryConfig() + refresh()
}

def getBatteryPercentage(rawValue) {
	def result = [:]

	result.name = "battery"

	if (rawValue == 0) {
		sendEvent(name: "powerSource", value: "mains", descriptionText: "${device.displayName} is connected to mains")
		result.value = 100
		result.descriptionText = "${device.displayName} is powered by external source."
	} else {
		def volts = rawValue / 10
		def minVolts = voltageRange.minVolts
		def maxVolts = voltageRange.maxVolts
		def pct = (volts - minVolts) / (maxVolts - minVolts)
		def roundedPct = Math.round(pct * 100)
		if (roundedPct < 0) {
			roundedPct = 0
		}
		result.value = Math.min(100, roundedPct)
	}

	return result
}

def getVoltageRange() {
		[minVolts: 2.0, maxVolts: 3.2]
}

def setActualTemperature(value) {
	sendEvent(name: "temperature", value: value, unit: "C")
}

def getTemperature(value) {
	if (value != null) {
		def celsius = Integer.parseInt(value, 16) / 100
		if (temperatureScale == "C") {
			return Math.round(celsius)
		} else {
			return Math.round(celsiusToFahrenheit(celsius))
		}
	}
}

def getPowerSource(value) {
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

def setThermostatMode(mode) {
	log.debug "set mode $mode (supported ${state.supportedThermostatModes})"
	if (state.supportedThermostatModes?.contains(mode)) {
		switch (mode) {
			case "heat":
				heat()
				break
			case "cool":
				cool()
				break
			case "auto":
				auto()
				break
			case "emergency heat":
				emergencyHeat()
				break
			case "off":
				off()
				break
		}
	} else {
		log.debug "Unsupported mode $mode"
	}
}

def setThermostatFanMode(mode) {
	if (state.supportedFanModes?.contains(mode)) {
		switch (mode) {
			case "on":
				fanOn()
				break
			case "auto":
				fanAuto()
				break
		}
	} else {
		log.debug "Unsupported fan mode $mode"
	}
}

def off() {
	return zigbee.writeAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE, DataType.ENUM8, THERMOSTAT_MODE_OFF) +
			zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE)
}

def auto() {
	return zigbee.writeAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE, DataType.ENUM8, THERMOSTAT_MODE_AUTO) +
			zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE)
}

def cool() {
	return zigbee.writeAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE, DataType.ENUM8, THERMOSTAT_MODE_COOL) +
			zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE)
}

def heat() {
	return zigbee.writeAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE, DataType.ENUM8, THERMOSTAT_MODE_HEAT) +
			zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE)
}

def emergencyHeat() {
	return zigbee.writeAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE, DataType.ENUM8, THERMOSTAT_MODE_EMERGENCY_HEAT) +
			zigbee.readAttribute(THERMOSTAT_CLUSTER, THERMOSTAT_MODE)
}

def fanAuto() {
	return zigbee.writeAttribute(FAN_CONTROL_CLUSTER, FAN_MODE, DataType.ENUM8, FAN_MODE_AUTO) +
			zigbee.readAttribute(FAN_CONTROL_CLUSTER, FAN_MODE)
}

def fanOn() {
	return zigbee.writeAttribute(FAN_CONTROL_CLUSTER, FAN_MODE, DataType.ENUM8, FAN_MODE_ON) +
			zigbee.readAttribute(FAN_CONTROL_CLUSTER, FAN_MODE)
}

private setSetpoint(degrees, setpointAttr, degreesMin, degreesMax) {
	if (degrees != null && setpointAttr != null && degreesMin != null && degreesMax != null) {
		def normalized = Math.min(degreesMax as Double, Math.max(degrees as Double, degreesMin as Double))
		def celsius = (temperatureScale == "C") ? normalized : fahrenheitToCelsius(normalized)
		celsius = (celsius as Double).round(2)

		log.debug "SAVING HEATING SETPOINT"
    	state.commandSetpoint = celsius
 		//Need to update the device object setpoint here to allow the UI to update
 		sendEvent(name: "heatingSetpoint", value: degrees)
        state.pendingDispatch = true
		
        if (isVACT()) {
            //Actual device will update on next wakeup/report
        }
        else {
        	updateSetpoints()
        }
	}
}

def setCoolingSetpoint(degrees) {
	setSetpoint(degrees, COOLING_SETPOINT, coolingSetpointRange[0], coolingSetpointRange[1])
}

def setHeatingSetpoint(degrees) {
	setSetpoint(degrees, HEATING_SETPOINT, heatingSetpointRange[0], heatingSetpointRange[1])
}

private hex(value) {
	return new BigInteger(Math.round(value).toString()).toString(16)
}

private hexToInt(value) {
	new BigInteger(value, 16)
}

private boolean isVACT() {
	device.getDataValue("model") == "EH-ZB-VACT"
}

// TODO: Get these from the thermostat; for now they are set to match the UI metadata
def getCoolingSetpointRange() {
	(getTemperatureScale() == "C") ? [0, 0] : [0, 0]
}
def getHeatingSetpointRange() {
	(getTemperatureScale() == "C") ? [4, 35] : [39, 95]
}

def sendZigbeeCmds(cmds, delay = 2000) {
    // remove zigbee library added "delay 2000" after each command
    // the new sendHubCommand won't honor these, instead it'll take the delay as argument
    cmds.removeAll { it.startsWith("delay") }
    // convert each command into a HubAction
    cmds = cmds.collect { new physicalgraph.device.HubAction(it) }
    def info = sendHubCommand(cmds, delay)
}

private getTHERMOSTAT_CLUSTER() { 0x0201 }
private getLOCAL_TEMPERATURE() { 0x0000 }
private getTHERMOSTAT_SYSTEM_CONFIG() { 0x0009 } // Optional attribute
private getCOOLING_SETPOINT() { 0x0011 }
private getHEATING_SETPOINT() { 0x0012 }
private getMIN_HEAT_SETPOINT_LIMIT() { 0x0015 }
private getMAX_HEAT_SETPOINT_LIMIT() { 0x0016 }
private getTHERMOSTAT_RUNNING_MODE() { 0x001E }
private getCONTROL_SEQUENCE_OF_OPERATION() { 0x001B } // Mandatory attribute
private getCONTROL_SEQUENCE_OF_OPERATION_MAP() {
	[
		"00":["off", "cool"],
		"01":["off", "cool"],
		// 0x02, 0x03, 0x04, and 0x05 don't actually guarentee emergency heat; to learn this, one would
		// try THERMOSTAT_SYSTEM_CONFIG (optional), which we default to for the LUX KONOz since it supports THERMOSTAT_SYSTEM_CONFIG
		"02":["off", "heat", "emergency heat"],
		"03":["off", "heat", "emergency heat"],
		"04":["off", "heat", "auto", "cool", "emergency heat"],
		"05":["off", "heat", "auto", "cool", "emergency heat"]
	]
}
private getTHERMOSTAT_MODE() { 0x001C }
private getTHERMOSTAT_MODE_OFF() { 0x00 }
private getTHERMOSTAT_MODE_AUTO() { 0x01 }
private getTHERMOSTAT_MODE_COOL() { 0x03 }
private getTHERMOSTAT_MODE_HEAT() { 0x04 }
private getTHERMOSTAT_MODE_EMERGENCY_HEAT() { 0x05 }
private getTHERMOSTAT_MODE_MAP() {
	[
		"00":"off",
		"01":"auto",
		"03":"cool",
		"04":"heat",
		"05":"emergency heat"
	]
}
private getTHERMOSTAT_RUNNING_STATE() { 0x0029 }
private getSETPOINT_RAISE_LOWER_CMD() { 0x00 }
private getSET_SETPOINT_CMD() {0xE0 }

private getFAN_CONTROL_CLUSTER() { 0x0202 }
private getFAN_MODE() { 0x0000 }
private getFAN_MODE_SEQUENCE() { 0x0001 }
private getFAN_MODE_SEQUENCE_MAP() {
	[
		"00":["low", "medium", "high"],
		"01":["low", "high"],
		"02":["low", "medium", "high", "auto"],
		"03":["low", "high", "auto"],
		"04":["on", "auto"],
	]
}
private getFAN_MODE_ON() { 0x04 }
private getFAN_MODE_AUTO() { 0x05 }
private getFAN_MODE_MAP() {
	[
		"04":"on",
		"05":"auto"
	]
}

private getPOWER_CONFIGURATION_CLUSTER() { 0x0001 }
private getBATTERY_VOLTAGE() { 0x0020 }
private getBATTERY_PERCENTAGE_REMAINING() { 0x0021 }
private getBATTERY_ALARM_STATE() { 0x003E }