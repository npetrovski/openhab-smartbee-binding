package org.openhab.binding.smartbee.internal.pin;

import com.digi.xbee.api.io.IOLine;

/**
 * @author Nikolay Petrovski
 * @since 1.8
 */
public class SmartBeePin {
	public SmartBeePinType pinType;
	public int pinNumber;
        public IOLine ioLine;

	public SmartBeePin(String pin) {
		if (pin.startsWith("A")) {
			pinType = SmartBeePinType.ANALOG;
		} else if (pin.startsWith("D")) {
			pinType = SmartBeePinType.DIGITAL;
		} else {
			throw new IllegalArgumentException("Pin should start with A (for analog) or D (for digital)");
		}
		pinNumber = Integer.parseInt(pin.substring(1));

                ioLine = IOLine.getDIO(pinNumber);
	}

        public IOLine getIOLine() {
            return ioLine;
        }

	@Override
	public String toString() {
		String repr;
		if (pinType == SmartBeePinType.ANALOG) {
			repr = "A";
		} else {
			repr = "D";
		}
		repr += Integer.toString(pinNumber);
		return repr;
	}
}
