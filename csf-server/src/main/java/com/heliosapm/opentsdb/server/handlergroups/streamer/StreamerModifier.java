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
package com.heliosapm.opentsdb.server.handlergroups.streamer;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;

import com.heliosapm.opentsdb.server.PipelineModifier;
import com.heliosapm.opentsdb.server.handlergroups.URIHandler;

/**
 * <p>Title: StreamerModifier</p>
 * <p>Description: Pipeline modifier to create a pipeline that will use HTTP streaming to send updates to the client.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.server.handlergroups.streamer.StreamerModifier</code></p>
 */
@URIHandler(uri={"streamer"})
public class StreamerModifier implements PipelineModifier {
	/** The handler that this modifier adds at the end of the pipeline */
	protected final ChannelHandler handler = new StreamingHandler();
	/** The name of the handler this modifier adds */
	public static final String NAME = "streamer";
	
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
