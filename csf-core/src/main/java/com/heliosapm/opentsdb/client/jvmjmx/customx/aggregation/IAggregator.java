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
package com.heliosapm.opentsdb.client.jvmjmx.customx.aggregation;

import java.util.List;

/**
 * <p>Title: IAggregator</p>
 * <p>Description: Defines an aggregation function that accepts a list of objects and returns an object representing the aggregate.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.aggregation.IAggregator</code></p>
 */
public interface IAggregator {
	/**
	 * Computes an aggregate of the passed items
	 * @param items The items to compute an aggregate for
	 * @return The aggregate result
	 */
	public Object aggregate(List<Object> items);
	
	/**
	 * Aggregates a long array
	 * @param items The array of longs to aggregate
	 * @return the aggregated long value
	 */
	public long aggregate(long[] items);
	
	/**
	 * Aggregates a double array
	 * @param items The array of double to aggregate
	 * @return the aggregated double value
	 */	
	public double aggregate(double[] items);
	
	/**
	 * Applies a modifier to the passed base name to identify it as being associated to this aggregator
	 * @param base The base name to modify
	 * @return the modified name
	 */
	public String assignName(String base);
	
}
