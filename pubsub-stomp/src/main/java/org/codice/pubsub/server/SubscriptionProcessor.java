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
import java.util.Hashtable;
import java.util.TimerTask;

import org.apache.commons.lang.StringUtils;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswConstants;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswException;
import org.codice.ddf.spatial.ogc.csw.catalog.endpoint.mappings.CswRecordMapperFilterVisitor;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.sort.SortOrder;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.codice.pubsub.stomp.QueryExecutor;
import org.codice.pubsub.stomp.SearchQueryMessage;

import ddf.catalog.CatalogFramework;
import ddf.catalog.filter.SortByImpl;
import ddf.catalog.operation.QueryImpl;


/**
 * Processes incoming catalog content against existing subscriptions and pushes out events.
 * 
 * @author damonsjones
 * 
 *
 */
public class SubscriptionProcessor extends TimerTask {
	private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionProcessor.class);
	private static final int DEFAULT_START_INDEX = 1;
    private static final boolean DEFAULT_IS_ENTERPRISE = false;
    private static final boolean DEFAULT_REQUESTS_TOTAL_RESULT_COUNT = false;
    private static final String DATE_FILTER = " AND modified AFTER ";
	private static final String SUBSCRIPTION_MAP = "SubscriptionMap";
    private String stompHost;
    private int stompPort = 61613;
    private String subscribeTopicName = null;
    private int defaultMaxResults = 1000;
    private int defaultRequestTimeout = 30000;
	ConfigurationAdmin configAdmin = null;
	private Configuration subscriptionMap = null;
	private CatalogFramework catalogFramework;
	private DateTime timestamp = null;
	
	public SubscriptionProcessor( ConfigurationAdmin configAdmin, CatalogFramework catalogFramework, String stompHost, 
    		int stompPort, int defaultMaxResults, int defaultRequestTimeout, String subscribeTopicName){
		this.configAdmin = configAdmin;
		this.catalogFramework = catalogFramework;
    	this.stompHost = stompHost;
    	this.stompPort = stompPort;
    	this.defaultMaxResults = defaultMaxResults;
    	this.defaultRequestTimeout = defaultRequestTimeout;
    	this.subscribeTopicName = subscribeTopicName;
    	
    	timestamp = new DateTime();
	}

	@Override
	public void run() {
		LOGGER.debug("Previous Timestamp: {}", timestamp.toString());
		processSubscriptions();
		timestamp = new DateTime();
		LOGGER.debug("Current Timestamp: {}", timestamp.toString());
	}
	
	
	private void processSubscriptions(){
		//Fetch Subscriptions
	    try {
			subscriptionMap = configAdmin.getConfiguration(SUBSCRIPTION_MAP);
		} catch (IOException e) {
			LOGGER.error(e.getMessage());
		}
	    
        Dictionary subMap = subscriptionMap.getProperties();
        if (subMap == null) 
        {
           subMap = new Hashtable();
        }
        
        //Loop through each subscription and poll catalog based on subscription details
        if (subMap != null){
	    	Enumeration e = subMap.keys();
	    	while(e.hasMoreElements()){
	    		String key = (String) e.nextElement();
	    		String msg = (String)subMap.get(key);

	    		if(! key.equals("service.pid")){
	    	    	SearchQueryMessage queryMsg = null;
	    	    	try {
	    				 queryMsg = new ObjectMapper().readValue(msg, SearchQueryMessage.class);
	    				 
	    				 //Set date / time filter to only get results from last time processor polled
	    				 String origQueryMsg = queryMsg.getQueryString();
	    				 DateTimeFormatter fmt2 = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
	    				 LOGGER.debug("String to be processed: {}", origQueryMsg + DATE_FILTER + fmt2.print(timestamp));
	    				 queryMsg.setQueryString(origQueryMsg + DATE_FILTER + fmt2.print(timestamp));
	   
	    				 pollCatalog(queryMsg);
	    				 
	    				 
	    			} catch (IOException ex) {
	    				LOGGER.error("Issues processing incoming query subscription: " + ex.getMessage());
	    			}
	    		}
	    	}
    	} 
	}
	
	private void pollCatalog(SearchQueryMessage queryMsg){
		//Build Query
    	String cqlText = queryMsg.getQueryString();
    	
    	if (StringUtils.isNotEmpty(cqlText)){
	    	 Filter filter = null;
	         try {
	             filter = CQL.toFilter(cqlText);
	             LOGGER.debug("CQL sets filter: {}", filter.toString());
	         } catch (CQLException e) {
	        	 LOGGER.error("Fatal error while trying to build CQL-based Filter from cqlText : "+ cqlText);
	         }   
	         
	         // Run through CSW filter as it has better CQL support
	        try {
	            FilterVisitor f = new CswRecordMapperFilterVisitor();
	            filter = (Filter) filter.accept(f, null);
	        } catch(UnsupportedOperationException ose) {
	            try {
					throw new CswException(ose.getMessage(), CswConstants.INVALID_PARAMETER_VALUE, null);
				} catch (CswException e) {
					LOGGER.error(e.getMessage());
				}
	        }
	         
	    	if (catalogFramework != null && filter != null){
	    		LOGGER.trace("Catalog Frameowork: " + catalogFramework.getVersion());                                                                                                                                
		        	 
	        	 //Execute Query & Send Results To Topic
	        	 SortBy sortPolicy = buildSortByMetacardIdPolicy();
		         QueryImpl queryImpl = new QueryImpl(filter, DEFAULT_START_INDEX, defaultMaxResults, sortPolicy,
		        		 DEFAULT_REQUESTS_TOTAL_RESULT_COUNT, defaultRequestTimeout);
	        	 QueryExecutor qExec = new QueryExecutor(catalogFramework, stompHost, stompPort, subscribeTopicName);
	        	 qExec.execute(queryImpl, DEFAULT_IS_ENTERPRISE, queryMsg.getSubscriptionId());
	    	}
    	}
	}
	
    public SortBy buildSortByMetacardIdPolicy() {
        String propertyName = "id";
        SortOrder sortOrder = SortOrder.ASCENDING;
        SortByImpl sortByImpl = new SortByImpl(propertyName, sortOrder);
        return sortByImpl;
    }
}
