# SmartThings-WiserSmartDevices
Support in SmartThings for the zigbee-ish devices in the Wiser Smart system previously sold in Europe

Note: This is not for Wiser Heat or other Wiser devices other than those sold below.  If you have Wiser devices connecting to an X shaped controller like this:

![Wiser Controller](https://download.schneider-electric.com/files?p_Doc_Ref=P138560&p_File_Type=rendition_369_jpg)

Then you have come to the right place :-)

My goal is to add basic support for the devices that connected to this controller into the SmartThings hub, as integration options for the controller are non-existent, and Schneider Electric has moved on to other systems.

This software is provided with no warranty and does not intend to support the full functionality of these devices.  Where it integrates well into the SmartThings Hub and its device model, I have tried to add it.  Pull requests for other devices and features are welcome :-)

# Devices Supported
- Wiser Thermostat (RTS) EER51000
- Wiser H-Relay (HACT) EER50000
- Wiser Smartplug V2 EER40030
- Wiser Radiator Thermostat (VACT) EER53000

# Devices Currently Unsupported
- Wiser L-Relay EER42000 (TBD)
- Wiser Boiler Controller (need to RE join process)
- Wiser S-Meter (possible, but I'm not doing it :-)

# Devices Never Supported
- Wiser Smartplug V1 (cannot be made to join)
- Wiser Controller (it replaces this)

# How to Join (except Smartplug V2)
Where the instructions indicate to press the set button, instead hold for 5 seconds.  There will be no indication until release, it will then blink with a normal join process, but actually attempt a normal HA style join, rather than the proprietary Wiser join, which only wants to pair with its own controller.

# How to Join Smartplug V2
It will join with a normal press on the set button, however it will only work on channels 11,15,20,25.  You need to setup your hub with this network BEFORE you start joining devices to not break up your network.

# RTS Limitations
Due to the structure of the EmberZnet interface the Smarthub has towards its Zigbee Chip, I cannot emulate the necessary server clusters for allowing the RTS to work directly with the Hub.  Therefore the RTS __can only__ work with the H-Relay.  I have provided a bare-bones Smartapp in the repo to perform the binding.  Only one-to-one binding is supported at this stage, some enterprising individual is welcome to implement multi H-Relay bind/unbind, if their usecase supports.


This project is mostly to try and keep my housefull of these heating devices still running, and 90% of the features I don't care about.  The SmartThings platform streamlines alot of the non-zigbee configuration, but frankly this level of integration pushes the APIs to its limit, so more is not super likely

Also, this must be said... __this is alpha grade and should not be relied upon to stop your house from freezing__

If you want to join me in tooling around with some almost-obsolete but decent quality Zigbee hardware, you are most welcome.  Together we get Wiser ;-)

-P. Klokke

