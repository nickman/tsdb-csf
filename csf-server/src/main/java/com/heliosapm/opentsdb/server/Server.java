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

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Log4JLoggerFactory;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.heliosapm.opentsdb.server.handlergroups.URIHandler;
import com.heliosapm.opentsdb.server.handlergroups.fileserver.HttpStaticFileServerHandler;

/**
 * <p>Title: Server</p>
 * <p>Description: The server launcher.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.server.Server</code></p>
 */
public class Server {
	/** Static class logger */
	protected static final Logger LOG = LogManager.getLogger(Server.class);
	
	/** The netty server boss pool */
	protected final Executor bossPool;
	/** The netty server worker pool */
	protected final Executor workerPool;
	/** The netty bootstrap */
	protected final ServerBootstrap bstrap;
	/** The netty pipeline factory */
	protected final ChannelPipelineFactory pipelineFactory;
	/** The netty channel factory */
	protected final ChannelFactory channelFactory;
	/** The Inet socket that the server will listen on */
	protected final InetSocketAddress isock;
	/** The static content directory */
	protected final String contentRoot;
	
	/** The default binding interface */
	public static final String DEFAULT_INTERFACE = "0.0.0.0";
	/** The default binding port */
	public static final int DEFAULT_PORT = 8087;
	/** The default static content path */
	public static final String DEFAULT_CONTENT_DIR = String.format(".%ssrc%smain%sresources%swww", File.separator, File.separator, File.separator, File.separator);
	
	
	
	/**
	 * Boots the server.
	 * @param args Command line args are:<ol>
	 * <li>The binding interface in the form of an IP address or a host name</li>
	 * <li>The binding port</li>
	 * <li>The static content root directory
	 * </ol>
	 */
	public static void main(String[] args) {
		//BasicConfigurator.configure();
		InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory());
		String iface = null;
		String root = null;
		int port = -1;
		if(args.length>0) {
			iface = args[0];
		}
		if(args.length>1) {
			try {
				port = Integer.parseInt(args[1]);
			} catch (Exception e) {
				port = DEFAULT_PORT;
			}
		}
		if(args.length>2) {
			root = args[2];
		}
		
		if(iface==null) {
			iface = DEFAULT_INTERFACE;
		}
		if(port==-1) {
			port = DEFAULT_PORT;
		}
		if(root==null) {
			root = DEFAULT_CONTENT_DIR;
		}
		new Server(iface, port, root);
	}
	
	
	/**
	 * Creates a new Server
	 * @param iface The binding interface
	 * @param port the listening port
	 * @param root The root content directory
	 */
	public Server(String iface, int port, String root) {
		LOG.info("Starting Netty-Ajax Server on [" + iface + ":" + port + "]");
		this.contentRoot = root;
		HttpStaticFileServerHandler.contentRoot = root;
		isock = new InetSocketAddress(iface, port);
		bossPool = Executors.newCachedThreadPool();
		workerPool =  Executors.newCachedThreadPool();
		pipelineFactory = new ServerPipelineFactory(getPipelineModifiers());
//		((ServerPipelineFactory)pipelineFactory).addModifier(collector.getName(), collector);
		channelFactory = new NioServerSocketChannelFactory(bossPool, workerPool);
		bstrap = new ServerBootstrap(channelFactory);
		bstrap.setPipelineFactory(pipelineFactory);
		bstrap.setOption("child.keepAlive", true);
		bstrap.bind(isock);
		LOG.info("Netty-Ajax Server Started with Root [" + contentRoot + "]");		
		try { Thread.currentThread().join(); } catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
	
	protected Map<String, PipelineModifier> getPipelineModifiers() {
		Map<String, PipelineModifier> map = new ConcurrentHashMap<String, PipelineModifier>();
		Set<URL> urls = new HashSet<URL>();
		for(Iterator<URL> urlIter = ClasspathHelper.forClassLoader().iterator(); urlIter.hasNext();) {
			URL url = urlIter.next();
			if(url.toString().toLowerCase().endsWith(".jar") || !url.toString().toLowerCase().contains(".")) {
				urls.add(url);
			}
		}
		Reflections ref = new Reflections(new ConfigurationBuilder().setUrls(urls));
		for(Class<?> clazz: ref.getTypesAnnotatedWith(URIHandler.class)) {
			if(PipelineModifier.class.isAssignableFrom(clazz)) {
				URIHandler uhandler = clazz.getAnnotation(URIHandler.class);
				try {
					PipelineModifier pm = (PipelineModifier)clazz.newInstance();
					String[] names = uhandler.uri();
					for(String name: names) {
						name = name.trim().toLowerCase();
						if(map.containsKey(name)) {
							LOG.warn("The handler [" + pm.getName() + "] offering URI [" + name + "] could not be registered as that URI is already registered" );
						} else {
							map.put(name, pm);
						}
					}
				} catch (Exception e) {
					LOG.error("Failed to create PipelineModifier instance from class [" + clazz.getName() + "]");
				}
			}
		}
		LOG.info("Discovered PipelineModifiers:" + map);
		return map;
	}

}
