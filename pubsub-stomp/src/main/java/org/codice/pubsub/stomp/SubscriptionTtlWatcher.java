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

package org.codice.pubsub.stomp;

import java.util.Calendar;
import java.util.Map;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionTtlWatcher extends TimerTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionTtlWatcher.class);
	private Map<String, Calendar> ttlMap = null;
	private SubscriptionQueryMessageListener listener = null;
	
	public SubscriptionTtlWatcher(Map<String, Calendar> ttlMap, SubscriptionQueryMessageListener listener){
		this.ttlMap = ttlMap;
		this.listener = listener;
	}
	
	public void run(){
		for (Map.Entry<String, Calendar> entry : ttlMap.entrySet()) {
			String subscriptionId = entry.getKey();
			LOGGER.debug("SubscriptionId : " + entry.getKey());			
			Calendar cal = ttlMap.get(entry.getKey());
			Calendar now = Calendar.getInstance();
			if (now.after(cal)){
				LOGGER.debug("{}: EXPIRED - {}", entry.getKey(), cal.toString());
				listener.removeSubscription(subscriptionId);
			}
		}
	}
}
