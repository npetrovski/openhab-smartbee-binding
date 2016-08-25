package org.openhab.binding.smartbee.internal;

import org.openhab.binding.smartbee.internal.pin.SmartBeePin;
import org.openhab.binding.smartbee.SmartBeeBindingProvider;

import java.util.Dictionary;

import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.objecthunter.exp4j.ExpressionBuilder;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.listeners.IDataReceiveListener;
import com.digi.xbee.api.listeners.IPacketReceiveListener;
import com.digi.xbee.api.models.XBeeMessage;
import com.digi.xbee.api.XBeeDevice;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.packet.XBeePacket;
import com.digi.xbee.api.utils.HexUtils;
import com.digi.xbee.api.io.IOMode;

import org.openhab.core.binding.AbstractActiveBinding;

/**
 * SmartBee Binding.
 *
 * @author Nikolay Petrovski
 * @since 1.8
 */
public class SmartBeeBinding extends AbstractActiveBinding<SmartBeeBindingProvider> implements ManagedService, IDataReceiveListener, IPacketReceiveListener {

    /**
     * Default refresh interval (currently 30 seconds)
     */
    private long refreshInterval = 30000L;

    private static final Logger LOG = LoggerFactory.getLogger(SmartBeeBinding.class);

    private XBeeDevice xbee = null;

    @Override
    public void activate() {
        LOG.debug("Activate SmartBee");
    }

    @Override
    public void deactivate() {
        LOG.debug("Deactivate SmartBee");
        disconnect();
    }

    /**
     * Disconnect from the XBee
     */
    public void disconnect() {

        if (xbee != null) {

            LOG.debug("Disconnecting from the XBee");

            xbee.removeDataListener(this);
            xbee.removePacketListener(this);

            xbee.close();
            xbee = null;
        }
    }

    @Override
    public void updated(Dictionary<String, ?> config) throws ConfigurationException {
        LOG.debug("SmartBee configuration updated");

        if (config != null) {
            String refreshIntervalString = (String) config.get("refresh");
            if (!(refreshIntervalString == null || refreshIntervalString.trim().isEmpty())) {
                refreshInterval = Long.parseLong(refreshIntervalString);
            }

            // Get the configuration
            String serialPort = (String) config.get("serialPort");
            String baudRate = (String) config.get("baudRate");

            // Connect to the XBee
            reconnect(serialPort, baudRate != null ? Integer.parseInt(baudRate) : 9600);

            setProperlyConfigured(true);
        }
    }

    /**
     * Connect to the XBee
     *
     * @param serialPort port to connect to
     * @param baudRate baud rate to use
     * @throws ConfigurationException
     */
    public void reconnect(String serialPort, int baudRate) throws ConfigurationException {

        LOG.info("Connecting to XBee [serialPort='{}', baudRate={}].", new Object[]{serialPort, baudRate});

        try {

            if (xbee != null) {
                disconnect();
            }

            xbee = new XBeeDevice(serialPort, baudRate);

            xbee.open();

            xbee.addDataListener(this);
            xbee.addPacketListener(this);

            byte[] paramValueNI = xbee.getParameter("NI");
            byte[] paramValueID = xbee.getParameter("ID");

            LOG.info("Connection successfull. Local XBee [nodeId='{}', panId='{}']", new Object[]{new String(paramValueNI), HexUtils.prettyHexString(paramValueID)});
        } catch (XBeeException e) {

            xbee = null;
            LOG.error("XBee connection failed: {}", e);
            throw new ConfigurationException("serialPort", e.getMessage());
        }
    }

    @Override
    protected void internalReceiveCommand(String itemName, Command command) {
        LOG.debug("internalReceiveCommand({},{}) is called!", itemName, command);

        for (SmartBeeBindingProvider provider : providers) {
            if (provider.providesBindingFor(itemName)) {
                internalReceiveCommand(provider, itemName, command);
            }
        }
    }

    private void internalReceiveCommand(SmartBeeBindingProvider provider, String itemName, Command command) {

        try {

            RemoteXBeeDevice remote = getRemoteDevice(provider, itemName);
            SmartBeePin pin = provider.getPin(itemName);

            if (command == OnOffType.ON) {
                remote.setIOConfiguration(pin.getIOLine(), IOMode.DIGITAL_OUT_HIGH);
                //remote.setDIOValue(pin.getIOLine(), IOValue.HIGH);

            } else if (command == OnOffType.OFF) {
                remote.setIOConfiguration(pin.getIOLine(), IOMode.DISABLED);
                //remote.setDIOValue(pin.getIOLine(), IOValue.LOW);
            }
        } catch (XBeeException e) {
            LOG.error(e.getMessage());
        }

    }

