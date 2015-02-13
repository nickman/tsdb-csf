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
package com.heliosapm.opentsdb.client.collector;

/**
 * <p>Title: Measurement</p>
 * <p>Description: Functional enumeration of thread execution metrics that can be collected</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.collector.Measurement</code></p>
 */

public enum Measurement {
	/** Elapsed time in ns.  */
	ELAPSED,
	/** Elapsed CPU time in ns. */
	CPU,
	/** Elapsed User Mode CPU time in ns. */
	UCPU,
	/** Number of thread waits */	
	WAIT,
	/** Number of thread blocks */
	BLOCK,
	/** Thread wait time in ms. */
	WAITTIME,
	/** Thread block time in ms. */
	BLOCKTIME,
	/** Concurrent threads with entry/exit block */
	CONCURRENT,
	/** Total invocation count */
	COUNT,
	/** Total return count */
	RETURN,
	/** Total exception count */
	ERROR;
	
	
}
