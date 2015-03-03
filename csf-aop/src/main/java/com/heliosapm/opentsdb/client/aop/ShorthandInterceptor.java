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


/**
 * <p>Title: ShorthandInterceptor</p>
 * <p>Description: Defines the externalized interface exposed for the interceptor injected into instrumented members.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ShorthandInterceptor</code></p>
 */

public interface ShorthandInterceptor {
	

	/**
	 * Entry point for the interceptor
//	 * @param mask The enabled measurement bit mask
//	 * @param parentMetricId The long hash code of the OTMetric associated to this interceptor
	 * @return the captured entry state for the interceptor
	 */
	public long[] enter(/* int mask, long parentMetricId */);
	
	/**
	 * Exit point for the interceptor
	 * @param entryState The state captured in the entry call
	 */
	public void exit(long[] entryState);
	
	/**
	 * The finally block exit for the interceptor 
	 */
	public void finalExit();
	
	/**
	 * The catch block exit for the interceptor 
	 * @param t The caught exception that will be rethrown
	 */
	public void throwExit(Throwable t);
	
}
