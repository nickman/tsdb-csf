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

package com.heliosapm.opentsdb.client.aop;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;

/**
 * <p>Title: TestRetransformer</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.TestRetransformer</code></p>
 */

public class TestRetransformer {

	/**
	 * Creates a new TestRetransformer
	 */
	public TestRetransformer() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		log("RetransformTest");
		System.setProperty("tsdb.http.tsdb.url", "http://10.12.114.48:4242");
		System.setProperty("tsdb.http.compression.enabled", "false");		
		LoggingConfiguration.getInstance();
		Retransformer.getInstance().instrument(com.google.common.eventbus.EventBus.class, "post", "register");
		Retransformer.getInstance().instrument(Sub.class, "onMsg");
		runTest();

	}
	
	private static void runTest() {
		EventBus bus = new EventBus();
		bus.register(new Sub());
		Broadcaster b = new Broadcaster(bus);
		b.run();
	}
	
	
	private static class Sub {
		@Subscribe()
		public void onMsg(final Message message) {
			log("Received Message:" + message);
		}
	}
	
	private static class Message {
		final String msg;

		/**
		 * Creates a new Message
		 * @param msg
		 */
		public Message(String msg) {
			super();
			this.msg = msg;
		}
		
		public String toString() {
			return msg;
		}
	}
	
	private static class Broadcaster {
		final EventBus bus;
		
		Broadcaster(EventBus bus) {
			this.bus = bus;
			
		}
		public void run() {
			for(int i = 0; i < 100; i++) {
				Message m = new Message("This is message #" + i);
				bus.post(m);
				try { Thread.sleep(500); } catch (Exception ex) {}
			}
		}
	}
	
	public static void log(Object msg) {
		System.out.println(msg);
	}

}
