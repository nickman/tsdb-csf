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
package com.heliosapm.opentsdb.server.handlergroups.submission;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.execution.ExecutionHandler;

import com.heliosapm.opentsdb.server.PipelineModifier;
import com.heliosapm.opentsdb.server.handlergroups.URIHandler;

/**
 * <p>Title: SubmissionModifier</p>
 * <p>Description: A modifier that creates a pipeline for accepting external metric submissions via HTTP.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.server.handlergroups.submission.SubmissionModifier</code></p>
 */
@URIHandler(uri={"submission"})
public class SubmissionModifier implements PipelineModifier {
	/** The handler that this modifier adds at the end of the pipeline */
	protected final ChannelHandler handler = new SubmissionHandler();
	/** The name of the handler this modifier adds */
	public static final String NAME = "submission";
	/** An execution handler to hand off the metric submissions to */
	protected static final ExecutionHandler execHandler = new ExecutionHandler(Executors.newCachedThreadPool(			
			new ThreadFactory() {
				final AtomicInteger serial = new AtomicInteger(0);
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r, "HttpMetricSubmissionThread#" + serial.incrementAndGet());
					t.setDaemon(true);
					return t;
				}
			}
	), false, true);
	/** The name of the execution handler this modifier adds */
	public static final String EXEC_NAME = "exec-submission";
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.server.PipelineModifier#getChannelHandler()
	 */
	public ChannelHandler getChannelHandler() {
		return handler;
	}

	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.server.PipelineModifier#modifyPipeline(org.jboss.netty.channel.ChannelPipeline)
	 */
	@Override
	public void modifyPipeline(ChannelPipeline pipeline) {
		if(pipeline.get(EXEC_NAME)==null) {
			pipeline.addLast(EXEC_NAME, execHandler);
		}
		if(pipeline.get(NAME)==null) {
			pipeline.addLast(NAME, handler);
		}
		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.server.PipelineModifier#getName()
	 */
	@Override
	public String getName() {
		return NAME;
	}

}
