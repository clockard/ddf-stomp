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

import java.io.IOException;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang.StringUtils;
import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import org.fusesource.stomp.jms.message.StompJmsBytesMessage;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswConstants;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswException;
import org.codice.ddf.spatial.ogc.csw.catalog.endpoint.mappings.CswRecordMapperFilterVisitor;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.sort.SortOrder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.codice.pubsub.server.SubscriptionServer;

import ddf.catalog.CatalogFramework;
import ddf.catalog.filter.SortByImpl;
import ddf.catalog.operation.QueryImpl;


/**
 * Manages subscriptions and handles incoming STOMP queries.
 * @author damonsjones
 *
 */
public class SubscriptionQueryMessageListener implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionQueryMessageListener.class);
	private static final int NTHREDS = 1;
	private static final int NUM_QUERY_SEND_THREADS = 10;
    private static final boolean DEFAULT_IS_ENTERPRISE = false;
    private static final String SUBSCRIPTION_MAP = "SubscriptionMap";
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_DELETE = "DELETE";
    private static final String TTL_TYPE_MILLISECONDS = "MILLISECONDS";
    private static final String TTL_TYPE_SECONDS = "SECONDS";
    private static final String TTL_TYPE_MINUTES = "MINUTES";
    private static final String TTL_TYPE_HOURS = "HOURS";
    private static final String TTL_TYPE_MONTHS = "MONTHS";
    private static final String TTL_TYPE_YEARS = "YEARS";
    
    private String user = "admin";
    private String password = "password";
    private String stompHost;
    private int stompPort = 61613;
    private String destTopicName = null;
    private MessageConsumer consumer = null;
    private Connection connection = null;
    private Session session = null;
    private CatalogFramework catalogFramework = null;
    private BundleContext bundleContext = null;
    private Configuration subscriptionMap = null;
    private Map<String, Calendar> subscriptionTtlMap = null;
    private Timer timer = null;
    private QueryAndSend queryAndSend = null;
    
    public SubscriptionQueryMessageListener(CatalogFramework catalogFramework, BundleContext bundleContext, String stompHost, 
    		int stompPort, String destTopicName, QueryAndSend queryAndSend){
    	this.catalogFramework = catalogFramework;
    	this.bundleContext = bundleContext;
    	this.stompHost = stompHost;
    	this.stompPort = stompPort;
    	this.destTopicName = destTopicName;
    	this.queryAndSend = queryAndSend;
    	
    	//Map to keep track of subscription TTL values to be tracked
    	subscriptionTtlMap = new HashMap<String,Calendar>();
    	
    	//Fetch ConfigurationAdmin
    	ServiceReference caRef = bundleContext.getServiceReference(ConfigurationAdmin.class.getName());  
    	if (caRef != null) 
    	{  
    	    ConfigurationAdmin configAdmin = (ConfigurationAdmin)  bundleContext.getService(caRef);
    	    try {
				subscriptionMap = configAdmin.getConfiguration(SUBSCRIPTION_MAP);
			} catch (IOException e) {
				LOGGER.error(e.getMessage());
			}
    	}
    }
    
    @Override
    public void run() {
    	
    	//TTL Checker
    	timer = new Timer();
    	timer.schedule(new SubscriptionTtlWatcher(subscriptionTtlMap, this), 0, 60000);
    	
    	SubscriptionServer subSvr = new SubscriptionServer(bundleContext, catalogFramework, queryAndSend);
        Runnable task = subSvr;
        Thread worker = new Thread(task);
        worker.setName("SubscriptionServer");
        worker.start();
    	
    	execute();
    }
    
    public void initialize(){
    	
    	//Starts this class in a thread
    	ExecutorService executor = Executors.newFixedThreadPool(NTHREDS);
    	SubscriptionQueryMessageListener lsnr = new SubscriptionQueryMessageListener(catalogFramework, bundleContext, stompHost, 
        		stompPort, destTopicName, queryAndSend);
    	Runnable worker = lsnr;
    	executor.execute(worker);	
    }
    
    private void execute(){
    	
    	StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
    	
    	factory.setBrokerURI("tcp://" + stompHost + ":" + stompPort);
	   
	    try {
	    	connection = factory.createConnection(user, password);
			connection.start();
		    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		    Destination dest = new StompJmsDestination(destTopicName);
		    consumer = session.createConsumer(dest);
	
		    while(!Thread.currentThread().isInterrupted()) {
		        Message msg = consumer.receive();
		        if( msg instanceof  TextMessage ) {
		            String body = ((TextMessage) msg).getText();
		            processMessage(body);
	
		        } else if( msg instanceof StompJmsBytesMessage ) {
		        	//Non-ActiveMQ STOMP client implementations end up here
		        	StompJmsBytesMessage byteMsg = (StompJmsBytesMessage) msg;
									
					long len = ((StompJmsBytesMessage) msg).getBodyLength();
					
					byte[] byteArr = new byte[(int)byteMsg.getBodyLength()];
	
		            for (int i = 0; i < (int) byteMsg.getBodyLength(); i++) {
		                byteArr[i] = byteMsg.readByte();
		            }
		            String msgStr = new String(byteArr);   
		        	processMessage(msgStr);
				}			
				else {
		        }
		    }
		    
		    //stop the connection
		    destroy();
		} catch (JMSException e) {
			LOGGER.error(e.getMessage());
		}
    }
    
    private void processMessage(String msg){
    	SearchQueryMessage queryMsg = null;
    	try {
			 queryMsg = 
				    new ObjectMapper().readValue(msg, SearchQueryMessage.class);
			 
		} catch (IOException e) {
			LOGGER.error("Issues processing incoming query subscription: " + e.getMessage());
		}
    	String subscriptionId = queryMsg.getSubscriptionId();
    	String action = queryMsg.getAction();
    	
    	if (StringUtils.isNotEmpty(subscriptionId) && StringUtils.isNotEmpty(action)){
    		
    		if(action.equals(ACTION_CREATE)){
    			//Execute Query and Create a Subscription
    			
    			//If subscription already exists don't create a new one
    			Dictionary subMap = getSubscriptionMap();
    			
    			if (subMap.get(subscriptionId) == null){
    				boolean success = createSubscription(queryMsg);
         		         	
    			} else {
    				LOGGER.debug("Subscription: {} already exists", subscriptionId);
    			}
    				
    		} else if(action.equals(ACTION_DELETE)){
    			removeSubscription(subscriptionId);			

    		} else if(action.equals(ACTION_UPDATE)){
    			
    			removeSubscription(subscriptionId);
    			createSubscription(queryMsg);
    		}
    	}
    }
    
    public void removeSubscription(String subscriptionId){
		//Remove subscription from map
		removeSubscriptionFromMap(subscriptionId);
		subscriptionTtlMap.remove(subscriptionId);
		LOGGER.debug("Subscription Deleted: {}", subscriptionId);
    }
    
    public SortBy buildSortByMetacardIdPolicy() {
        String propertyName = "id";
        SortOrder sortOrder = SortOrder.ASCENDING;
        SortByImpl sortByImpl = new SortByImpl(propertyName, sortOrder);
        return sortByImpl;
    }
    
    private Dictionary getSubscriptionMap(){
        Dictionary props = subscriptionMap.getProperties();
        if (props == null) 
        {
           props = new Hashtable();
        }
        return props;
    }
    
    private void setSubscriptionMap(Dictionary props){
    	try {
			subscriptionMap.update(props);
		} catch (IOException e) {
			LOGGER.error(e.getMessage());
		}
    }
    
    private void removeSubscriptionFromMap(String subscriptionId){
    	Dictionary props = getSubscriptionMap();
    	props.remove(subscriptionId);
    	setSubscriptionMap(props);
    }
    
    private boolean createSubscription(SearchQueryMessage queryMsg){

    	boolean success = false;
    	
    	String subscriptionId = queryMsg.getSubscriptionId();
		
		//Build Query
    	String cqlText = queryMsg.getQueryString();
    	
    	if (StringUtils.isNotEmpty(cqlText)){
	    	 Filter filter = null;
	         try {
	             filter = CQL.toFilter(cqlText);
	         } catch (CQLException e) {
	                     LOGGER.error("Fatal error while trying to build CQL-based Filter from cqlText : "+ cqlText);
	         }   
	         
	         //Add CSW Record Mapper Filter Visitor for more CQL filtering options
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
		    	 //Set creation and last modified date times
		    	 Calendar now = Calendar.getInstance();
		    	 queryMsg.setCreationDate(now.getTimeInMillis());
		    	 queryMsg.setLastModifiedDate(now.getTimeInMillis());
		    	 
		     	//Starts this class in a thread
		     	ExecutorService executor = Executors.newFixedThreadPool(NUM_QUERY_SEND_THREADS);
		     	QueryAndSend qasInst = queryAndSend.newInstance();
		     	qasInst.setEnterprise(DEFAULT_IS_ENTERPRISE);
		     	qasInst.setFilter(filter);
		     	qasInst.setSubscriptionId(subscriptionId);
		     	Callable worker = qasInst;
		     	executor.submit(worker);	
		    	 
		    	//Add to Subscription Map
		    	ObjectMapper mapper = new ObjectMapper();
		    	String jsonMsg = null;
		    	try {
						jsonMsg = mapper.writeValueAsString(queryMsg);
					} catch (JsonProcessingException e) {
						LOGGER.error(e.getMessage());
					}
		    	
		    	LOGGER.debug("Store Subscription: " + jsonMsg);
		    	Dictionary subMap = getSubscriptionMap();
		    	subMap.put(subscriptionId, jsonMsg);
		    	setSubscriptionMap(subMap);         		         	
		    	
		    	//Set TTL (time to live) for subscription
		    	int ttlType = Calendar.MILLISECOND;
		        
		         String queryTtlType = queryMsg.getSubscriptionTtlType();
		        
		        //If Query TTL Type is null, assume Milliseconds
		        if (StringUtils.isBlank(queryTtlType)){
		        	LOGGER.debug("Query TTL Type is null");
		        	queryTtlType = TTL_TYPE_MILLISECONDS;
		        }
		        
		        if (queryTtlType.equals(TTL_TYPE_MILLISECONDS)){
		        	ttlType = Calendar.MILLISECOND;
		        } else if (queryTtlType.equals(TTL_TYPE_SECONDS)){
		        	ttlType = Calendar.SECOND;
		        } else if (queryTtlType.equals(TTL_TYPE_MINUTES)){
		        	ttlType = Calendar.MINUTE;
		        } else if (queryTtlType.equals(TTL_TYPE_HOURS)){
		        	ttlType = Calendar.HOUR;
		        } else if (queryTtlType.equals(TTL_TYPE_MONTHS)){
					ttlType = Calendar.MONTH;
				} else if (queryTtlType.equals(TTL_TYPE_YEARS)){
					ttlType = Calendar.YEAR;
				}
		        
	        	int queryTtl = queryMsg.getSubscriptionTtl();
	        	LOGGER.debug("Query TTL: {}", queryTtl);
	        	
		        if (queryTtl == -9 || queryTtl == 0){
		        	//No TTL chosen; make it close to forever
		        	queryTtl = 9999;
		        	ttlType = Calendar.YEAR;
		        }
		        
		        //Set TTL from creation time to TTL specified
		        long creationTime = queryMsg.getCreationDate();
		        Calendar ttl = Calendar.getInstance();
		        ttl.setTimeInMillis(creationTime);
		        ttl.add(ttlType, queryTtl);
		        subscriptionTtlMap.put(subscriptionId, ttl);
		        
		        success = true;
		         
	    	} else {
	    		LOGGER.trace("Catalog Framework or filter is NULL");
	    	}
    	} else{
    		LOGGER.debug("Subscription ID is null: Subscription ID= {}", subscriptionId);
    	}	
    	return success;
    }
    
    public void destroy(){
    	try {
			consumer.close();
			session.close();
			connection.stop();
			connection.close();
			consumer = null;
			session = null;
			connection = null;
			timer.cancel();
			timer.purge();
		} catch (JMSException e) {
			LOGGER.error(e.getMessage());
		}
    }
}