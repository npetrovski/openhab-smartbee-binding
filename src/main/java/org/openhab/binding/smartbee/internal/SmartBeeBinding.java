package org.openhab.binding.smartbee.internal;

import com.digi.xbee.api.AbstractXBeeDevice;
import org.openhab.binding.smartbee.SmartBeeBindingProvider;

import java.util.Dictionary;

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
import com.digi.xbee.api.listeners.IPacketReceiveListener;
import com.digi.xbee.api.XBeeDevice;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.io.IOLine;
import com.digi.xbee.api.packet.XBeePacket;
import com.digi.xbee.api.utils.HexUtils;
import com.digi.xbee.api.io.IOMode;
import com.digi.xbee.api.io.IOSample;
import com.digi.xbee.api.io.IOValue;
import com.digi.xbee.api.listeners.IIOSampleReceiveListener;
import com.digi.xbee.api.models.XBee64BitAddress;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.ArrayUtils;
import org.openhab.core.binding.AbstractBinding;
import org.openhab.core.library.items.ContactItem;
import org.openhab.core.library.types.OpenClosedType;

/**
 * SmartBee Binding.
 *
 * @author Nikolay Petrovski
 * @since 1.8
 *
 */
public class SmartBeeBinding extends AbstractBinding<SmartBeeBindingProvider> implements ManagedService, IPacketReceiveListener, IIOSampleReceiveListener {

    private String serialPort;

    private int baudRate = 9600;

    private static final Logger LOG = LoggerFactory.getLogger(SmartBeeBinding.class);

    private XBeeDevice xbee = null;

    private static final Pattern EXTRACT_CONFIG_PATTERN = Pattern.compile("initDevice\\.(?<address>([0-9a-zA-Z])+)\\.(?<prop>(pin|sample))\\.(?<key>.*)");

    /**
     * Map table to store all available receivers configured by the user
     */
    private Map<String, DeviceConfig> deviceConfigCache = null;

    private String coordinatorAddr;

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

            //xbee.removeDataListener(this);
            xbee.removePacketListener(this);
            xbee.removeIOSampleListener(this);

