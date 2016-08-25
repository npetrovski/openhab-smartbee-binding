package org.openhab.binding.smartbee.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openhab.binding.smartbee.internal.pin.SmartBeePin;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.openhab.model.item.binding.BindingConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digi.xbee.api.models.XBee64BitAddress;
import org.openhab.binding.smartbee.SmartBeeBindingProvider;

/**
 * This class is responsible for parsing the binding configuration.
 *
 * @author Nikolay Petrovski
 * @since 1.8
 */
public class SmartBeeGenericBindingProvider extends AbstractGenericBindingProvider implements SmartBeeBindingProvider,
        BindingConfigReader {

    /**
     * Artificial command for the in-binding configuration (which has no command part by definition). Because we use
     * this artificial command we can reuse the {@link XBeeBindingConfig} for both in- and out-configuration.
     */
    protected static final Command IN_BINDING_KEY = StringType.valueOf("IN_BINDING");

    /**
     * {@link Pattern} which matches for an IoSampleResponse In-Binding
     */
    private static final Pattern PATTERN = Pattern
            .compile("(?<address>([0-9a-zA-Z])+)#(?<pin>[AD][0-9]{1,2})(:(?<transformation>.*))?");

    private static final Logger LOG = LoggerFactory.getLogger(SmartBeeGenericBindingProvider.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBindingType() {
        return "xbee";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
        // Accept all sort of items
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processBindingConfiguration(String context, Item item, String bindingConfig)
            throws BindingConfigParseException {

        super.processBindingConfiguration(context, item, bindingConfig);

        // Create the config
        XBeeBindingConfig config = new XBeeBindingConfig();
        config.itemType = item.getClass();

        // Match patterns
        if (PATTERN.matcher(bindingConfig).matches()) {
            Matcher matcher = PATTERN.matcher(bindingConfig);
            while (matcher.find()) {
                XBeeInBindingConfigElement configElement;
                configElement = new XBeeInBindingConfigElement();

                // Parse the address, pin and transformation
                configElement.address = new XBee64BitAddress(matcher.group("address"));
                configElement.pin = new SmartBeePin(matcher.group("pin"));
                configElement.transformation = matcher.group("transformation");

                // Add to the config
                LOG.debug("Adding in-binding configElement: {}", configElement.toString());
                config.put(IN_BINDING_KEY, configElement);
            }
        } else {
            throw new BindingConfigParseException("Invalid binding configuration '" + bindingConfig + "'");
        }

        LOG.debug("Adding binding config for item {}", item.getName());
        addBindingConfig(item, config);
    }

    @Override
    public Class<? extends Item> getItemType(String itemName) {
        XBeeBindingConfig config = (XBeeBindingConfig) bindingConfigs.get(itemName);
        return config != null ? config.itemType : null;
    }

    @Override
    public XBee64BitAddress getAddress(String itemName) {
        XBeeBindingConfig config = (XBeeBindingConfig) bindingConfigs.get(itemName);
        return config != null && config.get(IN_BINDING_KEY) != null ? ((XBeeInBindingConfigElement) config
                .get(IN_BINDING_KEY)).address : null;
    }

    @Override
    public SmartBeePin getPin(String itemName) {
        XBeeBindingConfig config = (XBeeBindingConfig) bindingConfigs.get(itemName);
        return config != null && config.get(IN_BINDING_KEY) != null ? ((XBeeInBindingConfigElement) config
                .get(IN_BINDING_KEY)).pin : null;
    }

    @Override
    public String getTransformation(String itemName) {
        XBeeBindingConfig config = (XBeeBindingConfig) bindingConfigs.get(itemName);
        return config != null && config.get(IN_BINDING_KEY) != null ? ((XBeeInBindingConfigElement) config
                .get(IN_BINDING_KEY)).transformation : null;
    }

    @Override
    public List<String> getInBindingItemNames() {
        List<String> inBindings = new ArrayList<String>();
        for (String itemName : bindingConfigs.keySet()) {
            XBeeBindingConfig config = (XBeeBindingConfig) bindingConfigs.get(itemName);
            if (config.containsKey(IN_BINDING_KEY)) {
                inBindings.add(itemName);
            }
        }
        return inBindings;
    }

    private Class<? extends Number> parseDataType(String dataType) {
        if (dataType == null) {
            return null;
        } else if (dataType.equals("float")) {
            return float.class;
        } else if (dataType.equals("int")) {
            return int.class;
        } else if (dataType.equals("byte")) {
            return byte.class;
        }
        return null;
    }

    static class XBeeBindingConfig extends HashMap<Command, BindingConfig> implements BindingConfig {

        /**
         * Generated serial version uid
         */
        private static final long serialVersionUID = 2541964231552108432L;
        Class<? extends Item> itemType;
    }

    static class XBeeInBindingConfigElement implements BindingConfig {

        //Class<? extends XBeeResponse> responseType;
        XBee64BitAddress address;
        SmartBeePin pin;
        String transformation;


        @Override
        public String toString() {
            String repr = /*responseType.getName() +*/ "(";
            repr += address != null ? "address=" + address : "address=null";
            repr += pin != null ? ", pin=" + pin : "";
            repr += transformation != null ? ", transformation=" + transformation : "";
            repr += ")";
            return repr;
        }
    }

    static class XBeeOutBindingConfigElement implements BindingConfig {

        //XBeeRequest request;
    }
}
