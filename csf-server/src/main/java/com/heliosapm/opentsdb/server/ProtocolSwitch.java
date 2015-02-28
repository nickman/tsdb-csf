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
package com.heliosapm.opentsdb.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.jboss.netty.handler.codec.compression.ZlibDecoder;
import org.jboss.netty.handler.codec.compression.ZlibWrapper;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.util.CharsetUtil;

/**
 * <p>Title: ProtocolSwitch</p>
 * <p>Description: An upfront channel handler to determine if the incoming is HTTP or plain socket text for submissions</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.server.ProtocolSwitch</code></p>
 */

public class ProtocolSwitch extends FrameDecoder {
	/** The comma based string delimeter */
	private static final ChannelBuffer COMMA_DELIM = ChannelBuffers.wrappedBuffer(new byte[] { ',' });
	/** The semi-colon based string delimeter */
	private static final ChannelBuffer SEMICOL_DELIM = ChannelBuffers.wrappedBuffer(new byte[] { ';' });
	
	/** The maximum frame size */
	public static final int MAX_FRAME_SIZE = 65536;
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());

	/** An execution handler to hand off the metric submissions to */
	protected static final ExecutionHandler execHandler = new ExecutionHandler(Executors.newCachedThreadPool(			
			new ThreadFactory() {
				final AtomicInteger serial = new AtomicInteger(0);
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r, "SocketMetricSubmissionThread#" + serial.incrementAndGet());
					t.setDaemon(true);
					return t;
				}
			}
	), false, true);
	/** The socket based metric submission handler */
	protected final SocketSubmissionHandler submissionHandler = new SocketSubmissionHandler();
	


	/**
	 * {@inheritDoc}
	 * @see org.jboss.netty.handler.codec.frame.FrameDecoder#decode(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.Channel, org.jboss.netty.buffer.ChannelBuffer)
	 */
	@Override
	protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
		// Will use the first two bytes to detect a protocol.
		if (buffer.readableBytes() < 2) {
			return null;
		}	
		ChannelPipeline pipeline = ctx.getPipeline();
		final int magic1 = buffer.getUnsignedByte(buffer.readerIndex());  // 22 and 3 for RMI/JMX
		final int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);		
		if(log.isDebugEnabled()) log.debug("\n\t  MAGIC:" + new String(new byte[]{(byte)magic1, (byte)magic2}) + "\n");
		if (!isHttp(magic1, magic2)) {
			boolean gzip = false;
			if(isGzip(magic1, magic2)) {
				gzip = true;
				if(log.isDebugEnabled()) log.debug("Switching to GZipped Raw Socket");
			} else {
				if(log.isDebugEnabled()) log.debug("Switching to Raw Socket");
			}
			ChannelHandler ch = null;
			while((ch = pipeline.getFirst())!=null) {
					pipeline.remove(ch);
			}			
			if(gzip) {
				pipeline.addLast("decompressor", new ZlibDecoder(ZlibWrapper.GZIP));
			}
			List<ChannelBuffer> delims = new ArrayList<ChannelBuffer>();
			delims.add(SEMICOL_DELIM);
			pipeline.addLast("frameDecoder", new DelimiterBasedFrameDecoder(65536, true, true, delims.toArray(new ChannelBuffer[delims.size()])));
			//pipeline.addLast("logger", new LoggingHandler(InternalLogLevel.INFO));
			pipeline.addLast("stringDecoder", new StringDecoder(CharsetUtil.UTF_8));
			pipeline.addLast("exec-handler", execHandler);
			pipeline.addLast("submission-handler", submissionHandler);
			pipeline.sendUpstream(new UpstreamMessageEvent(channel, buffer, channel.getRemoteAddress()));
			return null;
		} 
		if(log.isDebugEnabled()) log.debug("Switching to HTTP");
		ctx.getPipeline().remove(this);
		return buffer.readBytes(buffer.readableBytes());
	}
	
	/**
	 * Determines if the channel is carrying a gzipped metric submssion
	 * @param magic1 The first byte of the incoming request
	 * @param magic2 The second byte of the incoming request
	 * @return true if the incoming payload is gzipped
	 */
	private boolean isGzip(int magic1, int magic2) {
		return magic1 == 31 && magic2 == 139;	
	}
	
	/**
	 * Determines if the channel is carrying an HTTP request
	 * @param magic1 The first byte of the incoming request
	 * @param magic2 The second byte of the incoming request
	 * @return true if the incoming is HTTP, false otherwise
	 */
	private boolean isHttp(int magic1, int magic2) {
		 return
		 magic1 == 'G' && magic2 == 'E' || // GET
		 magic1 == 'P' && magic2 == 'O' || // POST
		 magic1 == 'P' && magic2 == 'U' || // PUT
		 magic1 == 'H' && magic2 == 'E' || // HEAD
		 magic1 == 'O' && magic2 == 'P' || // OPTIONS
		 magic1 == 'P' && magic2 == 'A' || // PATCH
		 magic1 == 'D' && magic2 == 'E' || // DELETE
		 magic1 == 'T' && magic2 == 'R' || // TRACE
		 magic1 == 'C' && magic2 == 'O';   // CONNECT
	}	

}
