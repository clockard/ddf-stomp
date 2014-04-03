/**
 * Copyright (c) Codice Foundation
 * 
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 * 
 **/

package org.codice.pubsub.server;

import java.util.Timer;

import org.codice.pubsub.stomp.QueryAndSend;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.catalog.CatalogFramework;


/**
 * Runs a Subscription server to match subscriptions to incoming catalog data
 * @author damonsjones
 *
 */
public class SubscriptionServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionServer.class);
	private static final long POLL_TIME = 5000;
	private static final long TIME_DELAY = 0;
	
	public SubscriptionServer(BundleContext bundleContext, CatalogFramework catalogFramework, QueryAndSend queryAndSend){
		Timer timer = null;
		ConfigurationAdmin configAdmin = null;
    	
		//Fetch ConfigurationAdmin
    	ServiceReference caRef = bundleContext.getServiceReference(ConfigurationAdmin.class.getName());  
    	if (caRef != null) 
    	{  
    	    configAdmin = (ConfigurationAdmin)  bundleContext.getService(caRef);
    	} else{
    		LOGGER.error("Service reference to Configuration Admin is null");
    	}
    	
    	if (configAdmin != null){
    		SubscriptionProcessor processor = new SubscriptionProcessor(configAdmin, catalogFramework, queryAndSend);
	    	//Poll Catalog for results
	    	timer = new Timer();
	    	timer.schedule(processor, TIME_DELAY, POLL_TIME);
    	} else {
    		LOGGER.error("ConfigurationAdmin is null");
    	}
	}

}
