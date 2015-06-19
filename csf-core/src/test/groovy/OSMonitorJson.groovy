//@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
//@Grab(group='org.json', module='json', version='20090211')

import org.helios.nativex.sigar.HeliosSigar;
import org.hyperic.sigar.*;
import org.json.*;
import java.util.concurrent.atomic.*;
import org.hyperic.sigar.ptql.*;
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.POST;
import static groovyx.net.http.ContentType.JSON;

//==========================================================================================
//     Globals
//==========================================================================================
def sigar = HeliosSigar.getInstance();
def endPoint = "http://10.12.114.48:8070";
//def postPath = "/api/put?details";
//def postPath = "/api/put";
def postPath = "/api/put";
def HOST = InetAddress.getLocalHost().getHostName();
def jsonRoot = new JSONArray();
def http = new HTTPBuilder( endPoint );
//==========================================================================================
//
println "Starting OS Monitor for [$HOST]"

stime = {
    return (long)System.currentTimeMillis()/1000;
}

flush = { 
    if(jsonRoot.length() > 0) {
        println "Submitting ${jsonRoot.length()} metrics";    
        f = new File("/tmp/posted.json");
        f.delete();
        jsontext = jsonRoot.toString();
        f.append(jsontext);
        http.request( POST ) {
            uri.path = postPath;
            //uri.query = [summary : '']
            uri.query = [details : '']
            send JSON,  jsontext;

            response.success = {resp, reader ->
                println "POST response status: ${resp.statusLine}\n\n${reader.text}"
            }

            response.'500' = {
                println 'BAD REQUEST'
            }
        }
        /*
        http.post(body: jsonRoot.toString(), requestContentType: JSON) { resp ->        // headers: ['ContentX-Encoding' : 'gzip']
            println "Status: ${resp.statusLine}\n\n${resp.dump()}"
        };
        */
       
        jsonRoot = new JSONArray();
    }
}

/*
def http = new HTTPBuilder('http://restmirror.appspot.com/')
http.request( POST ) {
    uri.path = '/'
    send URLENC, [name: 'bob', title: 'construction worker']

    response.success = { resp ->
        println "POST response status: ${resp.statusLine}"
        assert resp.statusLine.statusCode == 201
    }
}
 */

pflush = { 
    println jsonRoot.toString(1);
    println "Printed ${jsonRoot.length()} metrics";    
    jsonRoot = new JSONArray();
}



trace = { metric, value, tags ->
    now = stime();
    m = new JSONObject();
    m.put("metric", metric);
    m.put("timestamp", now);
    m.put("value", value);
    m.put("tags", new JSONObject(tags).put("host", HOST));
    jsonRoot.put(m);
}

ctrace = { metric, value, tags ->
    if(value!=-1) {
        trace(metric, value, tags);
    }
}