    private RemoteXBeeDevice getRemoteDevice(SmartBeeBindingProvider provider, String itemName) {
        return new RemoteXBeeDevice(xbee, provider.getAddress(itemName));
    }

//    @Override
//    public void processResponse(XBeeResponse response) {
//        // Handle error responses
//        if (response.isError()) {
//            LOG.error("Error response received: {}", ((ErrorResponse) response).getErrorMsg());
//            return;
//        }
//
//        // Handle incoming responses
//        LOG.debug("Response received: {}", response);
//        for (XBeeBindingProvider provider : providers) {
//            for (String itemName : provider.getInBindingItemNames()) {
//                State newState = null;
//
//                // Check the responseType
//                if (response.getClass() != provider.getResponseType(itemName)) {
//                    continue;
//                }
//
//                // Depending on the response type
//                // TODO: Support more
//                if (response.getClass() == ZNetRxIoSampleResponse.class) { // ZNetRxIoSampleResponse
//                    ZNetRxIoSampleResponse znetRxIoSampleResponse = (ZNetRxIoSampleResponse) response;
//
//                    // Check the address
//                    if (!znetRxIoSampleResponse.getRemoteAddress64().equals(provider.getAddress(itemName))) {
//                        continue;
//                    }
//
//                    // Get the data depending on the pin
//                    if (provider.getPin(itemName).pinType == XBeePinType.DIGITAL) {
//                        // Check that the digital pin is enabled
//                        if (!znetRxIoSampleResponse.isDigitalEnabled(provider.getPin(itemName).pinNumber)) {
//                            LOG.error("Digital pin {} is not enabled", provider.getPin(itemName).pinNumber);
//                            continue;
//                        }
//
//                        // Get the value
//                        Boolean isPinOn = znetRxIoSampleResponse.isDigitalOn(provider.getPin(itemName).pinNumber);
//
//                        // Cast according to the itemType
//                        // TODO: Support more
//                        if (provider.getItemType(itemName).isAssignableFrom(SwitchItem.class)) {
//                            if (isPinOn) {
//                                newState = OnOffType.ON;
//                            } else {
//                                newState = OnOffType.OFF;
//                            }
//                        } else if (provider.getItemType(itemName).isAssignableFrom(ContactItem.class)) {
//                            if (isPinOn) {
//                                newState = OpenClosedType.OPEN;
//                            } else {
//                                newState = OpenClosedType.CLOSED;
//                            }
//                        } else {
//                            LOG.error("Cannot create state of type {} for value {}", provider.getItemType(itemName),
//                                    isPinOn);
//                            continue;
//                        }
//                    } else {
//                        // Check that the analog pin is enabled
//                        if (!znetRxIoSampleResponse.isAnalogEnabled(provider.getPin(itemName).pinNumber)) {
//                            LOG.error("Analog pin {} is not enabled", provider.getPin(itemName).pinNumber);
//                            continue;
//                        }
//
//                        // Get the value
//                        Double pinValue = (double) znetRxIoSampleResponse
//                                .getAnalog(provider.getPin(itemName).pinNumber);
//
//                        // Apply the transformation if any
//                        if (provider.getTransformation(itemName) != null) {
//                            try {
//                                pinValue = new ExpressionBuilder(provider.getTransformation(itemName))
//                                        .withVariable("x", pinValue).build().calculate();
//                            } catch (Exception e) {
//                                LOG.error("Transformation error: {}", e);
//                                continue;
//                            }
//                        }
//
//                        // Cast according to the itemType
//                        // TODO: Support more
//                        if (provider.getItemType(itemName).isAssignableFrom(NumberItem.class)) {
//                            newState = new DecimalType(pinValue);
//                        } else if (provider.getItemType(itemName).isAssignableFrom(DimmerItem.class)) {
//                            newState = new PercentType(pinValue.intValue());
//                        } else {
//                            LOG.error("Cannot create state of type {} for value {}", provider.getItemType(itemName),
//                                    pinValue);
//                            continue;
//                        }
//                    }
//                } else if (response.getClass() == ZNetRxResponse.class) { // ZNetRxResponse
//                    ZNetRxResponse znetRxResponse = (ZNetRxResponse) response;
//
//                    // Check the address
//                    if (!znetRxResponse.getRemoteAddress64().equals(provider.getAddress(itemName))) {
//                        continue;
//                    }
//
//                    // Feed the raw data to the buffer
//                    ByteBuffer buffer = ByteBuffer.allocate(100);
//                    buffer.order(ByteOrder.BIG_ENDIAN);
//                    for (int i : znetRxResponse.getData()) {
//                        buffer.put((byte) i);
//                    }
//
//                    // Check the first byte
//                    if (provider.getFirstByte(itemName) != null && provider.getFirstByte(itemName) != buffer.get(0)) {
//                        continue;
//                    }
//
//                    // Cast according to the itemType
//                    // TODO: Support more
//                    if (provider.getItemType(itemName).isAssignableFrom(SwitchItem.class)) {
//                        if (buffer.get(provider.getDataOffset(itemName)) == 1) {
//                            newState = OnOffType.ON;
//                        } else {
//                            newState = OnOffType.OFF;
//                        }
//                    } else if (provider.getItemType(itemName).isAssignableFrom(ContactItem.class)) {
//                        if (buffer.get(provider.getDataOffset(itemName)) == 1) {
//                            newState = OpenClosedType.OPEN;
//                        } else {
//                            newState = OpenClosedType.CLOSED;
//                        }
//                    } else if (provider.getItemType(itemName).isAssignableFrom(NumberItem.class)) {
//                        if (provider.getDataType(itemName) == int.class) {
//                            newState = new DecimalType(buffer.getInt(provider.getDataOffset(itemName)));
//                        } else if (provider.getDataType(itemName) == byte.class) {
//                            newState = new DecimalType(buffer.get(provider.getDataOffset(itemName)));
//                        } else {
//                            newState = new DecimalType(buffer.getFloat(provider.getDataOffset(itemName)));
//                        }
//                    } else if (provider.getItemType(itemName).isAssignableFrom(DimmerItem.class)) {
//                        newState = new PercentType(buffer.get(provider.getDataOffset(itemName)));
//                    } else {
//                        LOG.debug("Cannot create state of type {} for value {}", provider.getItemType(itemName),
//                                buffer);
//                        continue;
//                    }
//                } else {
//                    LOG.debug("Unhandled response type {}", response.getClass().toString().toLowerCase());
//                    continue;
//                }
//
//                // Publish the new state
//                eventPublisher.postUpdate(itemName, newState);
//            }
//        }
//    }
    @Override
    public void dataReceived(XBeeMessage xbeeMessage) {
        LOG.debug("dataReceived(). From {} >> {} | {}", xbeeMessage.getDevice().get64BitAddress(),
                HexUtils.prettyHexString(HexUtils.prettyHexString(xbeeMessage.getData())),
                new String(xbeeMessage.getData()));
    }

