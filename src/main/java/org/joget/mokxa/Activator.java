package org.joget.mokxa;

import java.util.ArrayList;
import java.util.Collection;

import org.joget.mokxa.plugin.ClientImportProcessTool;
import org.joget.mokxa.processor.ClientImportProcessor;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(ClientImportProcessTool.class.getName(), new ClientImportProcessTool(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}