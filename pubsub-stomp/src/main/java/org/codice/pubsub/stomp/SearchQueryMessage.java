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

public class SearchQueryMessage {
	private String subscriptionId;
	private String action;
	private String subscriptionTtlType;
	private int subscriptionTtl;
	private long creationDate;
	private long lastModifiedDate;
	private String queryString;
	private String[] sources;
	
	public String getSubscriptionId() {
		return subscriptionId;
	}
	public void setSubscriptionId(String subscriptionId) {
		this.subscriptionId = subscriptionId;
	}
	public int getSubscriptionTtl() {
		return subscriptionTtl;
	}
	public void setSubscriptionTtl(int subscriptionTtl) {
		this.subscriptionTtl = subscriptionTtl;
	}
	public String[] getSources() {
		return sources;
	}
	public void setSources(String[] sources) {
		this.sources = sources;
	}
	public String getQueryString() {
		return queryString;
	}
	public void setQueryString(String queryString) {
		this.queryString = queryString;
	}
	public String getAction() {
		return action;
	}
	public void setAction(String action) {
		this.action = action;
	}
	public String getSubscriptionTtlType() {
		return subscriptionTtlType;
	}
	public void setSubscriptionTtlType(String subscriptionTtlType) {
		this.subscriptionTtlType = subscriptionTtlType;
	}
	public long getCreationDate() {
		return creationDate;
	}
	public void setCreationDate(long creationDate) {
		this.creationDate = creationDate;
	}
	public long getLastModifiedDate() {
		return lastModifiedDate;
	}
	public void setLastModifiedDate(long lastModifiedDate) {
		this.lastModifiedDate = lastModifiedDate;
	}
}
