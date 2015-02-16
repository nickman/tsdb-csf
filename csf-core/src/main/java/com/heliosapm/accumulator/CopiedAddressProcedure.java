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
package com.heliosapm.accumulator;

/**
 * <p>Title: CopiedAddressProcedure</p>
 * <p>Description:  Defines a procedure that is executed with a copied address for a metric name, passed to the procedure for operating on the copied adddress space.
 * This keeps control of the copied address within the accumulator.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.accumulator.CopiedAddressProcedure</code></p>
 * @param <T> The return type of the procedure
 */
public interface CopiedAddressProcedure<T>  {
	/** Empty object array const */
	public static final Object[] EMPTY_ARR = {};
	
	/**
	 * Callback with the metric name represented in the address space and the address of the memory space.
	 * @param metricName The metric name 
	 * @param address the address
	 * @param refs An arbitrary array of objects to pass in the callback
	 * @return the return value of the procedure
	 */
	public T addressSpace(String metricName, long address, Object...refs);
	
	
}
