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
package com.heliosapm.opentsdb.client.aop;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.LongBuffer;

/**
 * <p>Title: ThreadMetricReader</p>
 * <p>Description: Defines a measurement taken from the execution of a defined progression of code</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ThreadMetricReader</code></p>
 */

public interface ThreadMetricReader {

	/**
	 * Pre call to the reader. The reader should place it's initing value in it's designated slot.
	 * @param values The values buffer. At the begining of the call it will contain undefined data. It should be overlayed
	 * by the initial value. 
	 */
	public void pre(final LongBuffer values);

	
	/**
	 * Post call to the reader. The reader should place it's captured value in it's designated slot.
	 * @param values The values buffer. At the begining of the call it will contain the starting value (if applicable). It should be overlayed
	 * by the value emitted for collection
	 */
	public void post(final LongBuffer values);
	
	
}
