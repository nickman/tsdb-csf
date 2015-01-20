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

import static com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader.conf;
import static com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader.confBool;
import static com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader.confInt;
import static com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader.confLevel;
import static com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader.confURI;
import static com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader.confEnum;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.DirectChannelBufferFactory;
import org.jboss.netty.buffer.DynamicChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.json.JSONObject;

import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.opentsdb.AnnotationBuilder.TSDBAnnotation;
import com.heliosapm.opentsdb.client.opentsdb.EmptyAsyncHandler.FinalHookAsyncHandler;
import com.heliosapm.opentsdb.client.util.Util;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Body;
import com.ning.http.client.BodyGenerator;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.RandomAccessBody;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;



/**
 * <p>Title: HttpMetricsPoster</p>
 * <p>Description: Singleton service to provision async-http clients</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPoster</code></p>
 */

public class HttpMetricsPoster extends NotificationBroadcasterSupport implements HttpMetricsPosterMXBean, Constants, ConnectivityChecker.ConnectivityListener {
	/** The singleton instance */
	private static volatile HttpMetricsPoster instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** Instance logger */
	private final Logger log = LogManager.getLogger(getClass());
	/** Bad metrics logger */
	private final Logger badMetricsLogger = LogManager.getLogger("tsdb-bad-metrics");
	
