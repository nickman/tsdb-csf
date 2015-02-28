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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * <p>Title: SocketSubmissionHandler</p>
 * <p>Description: Last channel handler for handling socket metric submissions</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.server.SocketSubmissionHandler</code></p>
 */

public class SocketSubmissionHandler extends SimpleChannelHandler {
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());
	/**
	 * {@inheritDoc}
	 * @see org.jboss.netty.channel.SimpleChannelHandler#messageReceived(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.MessageEvent)
	 */
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		String metricLine = (String)e.getMessage();
		String[] metrics = metricLine.split(",");
		if(log.isDebugEnabled()) log.debug("\n\t==============\n\tProcessing Metrics\n\tCount:" + metrics.length + "\n\tAddress:" + e.getChannel().getRemoteAddress() + "\n\t==============\n");
		Map<String, Long> metricMap = new HashMap<String, Long>(metrics.length);
		for(String metric: metrics) {
			try {
				String[] frags = metric.split(":");
				metricMap.put(frags[0], new Double(frags[1].trim()).longValue());
			} catch (Exception ex) {}
		}
//		if(!metricMap.isEmpty()) {
//			MetricCollector.getInstance().submitMetrics(e.getChannel().getRemoteAddress(), metricMap);
//		}
		super.messageReceived(ctx, e);
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.jboss.netty.channel.SimpleChannelHandler#handleDownstream(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelEvent)
	 */
	@Override
	public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
		AtomicInteger ai = (AtomicInteger) ctx.getAttachment();
		log.info("SocketSubmission Downstream:" + ai==null ? -1 : ai.get());
		super.handleDownstream(ctx, e);
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.jboss.netty.channel.SimpleChannelHandler#exceptionCaught(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ExceptionEvent)
	 */
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		log.error("Failed to process submission", e.getCause());
		super.exceptionCaught(ctx, e);
	}
}
