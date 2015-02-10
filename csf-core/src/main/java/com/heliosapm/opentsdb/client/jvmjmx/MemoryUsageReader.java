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

package com.heliosapm.opentsdb.client.jvmjmx;

import java.lang.management.MemoryUsage;

/**
 * <p>Title: MemoryUsageReader</p>
 * <p>Description: Defines a {@link MemoryUsage} reader</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MemoryUsageReader</code></p>
 */

public interface MemoryUsageReader {
	/**
	 * Reads the specific memory value from the passed memory usage
	 * @param memoryUsage The memory usage to read from
	 * @return the read value
	 */
	public long get(MemoryUsage memoryUsage);
}
