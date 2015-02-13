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
package com.heliosapm.opentsdb.client.opentsdb;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;

/**
 * <p>Title: EmptyAsyncHandler</p>
 * <p>Description: Empty async handler for extending</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.EmptyAsyncHandler</code></p>
 * @param <T> The expected return type
 */

public class EmptyAsyncHandler<T> implements AsyncHandler<T> {

	/**
	 * {@inheritDoc}
	 * @see com.ning.http.client.AsyncHandler#onThrowable(java.lang.Throwable)
	 */
	@Override
	public void onThrowable(final Throwable t) {
		/* No Op */
	}

	/**
	 * {@inheritDoc}
	 * @see com.ning.http.client.AsyncHandler#onBodyPartReceived(com.ning.http.client.HttpResponseBodyPart)
	 */
	@Override
	public com.ning.http.client.AsyncHandler.STATE onBodyPartReceived(final HttpResponseBodyPart bodyPart) throws Exception {
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.ning.http.client.AsyncHandler#onStatusReceived(com.ning.http.client.HttpResponseStatus)
	 */
	@Override
	public com.ning.http.client.AsyncHandler.STATE onStatusReceived(final HttpResponseStatus responseStatus) throws Exception {
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.ning.http.client.AsyncHandler#onHeadersReceived(com.ning.http.client.HttpResponseHeaders)
	 */
	@Override
	public com.ning.http.client.AsyncHandler.STATE onHeadersReceived(final HttpResponseHeaders headers) throws Exception {
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.ning.http.client.AsyncHandler#onCompleted()
	 */
	@Override
	public T onCompleted() throws Exception {
		return null;
	}
	
	/**
	 * <p>Title: FinalHookAsyncHandler</p>
	 * <p>Description: An async handler that simply provides a callback when the request is complete, regardless of outcome</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.EmptyAsyncHandler.FinalHookAsyncHandler</code></p>
	 * @param <T> The expected return type
	 */
	public abstract static class FinalHookAsyncHandler<T> extends EmptyAsyncHandler<T> {
		/**
		 * Invoked when the request ends regardless of outcome
		 * @param success true if handler was successful, false if an exception was thrown
		 */
		public abstract void onFinal(final boolean success);
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.EmptyAsyncHandler#onThrowable(java.lang.Throwable)
		 */
		@Override
		public void onThrowable(Throwable t) {
			onFinal(false);			
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.EmptyAsyncHandler#onCompleted()
		 */
		@Override
		public T onCompleted() throws Exception {
			onFinal(true);
			return super.onCompleted();
		}
	}

}
