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
package com.heliosapm.opentsdb.client.aop.naming;




/**
 * <p>Title: MetricNameProvider</p>
 * <p>Description: A compiled {@link com.heliosapm.opentsdb.client.aop.ShorthandScript} metric template translator to produce runtime metric names</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.naming.MetricNameProvider</code></p>
 */
public interface MetricNameProvider {
//	/**
//	 * Determines the method interception metric name as specified by the related {@link com.heliosapm.shorthand.instrumentor.shorthand.ShorthandScript}.
//	 * The two parameters are the only values that might be used to qualify a metric name at runtime.
//	 * All other metric name fragments should be determined at compile time
//	 * @param returnValue The return value of the method invocation
//	 * @param methodArgs The arguments to the method invocation
//	 * @return the metric name
//	 */
//	public String getMetricName(Object returnValue, Object...methodArgs);
	
	/**
	 * Returns the pre-compiled metric name for which the the related {@link com.heliosapm.shorthand.instrumentor.shorthand.ShorthandScript}
	 * had no runtime dependencies 
	 * @return the metric name
	 */
	public String getMetricName();
}
