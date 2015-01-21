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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Response;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;

/**
 * <p>Title: ConnectivityChecker</p>
 * <p>Description: Checks HTTP connectivity to the OpenTSDB endpoint</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.ConnectivityChecker</code></p>
 */

public class ConnectivityChecker implements Runnable {
	/** The registered listeners */
	protected final Set<ConnectivityListener> listeners = new CopyOnWriteArraySet<ConnectivityListener>();
	/** The URL to check */
	protected String urlToCheck;
	/** The period, in seconds, to check the connection */
	protected final int checkPeriod;
	/** The connection timeout in ms. */
	protected final int connectTimeout;
	/** The request timeout in ms. */
	protected final int requestTimeout;
	
	/** Indicates if we're connected.We initialize with the assumption that we're connected */
	protected final AtomicBoolean connected = new AtomicBoolean(true);
	/** The number of connections made successfully */
	protected final AtomicLong goodConnections = new AtomicLong();
	/** The number of failed connection attempts */
	protected final AtomicLong failedConnections = new AtomicLong();
	/** The async http client to issue connectivity checks with */
	protected final AsyncHttpClient httpClient;
	/** Instance logger */
	private final Logger log = LogManager.getLogger(ConnectivityChecker.class);

	/**
	 * Creates a new ConnectivityChecker with a new http client
	 * @param client The async http client to use
	 * @param urlToCheck The URL to check
	 * @param checkPeriod The period, in seconds, to check the connection
	 * @param connectTimeout The connection timeout in ms.
	 * @param requestTimeout The request timeout in ms.
	 * @param proxy An optional proxy server
	 * @param listeners An optional array of connectivity state change listeners
	 */
	ConnectivityChecker(final String urlToCheck, final int checkPeriod, final int connectTimeout, final int requestTimeout, final ProxyServer proxy, final ConnectivityListener...listeners) {
		this(null, urlToCheck, checkPeriod, connectTimeout, requestTimeout, proxy, listeners);
	}

	
	 
	
	/**
	 * Creates a new ConnectivityChecker with a supplied http client
	 * @param client The async http client to use
	 * @param urlToCheck The URL to check
	 * @param checkPeriod The period, in seconds, to check the connection
	 * @param connectTimeout The connection timeout in ms.
	 * @param requestTimeout The request timeout in ms.
	 * @param proxy An optional proxy server
	 * @param listeners An optional array of connectivity state change listeners
	 */
	ConnectivityChecker(final AsyncHttpClient client, final String urlToCheck, final int checkPeriod, final int connectTimeout, final int requestTimeout, final ProxyServer proxy, final ConnectivityListener...listeners) {
		this.urlToCheck = urlToCheck;
		this.checkPeriod = checkPeriod;
		this.connectTimeout= connectTimeout;
		this.requestTimeout = requestTimeout;
		if(listeners!=null && listeners.length != 0) {
			for(ConnectivityListener listener: listeners) {
				addListener(listener);
			}
		}
		if(client!=null) {
			httpClient = client;
		} else {
			AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder()		
			.setAllowPoolingConnection(false)
			.setConnectionTimeoutInMs(connectTimeout)
			.setRequestTimeoutInMs(requestTimeout)
			.setAsyncHttpClientProviderConfig(new NettyAsyncHttpProviderConfig()
				.addProperty(NettyAsyncHttpProviderConfig.BOSS_EXECUTOR_SERVICE, Threading.getInstance().getThreadPool())
				.addProperty(NettyAsyncHttpProviderConfig.REUSE_ADDRESS, true)
				.addProperty(NettyAsyncHttpProviderConfig.EXECUTE_ASYNC_CONNECT, false)
			)			
			.setExecutorService(Threading.getInstance().getThreadPool());
			if(proxy!=null) {
				builder.setProxyServer(proxy);
			}
			httpClient = new AsyncHttpClient(builder.build());					
		}
		Threading.getInstance().schedule(this, checkPeriod, TimeUnit.SECONDS);
		log.info("ConnectivityChecker Started\n\tURL:{}\n\tPeriod:{} s\n\tConnect Timeout:{} ms\n\tRequest Timeout:{} ms", urlToCheck, checkPeriod, connectTimeout, requestTimeout);
	}
	
	/**
	 * Synchronous connectivity check
	 * @param processResult true to process state changes as a result of a connect/fail,
	 * false for a simple check
	 * @return true if connection was made, false otherwise
	 */
	public boolean syncCheck(final boolean processResult) {
		try {
			log.debug("Executing Connectivity Check [{}]", urlToCheck);
			final CountDownLatch latch = new CountDownLatch(1);
			final Throwable[] throwable = new Throwable[1];
			final HttpResponseStatus[] _response = new HttpResponseStatus[1];
			try {
				httpClient.prepareGet(urlToCheck).execute(new AsyncCompletionHandler<Response>(){
					@Override
					public Response onCompleted(final Response response) throws Exception {				
						return response;
					}
					@Override
					public STATE onStatusReceived(final HttpResponseStatus status) throws Exception {
						_response[0] = status;
						final int code = status.getStatusCode(); 
						if(code < 200 && code >= 300) {
							throwable[0] = new Exception("Connectivity Check to [" + urlToCheck + "] failed with [" + status.getStatusCode() + "/" + status.getStatusText() + "]");
						} else {
							log.debug("Connectivity Check OK:[{}]: Code: [{}]", urlToCheck, code);
						}
						latch.countDown();						
						return STATE.CONTINUE;
					}
					@Override
					public void onThrowable(final Throwable t) {
//						t.printStackTrace(System.err);
						throwable[0] = t;
						latch.countDown();
					}
				});			
			} catch (Exception e) {			
				throwable[0] = e;
				latch.countDown();
			}
			try {
				boolean ok = latch.await(connectTimeout + requestTimeout, TimeUnit.MILLISECONDS) && throwable[0]==null;
				if(ok) {
					if(processResult) {
						processCheckResult(ok, null);
					}
					return true;
				}
				if(processResult) {
					processCheckResult(ok, throwable[0]!=null ? throwable[0] : new Exception("Unknown Connection Failure:" + (_response[0]!=null ? _response[0].getStatusText() : "No Response")));
				}
				return false;
			} catch (InterruptedException iex) {
				log.warn("Connection Check Was Interrupted. No action taken");
				return true;
			}		
			
		} catch (Exception ex) {
			return false;
		}
	}
	
