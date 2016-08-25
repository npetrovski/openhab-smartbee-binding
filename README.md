# openhab-smartbee-binding

An OpenHAB (v.1.8.3) binding that is defined as a standalone Maven project - it does not require Eclipse IDE and the entire OpenHab code in order to be compiled (for this project I am using NetBeans).

The purpose of the project is to provide a basic XBee management module for OpenHAB by using the [digi.com Java library](http://docs.digi.com/display/XBJLIB/XBee+Java+Library).

## Binding Configuration

Example openhab.cfg entry:

    ############################### SmartBee Binding ####################################
    #
    smartbee:serialPort=/dev/ttyAMA0
    smartbee:baudRate=9600
    smartbee:refresh=30000
    
    
<table>
<tr><td>Option</td><td>Description</td></tr>
<tr><td>smartbee:serialPort</td><td>value indicates the serial port on the host system to which the XBee controller is connected, e.g. "COM1" on Windows, "/dev/ttyS0" or "/dev/ttyUSB0" on Linux or "/dev/tty.PL2303-0000103D" on Mac.<br>
Note that some controllers register themselves as a modem (/dev/ttyACM) on Linux. In this case it is necessary to add user "openhab" to the group "dialout". Else openHAB won't be able to access the controller.
</td></tr>
<tr><td>smartbee:baudRate</td><td>Serial port boud rate.</td></tr>
<tr><td>smartbee:refresh</td><td>Refresh period (in ms) - the interval for reading the XBee values and populate them to the OpenHAB items.</td></tr>
</table> 


## Item configuration

In order to bind an item to a XBee device, you need to provide configuration settings. The easiest way to do so is to add some binding information in your item file (in the folder configurations/items`). The syntax for the XBee binding configuration string is explained here:
The format of the binding configuration is simple and looks like this:

    xbee="<direction><address>#<pinType><pinNumber>[:<transformation>]"

where parts in brackets indicate an optional item.

 - "direction" indicates whether the Item is a sensor or can be controled from the OpenHAB. Use "<" for sensors (DIRECTION.IN) or ">" for controlable items (DIRECTION.OUT)
 - "address" stands for 64bit Mac Address of the XBee device.
 - "pinType" defines the type of the pin that will be managed - can be "D" for digital or "A" for analog IO type.
 - "pinNumber" is the number of the XBee pin.
 - "transformation" (optional) is an [exp4j expression](http://www.objecthunter.net/exp4j/) that allows altering the pin value.
 
An example item definition:

    Switch    Battery_Led  "Led" <switch>  { smartbee=">0013A20040DB628A#D8:" }
    
or
    
    Number    Battery_Temperature	"Temperature [%.1f °C]"	 <temperature>	{ xbee="<0013A20040DB628A#A2:((x*3.2258)-500)/10" }
    

