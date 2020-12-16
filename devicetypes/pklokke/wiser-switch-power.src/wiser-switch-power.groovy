/**
 *	
 * Wiser Smartplug/L-Relay Device Handler
 *
 * Copyright 2020 P. Klokke
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 */

metadata {
    definition (name: "Wiser Switch Power", namespace: "pklokke", author: "pklokke", ocfDeviceType: "oic.d.waterheater", genericHandler: "Zigbee")
    {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Power Meter"
        capability "Sensor"
        capability "Switch"
        capability "Health Check"
        capability "Light"

        //Schneider Electric Wiser Smart
        fingerprint profileId: "0104", deviceId: "0009", manufacturer: "Schneider Electric", model: "EH-ZB-SPD-V2", deviceJoinName: "Wiser Smartplug", ocfDeviceType: "oic.d.smartplug" //Only works on channels 11,15,20,25
        fingerprint profileId: "0104", deviceId: "0009", manufacturer: "Schneider Electric", model: "EH-ZB-LMACT", deviceJoinName: "Wiser L-Relay"
    }

    tiles(scale: 2)
    {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true)
        {
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL")
            {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute ("power", key: "SECONDARY_CONTROL")
            {
                attributeState "power", label:'${currentValue} W'
            }
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
        {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main "switch"
        details(["switch", "refresh"])
    }
}

// Parse incoming device messages to generate events
def parse(String description)
{
    log.debug "description is $description"
    def event = zigbee.getEvent(description)
    if (event)
    {
        sendEvent(event)
    }
    else
    {
        log.warn "DID NOT PARSE MESSAGE for description : $description"
        log.debug zigbee.parseDescriptionAsMap(description)
    }
}

def off()
{
    zigbee.off()
}

def on()
{
    zigbee.on()
}

def refresh()
{
    return zigbee.onOffRefresh() + zigbee.simpleMeteringPowerRefresh()
}

def configure()
{
    log.debug "in configure()"
    def additionalCmds = zigbee.onOffConfig(0, 300) + zigbee.simpleMeteringPowerConfig()

    if (device.getDataValue("model") == "EH-ZB-SPD-V2") {
        //Set this to make the commissioning complete and persist on smartplug
        additionalCmds += zigbee.writeAttribute(zigbee.BASIC_CLUSTER, 0xe050, 0x10, 0x01)
    }
    return additionalCmds + configureHealthCheck()
}

def configureHealthCheck()
{
    Integer hcIntervalMinutes = 12
    sendEvent(name: "checkInterval", value: hcIntervalMinutes * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    return refresh()
}

def updated()
{
    log.debug "in updated()"
    // updated() doesn't have it's return value processed as hub commands, so we have to send them explicitly
    def cmds = configureHealthCheck()
    cmds.each{ sendHubCommand(new physicalgraph.device.HubAction(it)) }
}

def ping()
{
    return zigbee.onOffRefresh()
}

def getONOFF_ATTRIBUTE()                {0x0000}

def getMETERING_INSTANT_DEMAND()        {0x0400}
def getMETERING_MULTIPLIER_ATTRIBUTE()  {0x5000}
def getMETERING_DIVISOR_ATTRIBUTE()     {0x5001}