package org.openhab.binding.smartbee;

import java.util.List;

import org.openhab.binding.smartbee.internal.pin.SmartBeePin;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.items.Item;
import org.openhab.core.types.State;

public interface SmartBeeBindingProvider extends BindingProvider {

    List<Class<? extends State>> getAvailableItemTypes(String itemName);

    Class<? extends Item> getItemType(String itemName);
    
    String getAddress(String itemName);

    SmartBeePin getPin(String itemName);

    String getTransformation(String itemName);

    boolean isSensor(String itemName);

}
