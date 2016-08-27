package org.openhab.binding.smartbee.internal;

public class SmartBeePin {

    public int pinNumber;
   
    public static enum PinType {
        ANALOG,
        DIGITAL
    }

    public PinType pinType;

    public SmartBeePin(String pin) {
        if (pin.startsWith("A")) {
            pinType = PinType.ANALOG;
        } else if (pin.startsWith("D")) {
            pinType = PinType.DIGITAL;
        } else {
            throw new IllegalArgumentException("Pin should start with A (for analog) or D (for digital)");
        }
        pinNumber = Integer.parseInt(pin.substring(1));

    }
    
    public boolean isAnalog() {
        return pinType == PinType.ANALOG;
    }
    
    public boolean isDigital() {
        return pinType == PinType.DIGITAL;
    }

    @Override
    public String toString() {
        String repr;
        if (pinType == PinType.ANALOG) {
            repr = "A";
        } else {
            repr = "D";
        }
        repr += Integer.toString(pinNumber);
        return repr;
    }
}
