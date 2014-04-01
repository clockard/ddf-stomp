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

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.log4j.Logger;
import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;


/**
 * Publishes messages to STOMP.
 * @author damonsjones
 *
 */
public class StompPublisher {
    private static Logger LOGGER = Logger.getLogger(StompPublisher.class);
    String user;
    String password;
    String stompHost;
    int stompPort;
    String subscribeTopicName;
    String destination;
    
    public StompPublisher(String stompHost, int stompPort, String subscribeTopicName){
    	this.stompHost = stompHost;
    	this.stompPort = stompPort;
    	this.subscribeTopicName = subscribeTopicName;
    }
    
	public void publish(String subscriptionId, String jsonMsg){
    	user =  "admin";
    	password = "password";
    	destination = subscribeTopicName + subscriptionId;
    	
    	StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
        factory.setBrokerURI("tcp://" + stompHost + ":" + stompPort);

        
		try {
			Connection connection = factory.createConnection(user, password);
		
	        connection.start();
	        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
	        Destination dest = new StompJmsDestination(destination);
	        MessageProducer producer = session.createProducer(dest);
	        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
	        TextMessage msg = session.createTextMessage(jsonMsg);
	        producer.send(msg);
	        connection.close();
		} catch (JMSException e) {
			LOGGER.error("Caught exception while publishing subscription query result. ", e);
		} 
	        
	}
}
