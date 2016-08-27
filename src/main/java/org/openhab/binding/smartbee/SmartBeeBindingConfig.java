package org.openhab.binding.smartbee;

import java.util.List;
import org.openhab.binding.smartbee.internal.SmartBeePin;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.types.State;

public class SmartBeeBindingConfig implements BindingConfig {

    public enum DIRECTION {

        /**
         * binding for sensors
         */
        IN,
        /**
         * binding for actors
         */
        OUT
    };

    public DIRECTION direction;

    public String address;

    public SmartBeePin pin;

    public String transformation;

    public List<Class<? extends State>> types;

    public Class<? extends Item> itemType;
}
