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


/**
 * <p>Title: NumberWrapper</p>
 * <p>Description: Accepts an {@link INumberProvider} and wraps it to disguise it as a {@link Number}.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.aggregation.NumberWrapper</code></p>
 */
public class NumberWrapper {
	/** The wrapped number provider */
	private final INumberProvider provider;

	/**
	 * Returns a Number which is a synthetic which delegates to the passed {@link INumberProvider}
	 * @param provider The inner provider
	 * @return a Number
	 */
	public static Number getNumber(INumberProvider provider) {
		if(provider==null) throw new IllegalArgumentException("The passed number provider was null", new Throwable());
		return new NumberWrapper(provider).getNumber();
	}
	
	/**
	 * Creates a new NumberWrapper
	 * @param provider The provider to wrap
	 */
	private NumberWrapper(INumberProvider provider) {
		this.provider = provider;
	}
	
	/**
	 * @return
	 */
	private Number getNumber() {
		return new Number() {

			/**  */
			private static final long serialVersionUID = -7931533251321022845L;

			@Override
			public int intValue() {
				return provider.getNumber().intValue();
			}

			@Override
			public long longValue() {				
				return provider.getNumber().longValue();
			}

			@Override
			public float floatValue() {
				return provider.getNumber().floatValue();
			}

			@Override
			public double doubleValue() {
				return provider.getNumber().doubleValue();
			}			
		};
	}
}
