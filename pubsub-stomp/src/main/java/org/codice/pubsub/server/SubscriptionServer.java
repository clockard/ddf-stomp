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

import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang.StringUtils;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswConstants;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswException;
import org.codice.ddf.spatial.ogc.csw.catalog.endpoint.mappings.CswRecordMapperFilterVisitor;
import org.codice.pubsub.stomp.QueryAndSend;
import org.codice.pubsub.stomp.SearchQueryMessage;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import ddf.catalog.CatalogFramework;


/**
 * Runs a Subscription server to match subscriptions to incoming catalog data
 * @author damonsjones
 *
 */
public class SubscriptionServer implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionServer.class);
	private static final String SUBSCRIPTION_MAP = "SubscriptionMap";
	private static final String DATE_FILTER = " AND modified AFTER ";
	private static final int PROCESS_STATUS_RUNNING = 0;
	private static final int PROCESS_STATUS_NOT_EXIST = 1;
	private static final int PROCESS_STATUS_COMPLETE = 2;
	private static final int NUM_QUERY_SEND_THREADS = 10;
	private static final boolean DEFAULT_IS_ENTERPRISE = false;
	private BundleContext bundleContext = null;
	private CatalogFramework catalogFramework = null;
	private QueryAndSend queryAndSend = null;
	private HashMap<String, Future<QueryControlInfo>> processMap = null;
	
	public SubscriptionServer(BundleContext bundleContext, CatalogFramework catalogFramework, QueryAndSend queryAndSend){
		this.bundleContext = bundleContext;
		this.catalogFramework = catalogFramework;
		this.queryAndSend = queryAndSend;
		
		processMap = new HashMap<String, Future<QueryControlInfo>>();
	}
	
	private void processSubscriptions(){
		while(!Thread.currentThread().isInterrupted()){
			//Fetch Subscriptions
			Dictionary subMap = getSubscriptionMap();
			if (subMap != null){
		    	Enumeration e = subMap.keys();
		    	while(e.hasMoreElements()){
		    		String subscriptionId = (String) e.nextElement();
		    		if (!subscriptionId.equals("service.pid")){
			    		String subscriptionMsg = (String)subMap.get(subscriptionId);
			    		int status = checkProcessingStatus(subscriptionId);
			    		
			    		if(status == PROCESS_STATUS_COMPLETE){
			    			Future<QueryControlInfo> future = processMap.get(subscriptionId);
			    			if(future != null){
			    				boolean done = future.isDone();
			    				if(done){
			    					try{
			    						QueryControlInfo ctrlInfo  = future.get();
			    						processMap.remove(subscriptionId);
			    						runQuery(subscriptionMsg, ctrlInfo.getQueryEndDateTime());
			    					} catch (InterruptedException ie) {
			    						LOGGER.error(ie.getMessage());
			    			       
			    		            } catch (ExecutionException ee) {
			    		            	LOGGER.error(ee.getMessage());
			    		            }
			    				}
			    			}
			    		} else if(status == PROCESS_STATUS_NOT_EXIST){
			    			runQuery(subscriptionMsg, new DateTime());
			    		
			    		}  else if(status == PROCESS_STATUS_NOT_EXIST){
			    			
			    			//Do Nothing For Now
			    		}
		    		}
		    	}
	        }
		}
	}
	
	public int checkProcessingStatus(String subscriptionId){
		
		Future<QueryControlInfo> future = processMap.get(subscriptionId);
		
		if (future != null){
			boolean done = future.isDone();
			if (done){
				return PROCESS_STATUS_COMPLETE;
			} else {
				return PROCESS_STATUS_RUNNING;
			}
		} else {
			return PROCESS_STATUS_NOT_EXIST;
		}		
	}
	
	public Dictionary getSubscriptionMap(){
		Dictionary subMap = null;
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
    		Configuration subscriptionMap = null;
    		//Fetch Subscriptions
    	    try {
    			subscriptionMap = configAdmin.getConfiguration(SUBSCRIPTION_MAP);
    		} catch (IOException e) {
    			LOGGER.error(e.getMessage());
    		}
    	    
           subMap = subscriptionMap.getProperties();
            
    	} else {
    		LOGGER.error("ConfigurationAdmin is null");
    	}
    	return subMap;
	}
	
	public boolean subscriptionExists(String subscriptionId){
		Dictionary subMap = getSubscriptionMap();
		String msg = (String)subMap.get(subscriptionId);
		if (msg == null){
			return false;
		} else {
			return true;
		}
	}
	
	public String getSubscriptionMsg(String subscriptionId){
		Dictionary subMap = getSubscriptionMap();
		String msg = (String)subMap.get(subscriptionId);
		return msg;
	}
	
	public void runQuery(String msg, DateTime timestamp){
			SearchQueryMessage queryMsg = null;
	    	try {
				 queryMsg = new ObjectMapper().readValue(msg, SearchQueryMessage.class);
				 
				 //Set date / time filter to only get results from last time processor polled
				 String origQueryMsg = queryMsg.getQueryString();
				 DateTimeFormatter fmt2 = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
				 LOGGER.debug("String to be processed: {}", origQueryMsg + DATE_FILTER + fmt2.print(timestamp));
				 queryMsg.setQueryString(origQueryMsg + DATE_FILTER + fmt2.print(timestamp));

				//Build Query
			    	String cqlText = queryMsg.getQueryString();
			    	
			    	if (StringUtils.isNotEmpty(cqlText)){
				    	 Filter filter = null;
				         try {
				             filter = CQL.toFilter(cqlText);
				             LOGGER.debug("CQL sets filter: {}", filter.toString());
				         } catch (CQLException ce) {
				        	 LOGGER.error("Fatal error while trying to build CQL-based Filter from cqlText : "+ cqlText);
				         }   
				         
				         // Run through CSW filter as it has better CQL support
				        try {
				            FilterVisitor f = new CswRecordMapperFilterVisitor();
				            filter = (Filter) filter.accept(f, null);
				        } catch(UnsupportedOperationException ose) {
				            try {
								throw new CswException(ose.getMessage(), CswConstants.INVALID_PARAMETER_VALUE, null);
							} catch (CswException cwe) {
								LOGGER.error(cwe.getMessage());
							}
				        }
				         
				    	if (catalogFramework != null && filter != null){
				    		LOGGER.trace("Catalog Frameowork: " + catalogFramework.getVersion());                                                                                                                                
				    		//Starts QueryAndSend in a thread
				    		ExecutorService executor = Executors.newFixedThreadPool(NUM_QUERY_SEND_THREADS);
				    		QueryAndSend qasInst = queryAndSend.newInstance();
				    		qasInst.setEnterprise(DEFAULT_IS_ENTERPRISE);
				    		qasInst.setFilter(filter);
				    		qasInst.setSubscriptionId(queryMsg.getSubscriptionId());
				    		Callable<QueryControlInfo> worker = qasInst;
				    	    Future<QueryControlInfo> ctrlInfo = executor.submit(worker);
				    	    processMap.put(queryMsg.getSubscriptionId(), ctrlInfo);	
				    	}
			    	}
				 
				 
			} catch (IOException ex) {
				LOGGER.error("Issues processing incoming query subscription: " + ex.getMessage());
				ex.printStackTrace();
			}
	}
	
	@Override
	public void run() {
		processSubscriptions();
		
	}
}
