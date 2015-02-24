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
package com.heliosapm.opentsdb.client.opentsdb.opt;


/**
 * <p>Title: ThreadMetricReader</p>
 * <p>Description: Defines a measurement taken from the execution of a defined progression of code</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.ThreadMetricReader</code></p>
 */
public interface ThreadMetricReader {

	/**
	 * Pre call to the reader. The reader should place it's initing value in the array slot with the passed index.
	 * @param values The values array. At the begining of the call it will contain undefined data. It should be overlayed
	 * by the initial value. 
	 * @param index the index of the slot the value should be written into
	 */
	public void pre(final long[] values, final int index);

	
	/**
	 * Post call to the reader. The reader should place it's captured value in the array slot with the passed index.
	 * @param values The values array. At the begining of the call it will contain the starting value (if applicable). It should be overlayed
	 * by the value emitted for collection
	 * @param index the index of the slot the value should be written into
	 */
	public void post(final long[] values, final int index);
	
	/**
	 * Catch block call to the reader, used to trace catch-and-throw exceptions.
	 * @throws Throwable throws the caught exception
	 */
	public void postCatch() throws Throwable;
	
	/**
	 * Finally block call to the reader, used to cleanup any references or resources allocated.
	 */
	public void postFinal();
	
	
	
}