    @Override
    public void packetReceived(XBeePacket xbp) {

        LOG.debug("packetReceived(). Packet received: {} \n {}", HexUtils.prettyHexString(xbp.getPacketData()), xbp.toPrettyString());
    }

    @Override
    protected void execute() {
        LOG.debug("execute() is called!");

        for (SmartBeeBindingProvider provider : providers) {
            for (String itemName : provider.getItemNames()) {
                updateItem(provider, itemName);
            }
        }
    }

    private void updateItem(SmartBeeBindingProvider provider, String itemName) {

        State newState = null;

        try {
            RemoteXBeeDevice remote = getRemoteDevice(provider, itemName);

            if (provider.getItemType(itemName).isAssignableFrom(NumberItem.class)) {
                remote.setIOConfiguration(provider.getPin(itemName).getIOLine(), IOMode.ADC);
                int pinValue = remote.getADCValue(provider.getPin(itemName).getIOLine());
                long value = 0;
                // Apply the transformation if any
                if (provider.getTransformation(itemName) != null) {
                    try {
                        value = (long) (new ExpressionBuilder(provider.getTransformation(itemName))
                                .variables("x")
                                .build()
                                .setVariable("x", pinValue)
                                .evaluate());
                    } catch (Exception e) {
                        LOG.error("Transformation error: {}", e);
                    }
                } else {
                    value = (long) pinValue;
                }

                newState = new DecimalType(value);
            } else if (provider.getItemType(itemName).isAssignableFrom(SwitchItem.class)) {
                IOMode mode = remote.getIOConfiguration(provider.getPin(itemName).getIOLine());
                if (mode == IOMode.DISABLED) {
                    newState = OnOffType.OFF;
                } else {
                    newState = OnOffType.ON;
                }
            }

            eventPublisher.postUpdate(itemName, newState);
        } catch (Throwable e) {
            e.printStackTrace();
            LOG.error(e.getMessage());
        }

    }

    @Override
    protected long getRefreshInterval() {
        return refreshInterval;
    }

    @Override
    protected String getName() {
        return "SmartBee bundle";
    }

    protected void addBindingProvider(SmartBeeBindingProvider bindingProvider) {
        super.addBindingProvider(bindingProvider);
    }

    protected void removeBindingProvider(SmartBeeBindingProvider bindingProvider) {
        super.removeBindingProvider(bindingProvider);
    }

}
