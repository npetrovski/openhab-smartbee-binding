package org.openhab.binding.smartbee;

import java.util.Collection;

import org.openhab.binding.smartbee.internal.SmartBeePin;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.items.Item;

public interface SmartBeeBindingProvider extends BindingProvider {

    Class<? extends Item> getItemType(String itemName);
    
    String getAddress(String itemName);

    SmartBeePin getPin(String itemName);

    String getTransformation(String itemName);

    boolean isSensor(String itemName);
    
    public Collection<String> getItemsByAddress(final String addr);
 
}
