package org.openhab.binding.smartbee.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension of the default OSGi bundle activator
 *
 */
public final class SmartBeeActivator implements BundleActivator {

    private static Logger LOG = LoggerFactory.getLogger(SmartBeeActivator.class);

    private static BundleContext context;

    static BundleContext getContext() {
        return context;
    }

    /**
     * Called whenever the OSGi framework starts our bundle
     */
    @Override
    public void start(BundleContext bc) throws Exception {
        SmartBeeActivator.context = bc;
        LOG.debug("XBee binding has been started.");
    }

    /**
     * Called whenever the OSGi framework stops our bundle
     */
    @Override
    public void stop(BundleContext bc) throws Exception {
        SmartBeeActivator.context = null;
        LOG.debug("XBee binding has been stopped.");
    }

}
