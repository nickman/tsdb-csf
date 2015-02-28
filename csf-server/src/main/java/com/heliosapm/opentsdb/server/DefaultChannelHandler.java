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

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * <p>Title: DefaultChannelHandler</p>
 * <p>Description: The initial and default channel handler inserted into all pipelines. This handler is intended to
 * examine the request URI and reconfigure the pipeline to handle the next request.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.server.DefaultChannelHandler</code></p>
 */

public class DefaultChannelHandler extends SimpleChannelUpstreamHandler {
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());

	/** The name of this handler in the pipeline */
	public static final String NAME = "router";

	protected final Map<String, PipelineModifier> modifierMap;
	/**
	 * Creates a new DefaultChannelHandler
	 * @param modifierMap The map of modifiers, keyed by the URI they accept.
	 */
	public DefaultChannelHandler(final Map<String, PipelineModifier> modifierMap) {
		this.modifierMap = modifierMap;		
	}
	
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {   
    	Object message = e.getMessage();
    	if(message instanceof HttpRequest) { 
	        HttpRequest request = (HttpRequest)message;
	        PipelineModifier modifier = getModifier(request.getUri());
	        if(!modifier.getName().equals(ctx.getAttachment())) {
	        	clearLastHandler(ctx.getPipeline());
	        	modifier.modifyPipeline(ctx.getPipeline());
	        	ctx.setAttachment(modifier.getName());
	        }
    	} else {
    		log.info("\n\t=====================\n\tNon HTTP Message Received\n\t" + message.getClass().getName() + "\n\t=====================\n");
    		ctx.sendDownstream(e);
    	}
        ctx.sendUpstream(e);
    }
    
    /**
     * Removes the last handler from the pipeline unless the last handler is this handler.
     * @param pipeline The pipeline to operate on
     */
    protected void clearLastHandler(ChannelPipeline pipeline) {
    	if(this!=pipeline.getLast()) {
    		pipeline.removeLast();
    	}
    }
    
    protected PipelineModifier getModifier(String uri) {
    	String[] frags = uri.trim().split("\\/");
    	for(String frag: frags) {
    		if(frag.trim().isEmpty()) continue;
    		PipelineModifier modifier = modifierMap.get(frag.trim());
    		if(modifier!=null) {
    			return modifier;
    		}
    	}
    	return modifierMap.get("");
    }

}
