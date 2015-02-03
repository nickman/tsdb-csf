/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.opentsdb.client.opentsdb;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.util.Timeout;

import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.name.AgentNameChangeListener;

/**
 * <p>Title: Heartbeat</p>
 * <p>Description: Issues a heartbeat metric on a fixed schedule</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.Heartbeat</code></p>
 * TODO: provide interface that can supply the value to send.
 */

public class Heartbeat implements Closeable, Runnable, AgentNameChangeListener {
	/** The metric name for the submission */
	protected final String metricName;
	/** The period for the submission */
	protected int period;	
	/** The schedule handle */
	protected Timeout timeout = null;
	/** The agent name tags */
	protected final Map<String, String> tags = new TreeMap<String, String>();
	/** The value to send in heartbeat metrics */
	protected int value;
	/** The current OTMetric */
	protected OTMetric otMetric = null;
	
	
	
	
	/**
	 * Creates a new Heartbeat
	 * @param metricName The metric name for the submission
	 * @param period The period for the submission
	 */
	public Heartbeat(final String metricName, final int period) {
		this.metricName = metricName;
		this.period = period;
		AgentName.getInstance().addAgentNameChangeListener(this);
		tags.put(Constants.APP_TAG, AgentName.getInstance().getAppName());
		tags.put(Constants.HOST_TAG, AgentName.getInstance().getHostName());
		value = ConfigurationReader.confInt(Constants.PROP_HEARTBEAT_VALUE, Constants.DEFAULT_HEARTBEAT_VALUE);
		otMetric = OTMetricCache.getInstance().getOTMetric(metricName, null, null, tags);
	}
	
	/**
	 * Starts the heartbeat
	 * @return this heartbeat
	 */
	public Heartbeat start() {
		if(timeout==null) {
			timeout = Threading.getInstance().schedule(this, period, TimeUnit.SECONDS);
		}
		return this;
	}
	
	/**
	 * Stops the heartbeat
	 * @return this heartbeat
	 */
	public Heartbeat stop() {
		if(timeout!=null) {
			timeout.cancel();
		}
		return this;
	}
	
	/**
	 * Sets a new value to be sent in the heartbeat metric
	 * @param value the new value
	 * @return this heartbeat
	 */
	public Heartbeat setValue(final int value) {
		this.value = value;
		return this;
	}


	/**
	 * {@inheritDoc}
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		if(timeout!=null) try { timeout.cancel(); } catch (Exception x) {/* No Op */}
		AgentName.getInstance().removeAgentNameChangeListener(this);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.name.AgentNameChangeListener#onAgentNameChange(java.lang.String, java.lang.String)
	 */
	@Override
	public void onAgentNameChange(final String app, final String host) {
		synchronized(tags) {
			if(app!=null) {
				tags.put(Constants.APP_TAG, AgentName.getInstance().getAppName());
			}
			if(host!=null) {
				tags.put(Constants.HOST_TAG, AgentName.getInstance().getHostName());
			}
		}
		otMetric = OTMetricCache.getInstance().getOTMetric(metricName, null, null, tags);
	}
	

	/**
	 * {@inheritDoc}
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		MetricBuilder.trace(otMetric, value);
	}

}
