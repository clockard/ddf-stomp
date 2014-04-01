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

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.federation.FederationException;
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
public class QueryExecutor {
	private static final String TRANSFORMER_ID = "geojson";
	private CatalogFramework catalogFramework = null;
	private static Logger LOGGER = Logger.getLogger(QueryExecutor.class);
    private String stompHost;
    private int stompPort;
    private String subscribeTopicName;

	public QueryExecutor(CatalogFramework catalogFramework, String stompHost, int stompPort, String subscribeTopicName) {
		this.catalogFramework = catalogFramework;
		this.stompHost = stompHost;
		this.stompPort = stompPort;
		this.subscribeTopicName = subscribeTopicName;
	}

	public void execute(QueryImpl query, boolean isEnterprise,
			String subscriptionId) {
		final QueryRequest queryRequest = new QueryRequestImpl(query);
		QueryResponse queryResponse;
		try {
			queryResponse = catalogFramework.query(queryRequest);

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
}
