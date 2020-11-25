/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.ProxyArp;

import com.google.common.collect.ImmutableSet;
import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;

import static org.onlab.util.Tools.get;
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onlab.packet.Ethernet;
import org.onlab.packet.ARP;
import org.onlab.packet.MacAddress;
import org.onlab.packet.Ip4Address;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import java.nio.ByteBuffer;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.ConnectPoint;
import java.util.Set;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.packet.PacketPriority;
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent{

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;
	
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    private ApplicationId appId;
	
    private ProxyArpProcessor processor = new ProxyArpProcessor();	
	
    protected Map<Ip4Address, MacAddress> arpTable = new ConcurrentHashMap();
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Activate
    protected void activate() {
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////		
	appId = coreService.registerApplication("nctu.winlab.ProxyArp");
	packetService.addProcessor(processor, PacketProcessor.director(1));
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);		
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////		
        cfgService.registerProperties(getClass());
        //log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        packetService.removeProcessor(processor);
        processor = null;
        cfgService.unregisterProperties(getClass(), false);
        //log.info("Stopped");
    }



///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private class ProxyArpProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            ProxyArp_handler(context);
	    }
	}

    private void ProxyArp_handler(PacketContext context){
	InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        ARP arpPayload = (ARP) ethPkt.getPayload();
	//log.info("Get Arp Packet");	
        Ip4Address senderIP = Ip4Address.valueOf(arpPayload.getSenderProtocolAddress());
	Ip4Address targetIP = Ip4Address.valueOf(arpPayload.getTargetProtocolAddress());
        MacAddress  senderMac = MacAddress.valueOf(arpPayload.getSenderHardwareAddress());
        //MacAddress targetMac =  MacAddress.valueOf(arpPayload.getTargetProtocolAddress());
        arpTable.putIfAbsent(senderIP, senderMac);
	ConnectPoint srccp = pkt.receivedFrom();
	    
        //ARP REQUEST
        if( arpPayload.getOpCode() == ARP.OP_REQUEST ){	
	    //log.info("Get Arp Pequest");
            MacAddress targetMac = arpTable.get(targetIP);
            if( targetMac == null ){
                log.info("TABLE MISS. Send request to edge ports");
	        for ( ConnectPoint cp : edgeService.getEdgePoints() ){
                    packetOut(cp.deviceId(), cp.port(),ByteBuffer.wrap(ethPkt.serialize()) );
                }
                return;
            }
            else {
                log.info("TABLE HIT. Request MAC = {}",targetMac);
                Ethernet arpRe = ARP.buildArpReply(targetIP, targetMac, ethPkt);
                packetOut(srccp.deviceId(), srccp.port(),ByteBuffer.wrap(arpRe.serialize()) );
            }

	}
	//ARP REPLY
	else if (arpPayload.getOpCode() == ARP.OP_REPLY) {
            senderMac = MacAddress.valueOf(arpPayload.getSenderHardwareAddress());
            MacAddress targetMac = MacAddress.valueOf(arpPayload.getTargetHardwareAddress());
            //log.info("Get Arp Reply");
            Set<Host> findHosts = hostService.getHostsByMac(targetMac);
            if( findHosts.size()==0 ){
                //log.info( "hostService not find {}", targetMac );
                return;
            }
            log.info("RECV REPLY. Requested MAC = {}",senderMac);
            Host firstHost = findHosts.iterator().next();
            ConnectPoint sourceCP = firstHost.location();
            packetOut(sourceCP.deviceId(), sourceCP.port(),ByteBuffer.wrap(ethPkt.serialize()) );

	}
	
    }

    private void packetOut(DeviceId device, PortNumber outPort, ByteBuffer data){

	TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                     .setOutput(outPort).build();
        DefaultOutboundPacket oPacket =  new DefaultOutboundPacket(device, treatment, data);
        packetService.emit(oPacket);

    }

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
