package org.openhab.binding.smartbee.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openhab.binding.smartbee.SmartBeeBindingConfig;

import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.openhab.model.item.binding.BindingConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openhab.binding.smartbee.SmartBeeBindingProvider;

/**
 * This class is responsible for parsing the binding configuration.
 *
 * @author Nikolay Petrovski
 * @since 1.8
 */
public class SmartBeeGenericBindingProvider extends AbstractGenericBindingProvider implements SmartBeeBindingProvider,
        BindingConfigReader {


    private static final Pattern CONFIG_PATTERN = Pattern
            .compile("(?<direction>[\\<\\>]{1})(?<address>([0-9a-zA-Z])+)#(?<pin>[AD][0-9]{1,2})(\\|(?<transformation>.*))?");

    private static final Logger LOG = LoggerFactory.getLogger(SmartBeeGenericBindingProvider.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBindingType() {
        return "smartbee";
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

        // Match patterns
        if (CONFIG_PATTERN.matcher(bindingConfig).matches()) {
            Matcher matcher = CONFIG_PATTERN.matcher(bindingConfig);
            while (matcher.find()) {

                SmartBeeBindingConfig config = new SmartBeeBindingConfig();
                config.itemType = item.getClass();
                config.direction = matcher.group("direction").equals("<")
                        ? SmartBeeBindingConfig.DIRECTION.IN
                        : SmartBeeBindingConfig.DIRECTION.OUT;
                config.address = matcher.group("address").toUpperCase();
                config.pin = new SmartBeePin(matcher.group("pin"));
                config.transformation = matcher.group("transformation");
                config.types = item.getAcceptedDataTypes();

                addBindingConfig(item, config);
            }

        } else {
            throw new BindingConfigParseException("Invalid binding configuration '" + bindingConfig + "'");
        }

        LOG.debug("Adding binding config for item {}", item.getName());

    }

    public Map<String, BindingConfig> getBindingConfig() {
        return bindingConfigs;
    }

    private SmartBeeBindingConfig getBinding(final String itemName) {
        return (SmartBeeBindingConfig) bindingConfigs.get(itemName);
    }

    public Collection<String> getItemsByAddress(final String addr) {
        Collection<String> bindings = new ArrayList<>();
        for (Map.Entry<String, BindingConfig> entry : bindingConfigs.entrySet()) {
            if (((SmartBeeBindingConfig) entry.getValue()).address.equals(addr)) {
                bindings.add(entry.getKey());
            }
        }

        return bindings;
    }

    @Override
    public String getAddress(String itemName) {
        return getBinding(itemName).address;
    }

    @Override
    public SmartBeePin getPin(String itemName) {
        return getBinding(itemName).pin;
    }

    @Override
    public String getTransformation(String itemName) {
        return getBinding(itemName).transformation;
    }

    @Override
    public boolean isSensor(String itemName) {
        return getBinding(itemName).direction == SmartBeeBindingConfig.DIRECTION.IN;
    }


    @Override
    public Class<? extends Item> getItemType(String itemName) {
        return getBinding(itemName).itemType;
    }

}
