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
package com.heliosapm.opentsdb.client.jvmjmx.customx;

/**
 * <p>Title: AbstractCompiledExpression</p>
 * <p>Description: The base expression that compiled expression extend</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.AbstractCompiledExpression</code></p>
 */

public abstract class AbstractCompiledExpression implements CompiledExpression {

	/**
	 * Creates a new AbstractCompiledExpression
	 */
	public AbstractCompiledExpression() {
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CompiledExpression#trace(com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext)
	 */
	@Override
	public void trace(CollectionContext ctx) {
		try {
			
		} catch (Exception ex) {
			
		}
	}
	
	protected abstract void doTrace(final CollectionContext ctx) throws Exception;

}
