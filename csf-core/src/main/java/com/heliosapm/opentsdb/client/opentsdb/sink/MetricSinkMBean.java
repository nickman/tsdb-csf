/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.heliosapm.opentsdb.client.opentsdb.sink;

import javax.management.ObjectName;

import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MetricSinkMBean</p>
 * <p>Description: JMX MBean interface for the {@link MetricSink}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.sink.MetricSinkMBean</code></p>
 */

public interface MetricSinkMBean {
	
	/** The MetricSink JMX ObjectName */
	public static final ObjectName OBJECT_NAME = Util.objectName(Util.getJMXDomain() + ":service=MetricSink");
	
	/**
	 * Returns the number of processed submissions
	 * @return the number of processed submissions
	 */
	public long getProcessedArrays();
	
	
	/**
	 * Returns the number of submissions in the input queue
	 * @return the number of submissions in the input queue
	 */
	public int getInputQueueDepth();
	
	/**
	 * Returns the number of free slots in the input queue
	 * @return the number of free slots in the input queue
	 */
	public int getInputQueueFree();
	
	/**
	 * Returns the number of dropped input items due to a full input queue
	 * @return the number of dropped input items
	 */
	public long getInputQueueDropCount();

}