	public void run() {
		//syncCheck(true);
	}
	
	/**
	 * Indicates if the connectivity is currently ok
	 * @return true if the connectivity is currently ok, false otherwise
	 */
	public boolean isConnected() {
		return connected.get();
	}
	
	protected void processCheckResult(final boolean passed, final Throwable t) {
		if(passed) {			
			long conn = goodConnections.incrementAndGet();
			if(conn==1) {
				fireOnConnected();
			} else {
				if(connected.compareAndSet(false, true)) {
					fireOnReconnected();
				}
			}
		} else {
			failedConnections.incrementAndGet();
			if(connected.compareAndSet(true, false)) {
				fireOnDisconnected(t);
			}
		}
	}
	
	/**
	 * Fires the OnConnected event to all listeners
	 */
	protected void fireOnConnected() {
		for(final ConnectivityListener listener: listeners) {
			Threading.getInstance().async(new Runnable(){
				public void run() {
					listener.onConnected();
				}
			});
		}
	}
	
	/**
	 * Fires the OnDisconnected event to all listeners
	 */
	protected void fireOnDisconnected(final Throwable t) {
		for(final ConnectivityListener listener: listeners) {
			Threading.getInstance().async(new Runnable(){
				public void run() {
					listener.onDisconnected(t);
				}
			});
		}
	}
	
	/**
	 * Fires the OnReconnected event to all listeners
	 */
	protected void fireOnReconnected() {
		for(final ConnectivityListener listener: listeners) {
			Threading.getInstance().async(new Runnable(){
				public void run() {
					listener.onReconnected();
				}
			});
		}
	}
	

	/**
	 * Registers a connectivity state change listener
	 * @param listener the listener to register
	 */
	public void addListener(final ConnectivityListener listener) {
		if(listener!=null) listeners.add(listener);
	}
	
	/**
	 * Removes a connectivity state change listener
	 * @param listener the listener to remove
	 */
	public void removeListener(final ConnectivityListener listener) {
		if(listener!=null) listeners.remove(listener);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		final Logger LOG = LogManager.getLogger("ConnectivityCheckerTest");
//		ConnectivityChecker cc = new ConnectivityChecker("http://www.google.com", 3000, 500, 500, null, new ConnectivityListener(){
		ConnectivityChecker cc = new ConnectivityChecker("http://localhost:8070", 3000, 500, 500, null, new ConnectivityListener(){

			@Override
			public void onDisconnected(Throwable t) {
				LOG.log(Level.ERROR, "ConnectionChecker Failed", t);				
			}

			@Override
			public void onConnected() {
				LOG.info("Initial Connect OK !");
				
			}

			@Override
			public void onReconnected() {
				LOG.info("Reconnected OK !");
				
			}
			
		});
		
		try { Thread.currentThread().join(); } catch (Exception e) {}

	}
	
	/**
	 * <p>Title: ConnectivityListener</p>
	 * <p>Description: Defines a listener that is notified of connectivity status changes</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.ConnectivityChecker.ConnectivityListener</code></p>
	 */
	public static interface ConnectivityListener {
		/**
		 * Fired when a scheduled or error triggered connection test fails
		 * @param t The error causing the disconnect
		 */
		public void onDisconnected(Throwable t);		
		/**
		 * Fired on an initial connect
		 */
		public void onConnected();
		/**
		 * Fired when connectivity is re-established
		 */
		public void onReconnected();
	}
	
	/**
	 * <p>Title: BaseConnectivityListener</p>
	 * <p>Description: An empty concrete impl of a {@link ConnectivityListener}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.ConnectivityChecker.BaseConnectivityListener</code></p>
	 */
	public static class BaseConnectivityListener implements ConnectivityListener {

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.ConnectivityChecker.ConnectivityListener#onDisconnected(java.lang.Throwable)
		 */
		@Override
		public void onDisconnected(final Throwable t) {
			/* Override Me */
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.ConnectivityChecker.ConnectivityListener#onConnected()
		 */
		@Override
		public void onConnected() {
			/* Override Me */
			
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.ConnectivityChecker.ConnectivityListener#onReconnected()
		 */
		@Override
		public void onReconnected() {
			/* Override Me */			
		}
		
	}

	/**
	 * Returns the URL being checked
	 * @return the URL
	 */
	public String getUrlToCheck() {
		return urlToCheck;
	}

	/**
	 * Sets the URL to check
	 * @param urlToCheck the URL to check
	 */
	public void setUrlToCheck(final String urlToCheck) {
		if(urlToCheck!=null && !urlToCheck.trim().isEmpty()) {
			this.urlToCheck = urlToCheck;
		}
	}

}