            xbee.close();
            xbee = null;
        }
    }

    /**
     * Internal data structure which carries the connection details of one
     * device (there could be several)
     */
    static class DeviceConfig {

        private final String address;

        private int sampleRate = 0;

        private Map<IOLine, IOMode> pins = new HashMap();

        private Set<IOLine> lines = new HashSet();

        public DeviceConfig(String addr) {
            this.address = addr.toUpperCase();
        }

        public String getAddress() {
            return address;
        }

        public void addPin(Integer num, String state) {
            IOMode pinstate;
            if (state.toLowerCase().equals("high")) {
                pinstate = IOMode.DIGITAL_OUT_HIGH;
            } else if (state.toLowerCase().equals("low")) {
                pinstate = IOMode.DIGITAL_OUT_LOW;
            } else if (state.toLowerCase().equals("din")) {
                pinstate = IOMode.DIGITAL_IN;
            } else if (state.toLowerCase().equals("adc")) {
                pinstate = IOMode.ADC;
            } else if (state.toLowerCase().equals("pwm")) {
                pinstate = IOMode.PWM;
            } else if (state.toLowerCase().equals("spec")) {
                pinstate = IOMode.SPECIAL_FUNCTIONALITY;
            } else {
                return;
            }
            pins.put(IOLine.getDIO(num), pinstate);
        }

        public Map<IOLine, IOMode> getPins() {
            return pins;
        }

        public Set<IOLine> getLines() {
            return lines;
        }

        public void setLines(String pins) {
            for (String pin : pins.split(",")) {
                lines.add(IOLine.getDIO(Integer.valueOf(pin.trim())));
            }
        }

        public void setSampleRate(int value) {
            sampleRate = (int) value;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public boolean useSampling() {
            return sampleRate > 0 || lines.size() > 0;
        }
    }

    private DeviceConfig getDeviceConfig(String addr) {
        if (null != deviceConfigCache.get(addr)) {
            return deviceConfigCache.get(addr);
        }

        return new DeviceConfig(addr);
    }

    @Override
    public void updated(Dictionary<String, ?> config) throws ConfigurationException {
        LOG.debug("SmartBee configuration updated.");

        if (config != null) {

            Enumeration<String> keys = config.keys();

            if (deviceConfigCache == null) {
                deviceConfigCache = new HashMap<String, DeviceConfig>();
            }

            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();

                if ("service.pid".equals(key)) {
                    continue;
                }

                if ("serialPort".equals(key)) {
                    // Get the configuration
                    serialPort = (String) config.get("serialPort");
                    LOG.info("Update config, serialPort = {}", serialPort);
                    
                    if ((System.getProperty("os.name").toLowerCase().contains("linux"))) {
                        System.setProperty("gnu.io.rxtx.SerialPorts", serialPort);
                    }
                    continue;
                }

                if ("baudRate".equals(key)) {
                    baudRate = Integer.parseInt(String.valueOf(config.get("baudRate")));
                    LOG.info("Update config, baudRate = {}", String.valueOf(baudRate));

                    continue;
                }

                Matcher matcher = EXTRACT_CONFIG_PATTERN.matcher(key);

                if (!matcher.matches()) {
                    LOG.debug("given config key '{}' does not follow the expected pattern", key);
                    continue;
                }

                matcher.reset();
                while (matcher.find()) {
                    if (null != matcher.group("address")) {
                        DeviceConfig deviceConfig = getDeviceConfig(matcher.group("address"));
                        if (matcher.group("prop").equals("pin") && null != matcher.group("key")) {
                            deviceConfig.addPin(Integer.valueOf(matcher.group("key")), String.valueOf(config.get(key)));
                        } else if (matcher.group("prop").equals("sample") && matcher.group("key").equals("change")) {
                            deviceConfig.setLines(String.valueOf(config.get(key)));
                        } else if (matcher.group("prop").equals("sample") && matcher.group("key").equals("rate")) {
                            deviceConfig.setSampleRate(Integer.valueOf(config.get(key).toString()));
                        }

                        deviceConfigCache.put(matcher.group("address"), deviceConfig);
                    }
                }

            }

            // Connect to the local XBee
            connect(serialPort, baudRate);

            // Initialise devices
            initializeDevices();
        }
    }

    /**
     * Connect to the XBee
     *
     * @param serialPort port to connect to
     * @param baudRate baud rate to use
     * @throws ConfigurationException
     */
    public void connect(String serialPort, int baudRate) throws ConfigurationException {

        LOG.info("Connecting to XBee [serialPort='{}', baudRate={}].", new Object[]{serialPort, baudRate});

        try {

            if (xbee != null) {
                disconnect();
            }

            xbee = new XBeeDevice(serialPort, baudRate);

            xbee.open();

            byte[] paramValueSH = xbee.getParameter("SH");
            byte[] paramValueSL = xbee.getParameter("SL");
            byte[] addr = ArrayUtils.addAll(paramValueSH, paramValueSL);
            coordinatorAddr = HexUtils.byteArrayToHexString(addr).toUpperCase();

            LOG.info("Connection successfull. Coordinator XBee [addr='{}']", coordinatorAddr);

            xbee.addPacketListener(this);

        } catch (XBeeException e) {
            if (xbee.isOpen()) {
                xbee.close();
            }

            xbee = null;
            LOG.error("XBee connection failed: {}", e);
            throw new ConfigurationException("serialPort", e.getMessage());
        }
    }

    /**
     * Initialize XBee device
     */
    public void initializeDevices() {

        if (deviceConfigCache.isEmpty()) {
            return;
        }

        for (DeviceConfig devConfig : deviceConfigCache.values()) {
            try {
                LOG.debug("Configuring device {}", devConfig.getAddress());

                AbstractXBeeDevice device = getXBeeDevice(devConfig.getAddress());
                device.enableApplyConfigurationChanges(false);
                for (Map.Entry<IOLine, IOMode> entry : devConfig.getPins().entrySet()) {
                    device.setIOConfiguration(entry.getKey(), entry.getValue());
                }

                device.setIOSamplingRate(devConfig.getSampleRate());

                if (!devConfig.getLines().isEmpty()) {
                    device.setDIOChangeDetection(devConfig.getLines());
                }

                if (devConfig.useSampling()) {
                    if (!devConfig.getAddress().equals(coordinatorAddr)) {
                        device.setDestinationAddress(new XBee64BitAddress(coordinatorAddr));
                    }

                    // Register a listener to handle the samples received by the local device.
                    xbee.addIOSampleListener(this);
                }

                device.applyChanges();
            } catch (XBeeException e) {
                LOG.error(e.getMessage());
            }
        }

    }

    @Override
    protected void internalReceiveCommand(String itemName, Command command) {
        LOG.debug("internalReceiveCommand({},{}) is called!", itemName, command);

        for (SmartBeeBindingProvider provider : providers) {
            if (provider.providesBindingFor(itemName)) {
                try {

                    if (provider.isSensor(itemName)) {
                        return;
                    }

                    AbstractXBeeDevice device = getXBeeDevice(provider, itemName);

                    SmartBeePin pin = provider.getPin(itemName);

                    IOLine line = IOLine.getDIO(pin.pinNumber);

                    if (pin.isDigital()) {
                        if (command == OnOffType.ON || command == OpenClosedType.OPEN) {
                            device.setDIOValue(line, IOValue.HIGH);

                        } else if (command == OnOffType.OFF || command == OpenClosedType.CLOSED) {
                            device.setDIOValue(line, IOValue.LOW);
                        }
                    }

                } catch (XBeeException e) {
                    LOG.error(e.getMessage());
                }
            }
        }
    }

    private AbstractXBeeDevice getXBeeDevice(String addr) {
        if (addr.equals(coordinatorAddr)) {
            return xbee;
        }
        return new RemoteXBeeDevice(xbee, new XBee64BitAddress(addr));
    }

    private AbstractXBeeDevice getXBeeDevice(SmartBeeBindingProvider provider, String itemName) {
        return getXBeeDevice(provider.getAddress(itemName));
    }

    private Number applyValueTransformation(String expr, Number value) {

        LOG.debug("Apply transformation: {} [{}]", expr, value);

        try {
            return (new ExpressionBuilder(expr))
                    .variables("x")
                    .build()
                    .setVariable("x", value.doubleValue())
                    .evaluate();
        } catch (Throwable e) {
            LOG.error("Transformation error: {}", e);
        }

        return value;
    }

    @Override
    public void ioSampleReceived(RemoteXBeeDevice remoteDevice, IOSample ioSample) {

        HashMap<IOLine, Integer> aV = ioSample.getAnalogValues();
        HashMap<IOLine, IOValue> dV = ioSample.getDigitalValues();

        for (SmartBeeBindingProvider provider : providers) {
            Collection<String> items = provider.getItemsByAddress(remoteDevice.get64BitAddress().toString().toUpperCase());

            for (String itemName : items) {

                State newState = null;

                SmartBeePin pin = provider.getPin(itemName);
                IOLine line = IOLine.getDIO(pin.pinNumber);

                if (pin.isAnalog() && null != aV.get(line)) {

                    Number pinValue = aV.get(line);
                    // Apply the transformation if any
                    if (provider.getTransformation(itemName) != null) {
                        pinValue = applyValueTransformation(provider.getTransformation(itemName), (Number) pinValue);
                    }

                    newState = new DecimalType(pinValue.longValue());

                    eventPublisher.postUpdate(itemName, newState);

                } else if (pin.isDigital() && null != dV.get(line)) {
                    IOValue pinValue = dV.get(line);

                    if (provider.getItemType(itemName).isAssignableFrom(SwitchItem.class)) {
                        if (pinValue == IOValue.LOW) {
                            newState = OnOffType.OFF;
                        } else {
                            newState = OnOffType.ON;
                        }
                    } else if (provider.getItemType(itemName).isAssignableFrom(ContactItem.class)) {
                        if (pinValue == IOValue.LOW) {
                            newState = OpenClosedType.CLOSED;
                        } else {
                            newState = OpenClosedType.OPEN;
                        }
                    }

                    eventPublisher.postUpdate(itemName, newState);
                }
            }
        }
    }

    @Override
    public void packetReceived(XBeePacket xbp) {

        LOG.debug("packetReceived(). Packet received: {} \n {}", HexUtils.prettyHexString(xbp.getPacketData()), xbp.toPrettyString());
    }

}
