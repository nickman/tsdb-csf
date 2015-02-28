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

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;

/**
 * <p>Title: PipelineModifier</p>
 * <p>Description: Defines a class that modifies a pipeline for a specific purpose</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.server.PipelineModifier</code></p>
 */

public interface PipelineModifier {
	/**
	 * Modifies the passed pipeline to provide specific functionality
	 * @param pipeline The pipeline to modify
	 */
	public void modifyPipeline(ChannelPipeline pipeline);
	
	/**
	 * Returns the name of this modifier
	 * @return the name of this modifier
	 */
	public String getName();
	
	/**
	 * Returns the channel handler to insert into the pipeline
	 * @return the channel handler to insert into the pipeline
	 */
	public ChannelHandler getChannelHandler();
}