try {
    while(true) {
        long start = System.currentTimeMillis();
        
        sigar.getCpuPercList().eachWithIndex() { cpu, index ->
            trace("sys.cpu", cpu.getCombined()*100, ['cpu':index, 'type':'combined']);
            trace("sys.cpu", cpu.getIdle()*100, ['cpu':index, 'type':'idle']);
            trace("sys.cpu", cpu.getIrq()*100, ['cpu':index, 'type':'irq']);
            trace("sys.cpu", cpu.getNice()*100, ['cpu':index, 'type':'nice']);
            trace("sys.cpu", cpu.getSoftIrq()*100, ['cpu':index, 'type':'softirq']);
            trace("sys.cpu", cpu.getStolen()*100, ['cpu':index, 'type':'stolen']);
            trace("sys.cpu", cpu.getSys()*100, ['cpu':index, 'type':'sys']);
            trace("sys.cpu", cpu.getUser()*100, ['cpu':index, 'type':'user']);
            trace("sys.cpu", cpu.getWait()*100, ['cpu':index, 'type':'wait']);
                 
        }
        //flush();
        //break;
        /* WINDOWS ISSUE
        sigar.getFileSystemList().each() { fs ->
            //println "FS: dir:${fs.getDirName()},  dev:${fs.getDevName()}, type:${fs.getSysTypeName()}, opts:${fs.getOptions()}";
            println fs;
            fsu = sigar.getFileSystemUsage(fs.getDevName());
            ctrace("sys.fs.avail", fsu.getAvail(), ['name':fs.getDirName(), 'type':fs.getSysTypeName()]);
            ctrace("sys.fs.queue", fsu.getDiskQueue(), ['name':fs.getDirName(), 'type':fs.getSysTypeName()]);
            ctrace("sys.fs.files", fsu.getFiles(), ['name':fs.getDirName(), 'type':fs.getSysTypeName()]);
            ctrace("sys.fs.free", fsu.getFree(), ['name':fs.getDirName(), 'type':fs.getSysTypeName()]);
            ctrace("sys.fs.freefiles", fsu.getFreeFiles(), ['name':fs.getDirName(), 'type':fs.getSysTypeName()]);
            ctrace("sys.fs.total", fsu.getTotal(), ['name':fs.getDirName(), 'type':fs.getSysTypeName()]);
            ctrace("sys.fs.used", fsu.getUsed(), ['name':fs.getDirName(), 'type':fs.getSysTypeName()]);
            ctrace("sys.fs.usedperc", fsu.getUsePercent(), ['name':fs.getDirName(), 'type':fs.getSysTypeName()]);
            
            ctrace("sys.fs.bytes", fsu.getDiskReadBytes(), ['name':fs.getDirName(), 'type':fs.getSysTypeName(), 'dir':'reads']);
            ctrace("sys.fs.bytes", fsu.getDiskWriteBytes(), ['name':fs.getDirName(), 'type':fs.getSysTypeName(), 'dir':'writes']);

            ctrace("sys.fs.ios", fsu.getDiskReads(), ['name':fs.getDirName(), 'type':fs.getSysTypeName(), 'dir':'reads']);
            ctrace("sys.fs.ios", fsu.getDiskWrites(), ['name':fs.getDirName(), 'type':fs.getSysTypeName(), 'dir':'writes']);

            
            flush();
            //println "[$fs]: $fsu";
        }
        */
        sigar.getNetInterfaceList().each() { iface ->
            ifs = sigar.getNetInterfaceStat(iface);
            trace("sys.net.iface", ifs.getRxBytes(), ['name':iface, 'dir':'rx', 'unit':'bytes']);
            trace("sys.net.iface", ifs.getRxPackets(), ['name':iface, 'dir':'rx', 'unit':'packets']);
            trace("sys.net.iface", ifs.getRxDropped(), ['name':iface, 'dir':'rx', 'unit':'dropped']);
            trace("sys.net.iface", ifs.getRxErrors(), ['name':iface, 'dir':'rx', 'unit':'errors']);
            trace("sys.net.iface", ifs.getRxOverruns(), ['name':iface, 'dir':'rx', 'unit':'overruns']);
            trace("sys.net.iface", ifs.getRxFrame(), ['name':iface, 'dir':'rx', 'unit':'frame']);
            
            trace("sys.net.iface", ifs.getTxBytes(), ['name':iface, 'dir':'tx', 'unit':'bytes']);
            trace("sys.net.iface", ifs.getTxPackets(), ['name':iface, 'dir':'tx', 'unit':'packets']);
            trace("sys.net.iface", ifs.getTxDropped(), ['name':iface, 'dir':'tx', 'unit':'dropped']);
            trace("sys.net.iface", ifs.getTxErrors(), ['name':iface, 'dir':'tx', 'unit':'errors']);
            trace("sys.net.iface", ifs.getTxOverruns(), ['name':iface, 'dir':'tx', 'unit':'overruns']);

            //println ifs;
        }
        
        tcp = sigar.getTcp();
        trace("sys.net.tcp", tcp.getRetransSegs(), ['type':'RetransSegs']);
        trace("sys.net.tcp", tcp.getPassiveOpens(), ['type':'PassiveOpens']);
        trace("sys.net.tcp", tcp.getCurrEstab(), ['type':'CurrEstab']);
        trace("sys.net.tcp", tcp.getEstabResets(), ['type':'EstabResets']);
        trace("sys.net.tcp", tcp.getAttemptFails(), ['type':'AttemptFails']);
        trace("sys.net.tcp", tcp.getInSegs(), ['type':'InSegs']);
        trace("sys.net.tcp", tcp.getActiveOpens(), ['type':'ActiveOpens']);
        trace("sys.net.tcp", tcp.getInErrs(), ['type':'InErrs']);        
        trace("sys.net.tcp", tcp.getOutRsts(), ['type':'OutRsts']);        
        trace("sys.net.tcp", tcp.getOutSegs(), ['type':'OutSegs']);       
        
        netstat = sigar.getNetStat();
        
        /*
        //===================================================================================================================
        //        INBOUND
        //===================================================================================================================
        trace("sys.net.socket", netstat.getAllInboundTotal(), ['dir':'inbound', 'protocol':'all', 'state':'all']);
        trace("sys.net.socket", netstat.getTcpInboundTotal(), ['dir':'inbound', 'protocol':'tcp', 'state':'all']);       
        trace("sys.net.socket", netstat.getTcpBound(), ['dir':'inbound', 'protocol':'tcp', 'state':'bound']);
        trace("sys.net.socket", netstat.getTcpListen(), ['dir':'inbound', 'protocol':'tcp', 'state':'lastack']);        
        trace("sys.net.socket", netstat.getTcpLastAck(), ['dir':'inbound', 'protocol':'tcp', 'state':'lastack']);        
        trace("sys.net.socket", netstat.getTcpCloseWait(), ['dir':'inbound', 'protocol':'tcp', 'state':'closewait']);
        
        //===================================================================================================================
        //        OUTBOUND
        //===================================================================================================================
        trace("sys.net.socket", netstat.getAllOutboundTotal(), ['dir':'outbound', 'protocol':'all', 'state':'all']);
        trace("sys.net.socket", netstat.getTcpOutboundTotal(), ['dir':'outbound', 'protocol':'tcp', 'state':'all']);        
        trace("sys.net.socket", netstat.getTcpSynRecv(), ['dir':'outbound', 'protocol':'tcp', 'state':'synrecv']);        
        trace("sys.net.socket", netstat.getTcpSynSent(), ['dir':'outbound', 'protocol':'tcp', 'state':'synsent']);        
        trace("sys.net.socket", netstat.getTcpEstablished(), ['dir':'outbound', 'protocol':'tcp', 'state':'established']);
        trace("sys.net.socket", netstat.getTcpClose(), ['dir':'outbound', 'protocol':'tcp', 'state':'close']);
        trace("sys.net.socket", netstat.getTcpClosing(), ['dir':'outbound', 'protocol':'tcp', 'state':'closing']);
        trace("sys.net.socket", netstat.getTcpFinWait1(), ['dir':'outbound', 'protocol':'tcp', 'state':'finwait1']);
        trace("sys.net.socket", netstat.getTcpFinWait2(), ['dir':'outbound', 'protocol':'tcp', 'state':'finwait2']);
        trace("sys.net.socket", netstat.getTcpIdle(), ['dir':'outbound', 'protocol':'tcp', 'state':'idle']);
        trace("sys.net.socket", netstat.getTcpTimeWait(), ['dir':'outbound', 'protocol':'tcp', 'state':'timewait']);        
        */
        //===================================================================================================================
        //        SERVER SOCKETS  -   Sends bad metrics
        //===================================================================================================================        
        
        /*
        connMap = new TreeMap<String, TreeMap<String, TreeMap<String, AtomicInteger>>>();
        sigar.getNetConnectionList(NetFlags.CONN_SERVER | NetFlags.CONN_PROTOCOLS).each() {
            addr = InetAddress.getByName(it.getLocalAddress()).getHostAddress();
            port = "${addr}:${it.getLocalPort()}";
            state = it.getStateString();
            protocol = it.getTypeString();
            stateMap = connMap.get(port);
            if(stateMap==null) {
                stateMap = new TreeMap<String, TreeMap<String, Integer>>();
                connMap.put(port, stateMap);
            }
            protocolMap = stateMap.get(state);
            if(protocolMap==null) {
                protocolMap = new TreeMap<String, AtomicInteger>();
                stateMap.put(state, protocolMap);
            }
            counter = protocolMap.get(protocol);
            if(counter==null) {
                counter = new AtomicInteger(0);
                protocolMap.put(protocol, counter);
            }
            counter.incrementAndGet();            
        }
        connMap.each() { port, stateMap ->
            stateMap.each() { state, protocolMap ->
                protocolMap.each() { protocol, counter ->
                    index = port.lastIndexOf(":");
                    addr = port.substring(0, index);
                    p = port.substring(index+1);
                    //println "Port: $port, State: $state, Protocol: $protocol, Count: ${counter.get()}";
                    trace("sys.net.server", counter.get(), ['protocol':protocol, 'state':state.toLowerCase(), 'port':p, 'bind':addr]);
                }
            }
        }
        //===================================================================================================================
        //        CLIENT SOCKETS
        //===================================================================================================================        
        connMap = new TreeMap<String, TreeMap<String, TreeMap<String, AtomicInteger>>>();
        sigar.getNetConnectionList(NetFlags.CONN_CLIENT | NetFlags.CONN_PROTOCOLS).each() {
            addr = InetAddress.getByName(it.getRemoteAddress()).getHostAddress();
            port = "${addr}:${it.getRemotePort()}";
            state = it.getStateString();
            protocol = it.getTypeString();
            stateMap = connMap.get(port);
            if(stateMap==null) {
                stateMap = new TreeMap<String, TreeMap<String, Integer>>();
                connMap.put(port, stateMap);
            }
            protocolMap = stateMap.get(state);
            if(protocolMap==null) {
                protocolMap = new TreeMap<String, AtomicInteger>();
                stateMap.put(state, protocolMap);
            }
            counter = protocolMap.get(protocol);
            if(counter==null) {
                counter = new AtomicInteger(0);
                protocolMap.put(protocol, counter);
            }
            counter.incrementAndGet();            
        }
        connMap.each() { port, stateMap ->
            stateMap.each() { state, protocolMap ->
                protocolMap.each() { protocol, counter ->
                    index = port.lastIndexOf(":");
                    addr = port.substring(0, index);
                    p = port.substring(index+1);
                    //println "Port: $port, State: $state, Protocol: $protocol, Count: ${counter.get()}";
                    trace("sys.net.client", counter.get(), ['protocol':protocol, 'state':state.toLowerCase(), 'port':p, 'address':addr]);
                }
            }
        }        
        */
        // ===================================================================================================================================
        //        SYSTEM MEMORY
        // ===================================================================================================================================
        mem = sigar.getMem();
        
        trace("sys.mem", mem.getUsed(), ['unit':'used']);       
        trace("sys.mem", mem.getFree(), ['unit':'used']);       
        
        trace("sys.mem.actual", mem.getActualFree(), ['unit':'free']);       
        trace("sys.mem.actual", mem.getActualUsed(), ['unit':'used']);       
        
        trace("sys.mem.total", mem.getTotal(), ['unit':'bytes']);       
        trace("sys.mem.total", mem.getRam(), ['unit':'MB']);       
        
        trace("sys.mem.percent", mem.getFreePercent(), ['unit':'free']);       
        trace("sys.mem.percent", mem.getUsedPercent(), ['unit':'used']);       
        
        // ===================================================================================================================================
        //    SWAP
        // ===================================================================================================================================
        swap = sigar.getSwap();
        swapFree = swap.getFree();
        swapUsed = swap.getUsed();
        swapTotal = swap.getTotal();
        trace("sys.swap", swapFree, ['unit': 'free']);
        trace("sys.swap", swapUsed, ['unit': 'used']);
        trace("sys.swap", swapTotal, ['unit': 'total']);
        trace("sys.swap.percent", swapUsed/swapTotal*100, ['unit': 'used']);
        trace("sys.swap.percent", swapFree/swapTotal*100, ['unit': 'free']);
        trace("sys.swap.page", swap.getPageIn(), ['dir': 'in']);
        trace("sys.swap.page", swap.getPageOut(), ['dir': 'out']);
        // ===================================================================================================================================
        //    PROCESS STATS
        // ===================================================================================================================================
        procStat = sigar.getProcStat();
        trace("sys.procs.state", procStat.getIdle(), ['state': 'idle']);
        trace("sys.procs.state", procStat.getRunning(), ['state': 'running']);
        trace("sys.procs.state", procStat.getSleeping(), ['state': 'sleeping']);
        trace("sys.procs.state", procStat.getStopped(), ['state': 'stopped']);
        trace("sys.procs.state", procStat.getZombie(), ['state': 'zombie']);
        
        trace("sys.procs.threads", procStat.getThreads(), []);
        trace("sys.procs.count", procStat.getTotal(), []);
        // ===================================================================================================================================
        //    LOAD AVERAGE
        // ===================================================================================================================================
        /* WINDOWS ISSUE
        double[] load = sigar.getLoadAverage();
        trace("sys.load", load[0], ['period': '1m']);
        trace("sys.load", load[1], ['period': '5m']);
        trace("sys.load", load[2], ['period': '15m']);
        flush();
        */
        
        // ===================================================================================================================================
        //    PROCESS GROUPS
        // ===================================================================================================================================
        processQueries = [
            "sshd" : "State.Name.eq=sshd",
            "apache2": "State.Name.eq=apache2",
            "java": "State.Name.eq=java"
        ];     
        // ===================================================================================================================================
        //    PROCESS GROUP CPU STATS
        // ===================================================================================================================================
        processQueries.each() { exe, query ->
            //mcpu = sigar.getMultiProcCpu(query);
            //trace("procs", mcpu.getPercent(), ['exe':exe, 'unit':'percentcpu']);
            //trace("procs", mcpu.getProcesses(), ['exe':exe, 'unit':'count']);            
        }
        // ===================================================================================================================================
        //    PROCESS GROUP MEM STATS
        // ===================================================================================================================================
        processQueries.each() { exe, query ->
            mmem = sigar.getMultiProcMem(query);
            trace("procs", mmem.getMajorFaults(), ['exe':exe, 'unit':'majorfaults']);
            trace("procs", mmem.getMinorFaults(), ['exe':exe, 'unit':'minorfaults']);            
            trace("procs", mmem.getPageFaults(), ['exe':exe, 'unit':'pagefaults']);            
            trace("procs", mmem.getResident(), ['exe':exe, 'unit':'resident']);            
            trace("procs", mmem.getShare(), ['exe':exe, 'unit':'share']);            
            trace("procs", mmem.getSize(), ['exe':exe, 'unit':'size']);            
        }
        
        
        
        
        //println tcp;

        //NetFlags.CONN_TCP | NetFlags.CONN_CLIENT
        sigar.getNetConnectionList(NetFlags.CONN_SERVER | NetFlags.CONN_UDP ).each() {
            //println "SendQueue=${it.getSendQueue()}, ReceiveQueue=${it.getReceiveQueue()}, State=${it.getStateString()}, Type=${it.getTypeString()}, LocalPort=${it.getLocalPort()}, RemoteAddress=${it.getRemoteAddress()}, RemotePort=${it.getRemotePort()}";
        }
        flush();
        long elapsed = System.currentTimeMillis() - start;
        println "Scan complete in $elapsed ms.";
        //break;
        Thread.sleep(15000);
    }  
    
    
        //trace(tsdbSocket);
        //     trace(tsdbSocket, "put sys.cpu.user ${stime()} 42.5 host=webserver1 cpu=0\n");
        //println "${it.getCombined()*100}  -  ${it.format(it.getCombined())}";
    //}

} finally {
    try { tsdbSocket.close(); } catch (e) {}
    println "Closed";
}

return null;

