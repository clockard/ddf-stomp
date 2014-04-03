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

import org.apache.log4j.Logger;
import org.codice.pubsub.server.SubscriptionProcessor;
import org.joda.time.DateTime;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.sort.SortOrder;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.SortByImpl;
import ddf.catalog.operation.QueryImpl;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.QueryRequestImpl;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.transform.CatalogTransformerException;

/**
 * Executes queries on the catalog and returned results are sent to the STOMP publisher.
 * @author damonsjones
 *
 */
public class QueryAndSend implements Runnable{
	private static final String TRANSFORMER_ID = "geojson";
	private static final int DEFAULT_START_INDEX = 1;
    private static final boolean DEFAULT_REQUESTS_TOTAL_RESULT_COUNT = false;
	private CatalogFramework catalogFramework = null;
	private static Logger LOGGER = Logger.getLogger(QueryAndSend.class);
    private String stompHost;
    private int stompPort;
    private int defaultMaxResults;
    private int defaultRequestTimeout;
    private String subscribeTopicName;
    private Filter filter;
    private boolean isEnterprise = false;
    private String subscriptionId = null;
    private SubscriptionProcessor processor = null;

	public QueryAndSend(CatalogFramework catalogFramework, String stompHost, int stompPort, String subscribeTopicName, 
			int defaultMaxResults, int defaultRequestTimeout) {
		this.catalogFramework = catalogFramework;
		this.stompHost = stompHost;
		this.stompPort = stompPort;
		this.subscribeTopicName = subscribeTopicName;
	}

	private void execute() {
		 
		SortBy sortPolicy = buildSortByMetacardIdPolicy();
		 QueryImpl queryImpl = new QueryImpl(filter, DEFAULT_START_INDEX, defaultMaxResults, sortPolicy,
				 DEFAULT_REQUESTS_TOTAL_RESULT_COUNT, defaultRequestTimeout);
     
		final QueryRequest queryRequest = new QueryRequestImpl(queryImpl);
		QueryResponse queryResponse;
		try {
			queryResponse = catalogFramework.query(queryRequest);
			processor.setTimestamp(new DateTime());

			// Transform metacards into geojson format
			BinaryContent content = null;
			
			if (queryResponse.getHits() > 0){
				LOGGER.debug("Results returned from query: {}" + queryResponse.getHits());
				content = catalogFramework.transform(queryResponse, TRANSFORMER_ID,
						null);
				String jsonText = new String(content.getByteArray());
				
				// Send query results to STOMP topic
				StompPublisher publisher = new StompPublisher(stompHost, stompPort, subscribeTopicName);
				publisher.publish(subscriptionId, jsonText);
				
			} else {
				LOGGER.debug("No Results returned");
			}
			
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace(String.format(
						"Begin publishing results from query %s on topic",
						subscriptionId));
			}
		} catch (UnsupportedQueryException e) {
			LOGGER.error("Caught exception while transforming metacard. ", e);
		} catch (IOException e) {
			LOGGER.error("Caught exception while transforming metacard. ", e);
		} catch (SourceUnavailableException e) {
			LOGGER.error("Caught exception while transforming metacard. ", e);
		} catch (FederationException e) {
			LOGGER.error("Caught exception while transforming metacard. ", e);
		} catch (CatalogTransformerException e) {
			LOGGER.error("Caught exception while transforming metacard. ", e);
		}

	}
	
	public SortBy buildSortByMetacardIdPolicy() {
	        String propertyName = "id";
	        SortOrder sortOrder = SortOrder.ASCENDING;
	        SortByImpl sortByImpl = new SortByImpl(propertyName, sortOrder);
	        return sortByImpl;
	 }

	@Override
	public void run() {
		execute();
		
	}

	public Filter getFilter() {
		return filter;
	}

	public void setFilter(Filter filter) {
		this.filter = filter;
	}

	public boolean isEnterprise() {
		return isEnterprise;
	}

	public void setEnterprise(boolean isEnterprise) {
		this.isEnterprise = isEnterprise;
	}

	public String getSubscriptionId() {
		return subscriptionId;
	}

	public void setSubscriptionId(String subscriptionId) {
		this.subscriptionId = subscriptionId;
	}

	public SubscriptionProcessor getProcessor() {
		return processor;
	}

	public void setProcessor(SubscriptionProcessor processor) {
		this.processor = processor;
	}
	
}
