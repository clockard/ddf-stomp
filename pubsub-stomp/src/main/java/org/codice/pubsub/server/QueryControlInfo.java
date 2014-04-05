package org.codice.pubsub.server;

import org.joda.time.DateTime;

public class QueryControlInfo {
	private long timeTaken;
	private DateTime queryEndDateTime;
	private String subscriptionId;
	
	public long getTimeTaken() {
		return timeTaken;
	}
	public void setTimeTaken(long timeTaken) {
		this.timeTaken = timeTaken;
	}
	public DateTime getQueryEndDateTime() {
		return queryEndDateTime;
	}
	public void setQueryEndDateTime(DateTime queryEndDateTime) {
		this.queryEndDateTime = queryEndDateTime;
	}
	public String getSubscriptionId() {
		return subscriptionId;
	}
	public void setSubscriptionId(String subscriptionId) {
		this.subscriptionId = subscriptionId;
	}
	
	
}
