/**
 * Wiser Thermostat Bind Manager
 *
 * Copyright 2020 P. Klokke
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Virtual Thermostat
 *
 *  Author: SmartThings
 */
definition(
    name: "Wiser Thermostat Bind Manager",
    namespace: "pklokke",
    author: "pklokke",
    description: "Bind a Wiser RTS Thermostat to an H-Relay",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch@2x.png",
    pausable: true
)

preferences {
	section("Choose a temperature sensor/RTS... "){
		input "sensor", "device.wiserThermostatRts", title: "Sensor"
	}
	section("Select the thermostat actuator... "){
		input "actuator", "device.wiserHeatingActuators", title: "Thermostat Actuator"
	}
}

def installed()
{
	state.existingSensor = [:]
    state.existingActuator = [:]
	subscribe(sensor, "temperature", temperatureReading)
    log.debug "Installed: sensor $sensor actuator: $actuator"
    state.updateBind = true
    sensor.bind(actuator.device.zigbeeId,actuator.device.endpointId)
}

def updated()
{
    log.debug "Updated: sensor $sensor actuator: $actuator"
	state.updateBind = true
    sensor.bind(actuator.device.zigbeeId,actuator.device.endpointId)
}

def uninstalled()
{
	sensor.unbind(actuator.device.zigbeeId,actuator.device.endpointId)
}

def temperatureReading(evt)
{
   log.debug "sensor temperature ${sensor.currentTemperature} actuator temperature: ${actuator.currentTemperature}"
   log.debug "sensor IEEE ${sensor.device.zigbeeId} actuator IEEE: ${actuator.device.zigbeeId}"
   log.debug "sensor network ID ${sensor.deviceNetworkId} actuator network ID: ${actuator.deviceNetworkId}"
   log.debug "sensor network EP ${sensor.device.endpointId} actuator network EP: ${actuator.device.endpointId}"
   log.debug "sensor model ${sensor.getModelName()} actuator model: ${actuator.getModelName()}"
   
   actuator.setActualTemperature(sensor.currentTemperature)
  
   if( state.updateBind )
   {
   		if ( state.existingSensor != [:] )
    	{
        
            evt.getDevice().unbind(state.existingActuator.zigbeeId,state.existingActuator.ep)

            //subscribe to new temperature event to prepare to handle new bind
            unsubscribe()
            subscribe(sensor, "temperature", handleBind)
            state.existingSensor = [zigbeeId: sensor.device.zigbeeId, ep: sensor.device.endpointId, nwkAddr: sensor.device.deviceNetworkId]
            state.existingActuator = [zigbeeId: actuator.device.zigbeeId,ep: actuator.device.endpointId]
   		}
        state.updateBind = false
   }
   
}