	/** Indicates if connections are pooled (i.e. if KeepAlive is enabled) */
	protected boolean poolConnections = confBool(PROP_POOL_CONNS, DEFAULT_POOL_CONNS);
	/** Indicates if http request payloads are compressed */
	protected boolean enableCompression = confBool(PROP_COMPRESS, DEFAULT_COMPRESS);
	/** The http request retry count */
	protected int retryCount = confInt(PROP_REQUEST_RETRY, DEFAULT_REQUEST_RETRY);
	/** The maximum number of concurrent flushes */
	protected int maxConcurrentFlushes = confInt(PROP_MAX_CN_FLUSH, DEFAULT_MAX_CN_FLUSH);
	/** The retry delay in ms. */
	protected int requestRetryDelay = confInt(PROP_REQUEST_RETRY_DELAY, DEFAULT_REQUEST_RETRY_DELAY);	
	/** The http request timeout in ms */
	protected int requestTimeout = confInt(PROP_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
	/** The http connection timeout in ms */
	protected int connectionTimeout = confInt(PROP_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT);
	/** The TSDB http metric submission endpoint */
	protected String tsdbUrl = conf(PROP_TSDB_URL, DEFAULT_TSDB_URL);
	/** The offline reconnect attempt period */
	protected int offlineReconnectPeriod = confInt(PROP_RECONNECT_PERIOD, DEFAULT_RECONNECT_PERIOD);
	/** The metric submission batch size */
	protected int batchSize = confInt(PROP_BATCH_SIZE, DEFAULT_BATCH_SIZE);
	/** The level name to log bad metrics at */
	protected Level badLevel = confLevel(PROP_BAD_METRICS_LEVEL, DEFAULT_BAD_METRICS_LEVEL);
	
	/** The ObjectName this instance is registered with */
	protected final ObjectName objectName;
	
	/** Indicates if OpenTSDB responses to metric HTTP posts should be tracked (or just fire-`n-forget) */
	protected OpenTsdbPutResponseHandler putResponseHandler = confEnum(OpenTsdbPutResponseHandler.class, PROP_PUT_RESPONSE_HANDLER, DEFAULT_PUT_RESPONSE_HANDLER);
	
	/** the post url according to {@link #tsdbUrl}, {@link #trackResponses} and {@link #trackCountsOnly} */
	protected String postUrl = "";
	


	/** The connectivity checker */
	protected final ConnectivityChecker checker;
	/** A count of consecutive failures */
	protected final AtomicInteger consecutiveFails = new AtomicInteger();

	/** A counter of sent metrics */
	protected final AtomicLong sentMetrics = new AtomicLong();
	/** A counter of buffered metrics */
	protected final AtomicLong bufferedMetrics = new AtomicLong();
	/** The last elapsed time to send successfully */
	protected final AtomicLong lastSendTime = new AtomicLong(-1L);
	/** A gauge of stored (pending) offline metric buffers */
	protected final AtomicLong pendingMetricStores = new AtomicLong();
	/** Notification serial number provider */
	protected final AtomicLong notificationSerial = new AtomicLong();
	/** A hard disconnected flag. If set to true, all submissions will be buffered */
	protected final AtomicBoolean hardDown = new AtomicBoolean(true);
	/** A counter of successfully submitted metrics based on OpenTSDB responses */
	protected final AtomicLong successfulMetrics = new AtomicLong(0);
	/** A counter of failed (bad) submitted metrics based on OpenTSDB responses */
	protected final AtomicLong failedMetrics = new AtomicLong(0);

	
	/** The http proxy */
	protected ProxyServer proxy = null;
	/** The http proxy user name */
	protected String proxyUser = conf(PROP_CONNECTION_PROXY_USER, DEFAULT_CONNECTION_PROXY_USER);
	/** The http proxy user password */
	protected String proxyPassword = conf(PROP_CONNECTION_PROXY_PW, DEFAULT_CONNECTION_PROXY_PW);
	
	/** The buffer factory for allocating composite buffers to stream reads and writes through offheap space */
	private static final DirectChannelBufferFactory bufferFactory = new DirectChannelBufferFactory(4096);
	
	
	
	/** The notification infos */
	private static final MBeanNotificationInfo[] NOTIF_INFOS = new MBeanNotificationInfo[]{
		new MBeanNotificationInfo(new String[]{NOTIF_CONNECTED}, Notification.class.getName(), "OpenTSDB endpoint connection"),
		new MBeanNotificationInfo(new String[]{NOTIF_RECONNECTED}, Notification.class.getName(), "OpenTSDB endpoint reconnection"),
		new MBeanNotificationInfo(new String[]{NOTIF_DISCONNECTED}, Notification.class.getName(), "OpenTSDB endpoint disconnection")
	};

	
	
	
	/** The http client */
	protected AsyncHttpClient httpClient = null;
	
	/** The header map */
	protected final Map<String,Collection<String>> httpHeaders = new ConcurrentHashMap<String,Collection<String>>(2);
	
	/** Flag indicating if we're on line */
	protected final AtomicBoolean online = new AtomicBoolean(true);	
	/** The metric persistor to buffer metrics when we're off-line */
	protected final MetricPersistence mpersistor = MetricPersistence.getInstance();
	
	
	/**
	 * Acquires the HttpMetricsPoster singleton instance
	 * @return the HttpMetricsPoster singleton instance
	 */
	public static HttpMetricsPoster getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new HttpMetricsPoster(); 
				}
			}
		}
		return instance;
	}
	
	
	/**
	 * Creates a new HttpMetricsPoster
	 */
	private HttpMetricsPoster() {
		super(Threading.getInstance().getThreadPool(), NOTIF_INFOS);
		URI proxyURI = confURI(PROP_CONNECTION_PROXY, DEFAULT_CONNECTION_PROXY);
		setPostUrl();
		if(proxyURI!=null) {
			if(proxyUser!=null && !proxyUser.trim().isEmpty() && proxyPassword!=null && !proxyPassword.trim().isEmpty()) {
				proxy = new ProxyServer(proxyURI.getHost(), proxyURI.getPort(), proxyUser, proxyPassword);
			} else {
				proxy = new ProxyServer(proxyURI.getHost(), proxyURI.getPort());
			}
		}
		AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder()		
			.setAllowPoolingConnection(poolConnections)
			.setIOThreadMultiplier(1)
			.setAsyncHttpClientProviderConfig(new NettyAsyncHttpProviderConfig()
				.addProperty(NettyAsyncHttpProviderConfig.BOSS_EXECUTOR_SERVICE, Threading.getInstance().getThreadPool())
				.addProperty(NettyAsyncHttpProviderConfig.USE_DIRECT_BYTEBUFFER, new DirectChannelBufferFactory(4096))
				.addProperty(NettyAsyncHttpProviderConfig.REUSE_ADDRESS, true)
				.addProperty(NettyAsyncHttpProviderConfig.EXECUTE_ASYNC_CONNECT, true)
			)
			.setMaximumConnectionsPerHost(5)
			.setMaximumConnectionsTotal(15)
			.setConnectionTimeoutInMs(connectionTimeout)
			.setRequestTimeoutInMs(requestTimeout)
			.setExecutorService(Threading.getInstance().getThreadPool());
//		if(proxy!=null) {
//			builder.setProxyServer(proxy);
//		} 
		httpClient = new AsyncHttpClient(builder.build());		
		checker = new ConnectivityChecker(httpClient, tsdbUrl + "/api/version", offlineReconnectPeriod, connectionTimeout, requestTimeout/2, proxy, this);
		httpHeaders.put(Names.CONTENT_TYPE, Collections.singleton("application/json"));
		if(enableCompression) {
			httpHeaders.put(Names.CONTENT_ENCODING, Collections.singleton("x-gzip"));
		}
		log.info("AsyncHttpClient Created");
		objectName = registerMBean();
		if(checker.syncCheck(true)) {
			onConnected();
		}
	}
	
	private ObjectName registerMBean() {
		try {
			ObjectName on = new ObjectName(getClass().getPackage().getName() + ":service=" + getClass().getSimpleName());
			ManagementFactory.getPlatformMBeanServer().registerMBean(this, on);
			log.info("Registered management MBean for HttpMetricsPoster: [" + on + "]");
			return on;
		} catch (Exception ex) {
			log.warn("Failed to register management MBean for HttpMetricsPoster. Will Continue without:" + ex);
			return null;
		}
	}
	

	/**
	 * Determines the outcome of a metrics post
	 * @param status The HTTP response status
	 * @param t The throwable that resulted, possibly null
	 * @return true if retried
	 */
	private boolean processResponse(final HttpResponseStatus status, final Throwable t, final ChannelBuffer body, final int metricCount, final int retries, final long startTime) {		
		if(status!=null) {
			sentMetrics.addAndGet(metricCount);
			lastSendTime.set(System.currentTimeMillis() - startTime);
			return false;
		}
		
		if(t!=null) {
			if(t instanceof ConnectException || t instanceof TimeoutException) {
				if(!retry(body, metricCount, retries)) {
					mpersistor.offline(body);
					bufferedMetrics.addAndGet(metricCount);
					return false;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Posts a built OpenTSDB annotation 
	 * @param annotation The annotation to send
	 * TODO:  need annotation post stats and need to implement support for them in the MetricStore
	 */
	public void sendAnnotation(final TSDBAnnotation annotation) {
		if(annotation==null) throw new IllegalArgumentException("The passed annotation was null");
		if(hardDown.get()) {
			log.warn("OpenTSDB Annotation post dropped since endpoint was hard down. Store&Forward for Annotations coming soon");
		}
		try {			
			httpClient.preparePost(tsdbUrl + "/api/annotation")
				.setHeader(Names.CONTENT_TYPE, "application/json")							
				.setBody(annotation.toJSON())
				.execute(new AsyncHandler<String>(){
					int completionCode = -1;
					@Override
					public void onThrowable(final Throwable t) {
						log.warn("Failed to post Annotation:{}", annotation.toJSON(), t);
					}

					@Override
					public STATE onBodyPartReceived(final HttpResponseBodyPart bodyPart) throws Exception {
						return null;
					}

					@Override
					public STATE onStatusReceived(final HttpResponseStatus responseStatus) throws Exception {
						completionCode = responseStatus.getStatusCode();
						return null;
					}

					@Override
					public STATE onHeadersReceived(final HttpResponseHeaders headers) throws Exception {
						return null;
					}

					@Override
					public String onCompleted() throws Exception {
						log.info("Annotation Post Complete: code:" + completionCode);
						return null;
					}
					
				});
		} catch (Exception ex) {
			log.warn("Failed to post Annotation:{}", annotation.toJSON(), ex);
			// TODO: handle annotation post fail
		}
	}

	
	/**
	 * Submits a retry
	 * @param body Thge body to send
	 * @param metricCount The number of metrics in the body
	 * @param retries The number of retries so far
	 * @return true if a retry was scheduled, false otherwise
	 */
	private boolean retry(final ChannelBuffer body, final int metricCount, final int retries) {
		if(retries >= retryCount || !checker.isConnected()) {
			return false;  
		}
		Threading.getInstance().delay(new Runnable(){
			public void run() {
				try {
					send(body, metricCount, (retries + 1));
				} catch (Exception ex) {
					retry(body, metricCount, retries);
				}
			}
		}, requestRetryDelay);
		return true;
		
	}
	
	/**
	 * Sends the metrics content of the passed file to the OpenTSDB endpoint 
	 * @param file The file to send
	 * @param onComplete An optional completion callback. The int passed to the comletion handler will be: <ul>
	 *  <li><b>0</b>: The send was not issued.</li>
	 * 	<li><b>1</b>: The send failed outright.</li>
	 *  <li><b>2</b>: The send completed but reported bad content.</li>
	 *  <li><b>3</b>: The send was successful.</li>
	 * </ul>
	 */
	@SuppressWarnings("unchecked")
	void send(final File file, final CompletionCallback<Integer> onComplete) {
		if(file==null || !file.canRead()) {
			if(onComplete!=null) onComplete.onComplete(0);
			return;		
		}
		final int fileSize = (int)file.length();
		if(fileSize < 2) {
			if(!file.delete()) {
				Util.sdhook(file);
			}			
			if(onComplete!=null) onComplete.onComplete(0);
			return;
		}
		final ChannelBuffer buff;
		FileInputStream fis = null;
		FileChannel fc = null;
		final MappedByteBuffer[] mbb = new MappedByteBuffer[1]; 		
		try {
			fis = new FileInputStream(file);
			fc = fis.getChannel();
			mbb[0] = fc.map(MapMode.READ_ONLY, 0, fileSize);
			// if enableCompression is false, we need to decompress.
			if(!enableCompression) {
				buff = new DynamicChannelBuffer(ByteOrder.nativeOrder(), fileSize, bufferFactory);
				buff.writeBytes(mbb[0]);
				OffHeapFIFOFile.decompress(buff, null, null);
				OffHeapFIFOFile.clean(mbb[0]); mbb[0] = null;
			} else {
				buff = ChannelBuffers.wrappedBuffer(mbb);
			}
		} catch (Exception ex) {
			if(mbb[0]!=null) { OffHeapFIFOFile.clean(mbb[0]); mbb[0] = null; }
			if(onComplete!=null) onComplete.onComplete(1);
			throw new RuntimeException("Failed to send file [" + file + "]", ex);
		} finally {			
			if(fis!=null) try { fis.close(); fis=null;} catch (Exception x) {/* No Op */}
			if(fc!=null) try { fc.close(); fc=null;} catch (Exception x) {/* No Op */}
		}
		// FIXME:  
		try {
			send(buff, 0, retryCount-1, new FinalHookAsyncHandler<Object>(){
				int completionCode = 0;
				@Override
				public void onFinal(final boolean success) {
					if(mbb[0]!=null) { OffHeapFIFOFile.clean(mbb[0]); mbb[0] = null; }
					if(!file.delete()) {
						Util.sdhook(file);
					}					
					if(onComplete!=null) onComplete.onComplete(completionCode);
				}
				/**
				 * {@inheritDoc}
				 * @see com.heliosapm.opentsdb.client.opentsdb.EmptyAsyncHandler#onStatusReceived(com.ning.http.client.HttpResponseStatus)
				 */
				@Override
				public STATE onStatusReceived(final HttpResponseStatus responseStatus) throws Exception {
					final int code = responseStatus.getStatusCode();
					if(code >= 200 && code < 300) {
						completionCode = 3;
						onFinal(true);
					} else {
						completionCode = 2;
						onFinal(false);
					}
					return STATE.CONTINUE;
				}
				@Override
				public void onThrowable(final Throwable t) {
					completionCode = t==null ? 0 : 1;
					onFinal(false);
				}
			});
		} catch (Exception ex) {
			if(onComplete!=null) onComplete.onComplete(0);
			log.warn("Failed to send tmp file [{}]:" + ex, file);
			if(mbb[0]!=null) { OffHeapFIFOFile.clean(mbb[0]); mbb[0] = null; }				
			if(!file.delete()) {
				Util.sdhook(file);
			}
			// FIXME:  need to limit max number of concurrent sends on flush
			// FIXME:  should file be re-archived for submission later ?
		} finally {
			if(fis!=null) try { fis.close(); } catch (Exception x) {/* No Op */}
			if(fc!=null) try { fc.close(); } catch (Exception x) {/* No Op */}			
		}
	}
	
	
	/**
	 * Sets the post url according to {@link #putResponseHandler}  
	 */
	protected void setPostUrl() {
		postUrl = tsdbUrl + "/api/put" + putResponseHandler.putSignature; 
		if(putResponseHandler==OpenTsdbPutResponseHandler.NOTHING) {
			successfulMetrics.set(-1L);
			failedMetrics.set(-1L);
		} else {
			successfulMetrics.compareAndSet(-1L, 0);
			failedMetrics.compareAndSet(-1L, 0);			
		}
		if(checker!=null) checker.setUrlToCheck(tsdbUrl);
	}
	
	private static void throwAsyncHandlers(final Throwable t, final AsyncHandler<Object>...handlers) {
		if(handlers==null || handlers.length==0) return;
		for(AsyncHandler<Object> handler: handlers) {
			if(handler==null) continue;
			handler.onThrowable(t);
		}
	}
	
	/**
	 * This is where the real metrics HTTP post is done
	 * @param body
	 * @param metricsToWrite
	 * @param retries
	 * @param handlers
	 * @throws IOException
	 */
	private void send(final ChannelBuffer body, final int metricsToWrite, final int retries, final AsyncHandler<Object>...handlers) throws IOException {
		if(hardDown.get() || retries == retryCount) {			
			try { mpersistor.offline(body); } catch (Exception x) {/* TODO */}
			bufferedMetrics.addAndGet(metricsToWrite);
			throwAsyncHandlers(null, handlers);
			return;
		}
		if(enableCompression && !OffHeapFIFOFile.isGzipped(body)) {
			OffHeapFIFOFile.compress(body, null, null);
		}
		final boolean hasHandlers = handlers!=null && handlers.length>0;
		final int contentLength = body.readableBytes();
		final long start = System.currentTimeMillis();
		try {
			httpClient.preparePost(postUrl) 
			.setHeaders(httpHeaders)				
			.setBody(new BodyGenerator(){
				/**
				 * {@inheritDoc}
				 * @see com.ning.http.client.BodyGenerator#createBody()
				 */
				@Override
				public Body createBody() throws IOException {						
					return new RandomAccessBody() {
						/**
						 * {@inheritDoc}
						 * @see com.ning.http.client.RandomAccessBody#transferTo(long, long, java.nio.channels.WritableByteChannel)
						 */
						@Override
						public long transferTo(final long position, final long count, final WritableByteChannel target) throws IOException {
							final int rb = body.readableBytes();
							if(rb==0) return 0;
							int pos = body.readerIndex();								
							int bytesToCopy = count>rb ? rb : (int)count;
							target.write(body.readSlice(bytesToCopy).toByteBuffer());
							int progress = body.readerIndex() - pos;
							return progress;
						}
	
						@Override
						public long getContentLength() {					
							return contentLength;
						}
	
						@Override
						public long read(final ByteBuffer buffer) throws IOException {
							final int rb = body.readableBytes();
							int bytesRead = -1;
							if(rb==0) bytesRead = -1;
							return bytesRead;
						}
	
						@Override
						public void close() throws IOException {
							/* No Op */
						}
					};
				}
			})
			.execute(new AsyncHandler<String>(){
				final StringBuilder resp = new StringBuilder();
				int responseCode = -1;
				@Override
				public void onThrowable(final Throwable t) {
					processResponse(null, t, body, metricsToWrite, retries, start);
					if(hasHandlers) {
						for(AsyncHandler<Object> h: handlers) {
							if(h==null) continue;
							h.onThrowable(t);
						}
					}
				}
	
				@Override
				public STATE onBodyPartReceived(final HttpResponseBodyPart bodyPart) throws Exception {
					final byte[] bp = bodyPart.getBodyPartBytes();
					if(bp!=null && bp.length>0) {
						final String bps = new String(bp, Charset.defaultCharset());
						resp.append(bps);
					}
					if(hasHandlers) {
						for(AsyncHandler<Object> h: handlers) {
							if(h==null) continue;
							h.onBodyPartReceived(bodyPart);
						}
					}				
					return STATE.CONTINUE;
				}
	
				@Override
				public STATE onStatusReceived(final HttpResponseStatus responseStatus) throws Exception {
					responseCode = responseStatus.getStatusCode();
					processResponse(responseStatus, null, body, metricsToWrite, retries, start);
					if(hasHandlers) {
						for(AsyncHandler<Object> h: handlers) {
							if(h==null) continue;
							h.onStatusReceived(responseStatus);
						}
					}				
					return putResponseHandler==OpenTsdbPutResponseHandler.NOTHING ? STATE.ABORT : STATE.CONTINUE;
				}
	
				@Override
				public STATE onHeadersReceived(final HttpResponseHeaders headers) throws Exception {
					headers.getHeaders().getFirstValue(Names.CONTENT_LENGTH);
					if(hasHandlers) {
						for(AsyncHandler<Object> h: handlers) {
							if(h==null) continue;
							h.onHeadersReceived(headers);
						}
					}												
					return STATE.CONTINUE;
				}
	
				@Override
				public String onCompleted() throws Exception {
					if(hasHandlers) {
						for(AsyncHandler<Object> h: handlers) {
							if(h==null) continue;
							h.onCompleted();
						}
					}
					int[] counts = putResponseHandler.process(responseCode, resp);
					if(counts!=null && counts[0] + counts[1] != 0) {
						failedMetrics.addAndGet(counts[0]);
						successfulMetrics.addAndGet(counts[1]);						
					}
					return null;				
				}				
				
			});
		} catch (Exception ex) {
			throwAsyncHandlers(ex, handlers);
			if(ex instanceof IOException) throw (IOException)ex;
			throw new IOException("Failed to http-post", ex);
		}
	}
	
	
	
	
	
	/**
	 * Sends the passed metrics to the OpenTSDB endpoint
	 * @param metrics The metrics to send
	 */
	public void postMetrics(final Set<OpenTsdbMetric> metrics) {
		try {
//			log.info("Posting to [" + tsdbUrl + "/api/put" + "] : " + size + " bytes"); // + new JSONObject(json).toString(2) + "\n===============");
			if(metrics==null || metrics.isEmpty()) return;
			final ChannelBuffer metricsBuffer = new DynamicChannelBuffer(ByteOrder.nativeOrder(), metrics.size() * 40, bufferFactory);
			final int metricsToWrite = OpenTsdbMetric.writeToBuffer(metrics, metricsBuffer);
			send(metricsBuffer, metricsToWrite, 0);
			
		} catch (Exception ex) {
			ex.printStackTrace();  // FIXME:  We don't want to throw anything here, but track errors, and write to pers. file
		}		
	}




	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.ConnectivityChecker.ConnectivityListener#onDisconnected(java.lang.Throwable)
	 */
	@Override
	public void onDisconnected(final Throwable t) {
		log.warn("\n\t=================\n\tTSDB Disconnected\n\t[{}]\n\t=================\n", tsdbUrl);
		if(hardDown.compareAndSet(false, true)) {
			if(objectName!=null) {
				final Notification notif = new Notification(NOTIF_DISCONNECTED, objectName, notificationSerial.incrementAndGet(), System.currentTimeMillis(), "Disconnected from OpenTSDB@[" + tsdbUrl + "]");
				sendNotification(notif);
			}
		}
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.ConnectivityChecker.ConnectivityListener#onConnected()
	 */
	@Override
	public void onConnected() {		
		if(hardDown.compareAndSet(true, false)) {
			log.info("\n\t=================\n\tTSDB Connected\n\t[{}]\n\t=================\n", tsdbUrl);			
			if(objectName!=null) {
				final Notification notif = new Notification(NOTIF_CONNECTED, objectName, notificationSerial.incrementAndGet(), System.currentTimeMillis(), "Connected to OpenTSDB@[" + tsdbUrl + "]");
				sendNotification(notif);
			}
			sendAnnotation(new AnnotationBuilder((int)(System.currentTimeMillis()/1000))
				.setDescription("Java Client [" + AgentName.getInstance().getId() + "] Connected")
				.setCustom(Constants.APP_TAG, OpenTsdb.getInstance().getAppName())
				.setCustom(Constants.HOST_TAG, OpenTsdb.getInstance().getHostName())
				.build()
			);
			mpersistor.flushToServer(this);
		}
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.ConnectivityChecker.ConnectivityListener#onReconnected()
	 */
	@Override
	public void onReconnected() {			
		if(hardDown.compareAndSet(true, false)) {
			log.info("\n\t=================\n\tTSDB Reconnected\n\t[{}]\n\t=================\n", tsdbUrl);			
			if(objectName!=null) {
				final Notification notif = new Notification(NOTIF_RECONNECTED, objectName, notificationSerial.incrementAndGet(), System.currentTimeMillis(), "Reconnected to OpenTSDB@[" + tsdbUrl + "]");
				sendNotification(notif);
			}
			mpersistor.flushToServer(this);
		}
	}
	

	/**
	 * Indicates if compression is enabled
	 * @return true if compression is enabled, false otherwise
	 */
	public boolean isEnableCompression() {
		return enableCompression;
	}


	/**
	 * Sets the enabled state of request compression
	 * @param enableCompression true to enable false to disable
	 */
	public void setEnableCompression(final boolean enableCompression) {
		this.enableCompression = enableCompression;
		if(this.enableCompression) {
			httpHeaders.put(Names.CONTENT_ENCODING, Collections.singleton("x-gzip"));
		} else {
			httpHeaders.remove(Names.CONTENT_ENCODING);
		}
	}


	/**
	 * Returns the failed request retry count
	 * @return the failed request retry count
	 */
	public int getRetryCount() {
		return retryCount;
	}


	/**
	 * Sets the the failed request retry count
	 * @param retryCount the retryCount to set
	 */
	public void setRetryCount(final int retryCount) {
		if(retryCount < 0 || retryCount > 10) throw new IllegalArgumentException("Invalid request retry count [" + retryCount + "], Must be > 0 and <= 10");
		this.retryCount = retryCount;
	}


	/**
	 * Returns the failed request retry delay in ms
	 * @return the failed request retry delay
	 */
	public int getRequestRetryDelay() {
		return requestRetryDelay;
	}


	/**
	 * Sets the failed request retry delay in ms
	 * @param requestRetryDelay failed request retry delay
	 */
	public void setRequestRetryDelay(final int requestRetryDelay) {
		if(requestRetryDelay < 1000) throw new IllegalArgumentException("Invalid request retry delay [" + batchSize + "], Must be > 1000");
		this.requestRetryDelay = requestRetryDelay;
	}


	/**
	 * Returns the request timeout in ms
	 * @return the requestTimeout
	 */
	public int getRequestTimeout() {
		return requestTimeout;
	}


	/**
	 * Returns the connection timeout in ms. 
	 * @return the connectionTimeout
	 */
	public int getConnectionTimeout() {
		return connectionTimeout;
	}


	/**
	 * Returns the current TSDB Http metric submission endpoint 
	 * @return the current TSDB Http metric submission endpoint
	 */
	public String getTsdbUrl() {
		return tsdbUrl;
	}
	
	/**
	 * Returns the configured batch size
	 * @return the configured batch size
	 */
	public int getBatchSize() {
		return batchSize;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getSentMetrics()
	 */
	public long getSentMetrics() {
		return sentMetrics.get();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getBufferedMetrics()
	 */
	public long getBufferedMetrics() {
		return bufferedMetrics.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getLastSendTime()
	 */
	public long getLastSendTime() {
		return lastSendTime.get();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getPendingMetricStores()
	 */
	public long getPendingMetricStores() {
		return pendingMetricStores.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getOfflineFile()
	 */
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getOfflineFile()
	 */
	@Override
	public String getOfflineFile() {
		return mpersistor.getCurrentOfflineFile();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getOfflineFileSize()
	 */
	@Override
	public long getOfflineFileSize() {
		return mpersistor.getCurrentOfflineFileSize();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getOfflineFileEntries()
	 */
	@Override
	public int getOfflineFileEntries() {
		return mpersistor.getCurrentOfflineFileEntries();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getOfflineFileCompressionRate()
	 */
	@Override
	public double getOfflineFileCompressionRate() {
		return mpersistor.getCurrentOfflineFileCompressionRate();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#isOfflineFileCompressed()
	 */
	@Override
	public boolean isOfflineFileCompressed() {		
		return mpersistor.isCurrentOfflineFileCompressed();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getOfflineDir()
	 */
	public String getOfflineDir() {
		File d = mpersistor.getOfflineDir();
		return d==null ? null : d.getAbsolutePath();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getOfflineFileCount()
	 */
	public int getOfflineFileCount() {
		return mpersistor.getOfflineFileCount();
	}
	
	/**
	 * TODO: this will be expensive. Make it an Op ?
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getOfflineEntryCount()
	 */
	public int getOfflineEntryCount() {
		return mpersistor.getOfflineEntryCount();
	}

	

	/**
	 * Sets the TSDB Http metric submission endpoint 
	 * @param tsdbUrl the tsdbUrl to set
	 */
	public void setTsdbUrl(final String tsdbUrl) {
		try {
			new URL(tsdbUrl);
			this.tsdbUrl = tsdbUrl;
			setPostUrl();
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid TSDB URL [" + tsdbUrl + "]");
		}		
	}


	/**
	 * Sets the submission batch size
	 * @param batchSize the batch size to set
	 */
	public void setBatchSize(final int batchSize) {
		if(batchSize <1) throw new IllegalArgumentException("Invalid batch size [" + batchSize + "]");
		this.batchSize = batchSize;
		OpenTsdb.getInstance().setBatchSize(this.batchSize);
	}				

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#isConnected()
	 */
	public boolean isConnected() {
		return checker.isConnected();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#isHardDown()
	 */
	public boolean isHardDown() {
		return hardDown.get();
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getFlushSuccessCount()
	 */
	@Override
	public long getFlushSuccessCount() {
		return mpersistor.getFlushSuccessCounter();
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getFlushFailedCount()
	 */
	@Override
	public long getFlushFailedCount() {
		return mpersistor.getFlushFailedCounter();
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getFlushBadContentCount()
	 */
	@Override
	public long getFlushBadContentCount() {
		return mpersistor.getFlushBadContentCounter();		
	}
	
	
	// putResponseHandler==OpenTsdbPutResponseHandler
	
	/**
	 * Returns the currently installed put response handler
	 * @return the currently installed put response handler
	 */
	public OpenTsdbPutResponseHandler getPutResponseHandler() {
		return putResponseHandler;
	}
	
	/**
	 * Sets the current put response handler
	 * @param handler the response handler to use
	 */
	public void setPutResponseHandler(final OpenTsdbPutResponseHandler handler) {
		if(handler==null) throw new IllegalArgumentException("The passed handler was null");
		this.putResponseHandler = handler;
		log.info("Changed OpenTsdbPutResponseHandler to [{}]", handler.name());
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getPutResponseHandlerName()
	 */
	@Override
	public String getPutResponseHandlerName() {
		return putResponseHandler.name();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#setPutResponseHandlerName(java.lang.String)
	 */
	@Override
	public void setPutResponseHandlerName(final String handlerName) {
		if(handlerName==null || handlerName.trim().isEmpty()) throw new IllegalArgumentException("The passed handler name was null");
		OpenTsdbPutResponseHandler handler = null;
		try {
			handler = OpenTsdbPutResponseHandler.valueOf(handlerName.trim().toUpperCase());
			putResponseHandler = handler;
			log.info("Changed OpenTsdbPutResponseHandler to [{}]", handler.name());
		} catch (Exception ex) {
			throw new RuntimeException("Invalid response handler name [" + handlerName + "]. Valid values are:" + OpenTsdbPutResponseHandler.VALID_NAMES);
		}
	}
	
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getSuccessfulMetrics()
	 */
	@Override
	public long getSuccessfulMetrics() {
		return successfulMetrics.get();
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getFailedMetrics()
	 */
	@Override
	public long getFailedMetrics() {
		return failedMetrics.get();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getAppName()
	 */
	@Override
	public String getAppName() {		
		return OpenTsdb.getInstance().getAppName();
	}	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getHostName()
	 */
	@Override
	public String getHostName() {
		return OpenTsdb.getInstance().getHostName();
	}	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#setAppName(java.lang.String)
	 */
	@Override
	public void setAppName(final String newAppName) {
		AgentName.getInstance().resetNames(null, newAppName);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#setHostName(java.lang.String)
	 */
	@Override
	public void setHostName(String newHostName) {
		AgentName.getInstance().resetNames(newHostName, null);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getGlobalTags()
	 */
	@Override
	public HashMap<String, String> getGlobalTags() {		
		return new HashMap<String, String>(AgentName.getInstance().getGlobalTags());
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getMaxConcurrentFlushes()
	 */
	@Override
	public int getMaxConcurrentFlushes() {
		return maxConcurrentFlushes;
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#setMaxConcurrentFlushes(int)
	 */
	@Override
	public void setMaxConcurrentFlushes(final int maxConcurrentFlushes) {
		if(maxConcurrentFlushes<0 || maxConcurrentFlushes > 32) throw new IllegalArgumentException("Invalid max concurrent flushes [" + maxConcurrentFlushes + "]");
		this.maxConcurrentFlushes = maxConcurrentFlushes;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#dumpMetricNames(boolean)
	 */
	@Override
	public Set<String> dumpMetricNames(final boolean recurse) {		
		return Collections.emptySet(); //OpenTsdb.getInstance().dumpMetricNames(recurse);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean#getMetricCount()
	 */
	@Override
	public int getMetricCount() {	
		return -1; //OpenTsdb.getInstance().getMetricCount();
	}
}
