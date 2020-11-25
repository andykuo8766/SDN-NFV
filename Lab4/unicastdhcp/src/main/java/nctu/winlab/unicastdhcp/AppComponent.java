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
package nctu.winlab.unicastdhcp;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

////////////////////////////////////////////////////////////////////////////////////////////////////
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;

import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;

import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;

import org.onosproject.net.Path;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.PortNumber;
import org.onosproject.net.host.HostService;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceService;

import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyVertex;
import org.onosproject.net.topology.TopologyEdge;

import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRuleService;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.packet.IPv4;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;
////////////////////////////////////////////////////////////////////////////////////////////////////
import org.onosproject.net.topology.PathService;
import java.util.*; 

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

//////////////////////////////////////////////////////////

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId appId;
    private final NameConfigListener cfgListener = new NameConfigListener();
    private DhcpPacketProcessor processor = new DhcpPacketProcessor();

//////////////////////////////////////////////////////////

    private static final MacAddress BoardcastMac = MacAddress.valueOf("ff:ff:ff:ff:ff:ff");
    private static final Ip4Address srcDHCPip = Ip4Address.valueOf("0.0.0.0");
    private static final Ip4Address dstDHCPip = Ip4Address.valueOf("255.255.255.255");
    private static final int DEFAULT_TIMEOUT = 300;
    private static final int DEFAULT_PRIORITY = 4000;
    private static String dhcpMac = "FF:FF:FF:FF:FF:FF";
    private static String dhcpCPoint = "of:0000000000000000/0";

//////////////////////////////////////////////////////////
    private final ConfigFactory factory =
        new ConfigFactory<ApplicationId, NameConfig>(
            APP_SUBJECT_FACTORY, NameConfig.class, "UnicastDhcpConfig") {
            	@Override
                public NameConfig createConfig() {
           		return new NameConfig();
          	}
          };
//////////////////////////////////////////////////////////
    /** ONOS service */
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PathService pathService;

//////////////////////////////////////////////////////////

    @Activate
    protected void activate() {
        
    	appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
   	cfgService.addListener(cfgListener);
    	cfgService.registerConfigFactory(factory);
        packetService.addProcessor(processor, PacketProcessor.director(1));

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
        	.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

	log.info("Started");

    }

    @Deactivate
    protected void deactivate() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4); 
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        packetService.removeProcessor(processor);
        processor = null;
        
        cfgService.removeListener(cfgListener);
    	cfgService.unregisterConfigFactory(factory);
	log.info("Stopped");
    }

//////////////////////////////////////////////////////////////////////////////////////////////////

    private class NameConfigListener implements NetworkConfigListener {

        // update the variable
        private void reconfigureNetwork(nctu.winlab.unicastdhcp.NameConfig config) {
            if (config == null) {
                return;
            }
            if (config.name() != null) {
                dhcpCPoint = config.name();
            }
        }

        @Override
        public void event(NetworkConfigEvent event) {
     	    if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED) && event.configClass().equals(NameConfig.class)) {
                NameConfig config = cfgService.getConfig(appId, NameConfig.class);
                if (config != null) {
		    reconfigureNetwork(config);
                    log.info("DHCP server is at {}", config.name());
       	        }
      	    }
        }

    }
//////////////////////////////////////////////////////////////////////////////////////////////////

    private class DhcpPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
     

            // check dhcp packet
	    // DHCP Discover & DHCP Request
            if ( ethPkt.isBroadcast() ){
                IPv4 ipv4IpPayload = (IPv4) ethPkt.getPayload();
                Ip4Address ipSrcAddr = Ip4Address.valueOf(ipv4IpPayload.getSourceAddress());
                Ip4Address ipDstAddr = Ip4Address.valueOf(ipv4IpPayload.getDestinationAddress());
                if ( ipSrcAddr.equals(srcDHCPip) && ipDstAddr.equals(dstDHCPip) ){
                    log.info("Get Broadcast Packet!!");
                    Discover_Request(context);
                }
                return;
            }
	    // DHCP Offer & DHCP Ack
            log.info("Get Packet!!");
            Offer_Ack(context);
        }

    }

    /** Sends flow modify to device */
    private void installRule(MacAddress srcMac, MacAddress dstMac,
                             PortNumber outPort, DeviceId configDeviceId){
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

        selectorBuilder.matchEthSrc(srcMac).matchEthDst(dstMac);

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(outPort)
                    .build();

        FlowRule myflowrule = DefaultFlowRule.builder()
                    .withSelector(selectorBuilder.build())
                    .withTreatment(treatment)
                    .forDevice(configDeviceId)
	            .withPriority(DEFAULT_PRIORITY)
                    .makeTemporary(DEFAULT_TIMEOUT)
                    .fromApp(appId)
		    .build();

        flowRuleService.applyFlowRules(myflowrule);

    }

    /** Discover_Request */
    private void Discover_Request(PacketContext context){



//////////////////////////////////////////////////////////////////////

        InboundPacket pkt = context.inPacket();
        ConnectPoint toHostCP = pkt.receivedFrom();
        Ethernet ethPkt = pkt.parsed();
        MacAddress srcmac = ethPkt.getSourceMAC();
        MacAddress dstmac = MacAddress.valueOf(dhcpMac);
        //log.info("path to DHCP: "+dstmac.toString());
        //log.info("From: "+toHostCP.deviceId().toString()+" "+toHostCP.port().toString());


        //get source device
        Set<Host> findHosts = hostService.getHostsByMac(srcmac);
        findHosts = hostService.getHostsByMac(srcmac);

        Host firstHost = findHosts.iterator().next();
        DeviceId sourceDeviceId = firstHost.location().deviceId();



	Set<Path> paths = pathService.getPaths(sourceDeviceId, DeviceId.deviceId(dhcpCPoint.split("/")[0]));
	Path path = paths.iterator().next();
	List<Link> link = path.links();
	int j = link.size()-1;


        //find path
	if( sourceDeviceId.equals(DeviceId.deviceId(dhcpCPoint.split("/")[0])) ){
            installRule(srcmac,BoardcastMac, PortNumber.portNumber(dhcpCPoint.split("/")[1]), sourceDeviceId);
            log.info("[Install] dhcp: {} {} {}", srcmac, PortNumber.portNumber(dhcpCPoint.split("/")[1]), sourceDeviceId );
        }
        else{
	  Link firstlink = link.get(j);
	  //log.info("[link] dhcp: {}", firstlink.dst() ); 

          ConnectPoint dstCP = firstlink.dst();

          installRule(srcmac,BoardcastMac, PortNumber.portNumber(dhcpCPoint.split("/")[1]), dstCP.deviceId());
          log.info("[Install] dhcp: {} {} {}", srcmac, PortNumber.portNumber(dhcpCPoint.split("/")[1]), dstCP.deviceId() );
	  j = link.size()-1;
          while(dstCP.deviceId() != sourceDeviceId){
	      //log.info("[linkPath] dhcp: {}", linkPath.dst() );
	      //log.info("[linkPath] link size :{}", link.size());
	      Link linkPath = link.get(j); 
              if(linkPath==null) break;
              //log.info("Path on "+linkPath.src().deviceId().toString() );
              installRule(srcmac, BoardcastMac,linkPath.src().port(), linkPath.src().deviceId());
              log.info("[Install] dhcp: {} {} {}", srcmac, linkPath.src().port(), linkPath.src().deviceId() );
              dstCP = linkPath.src();
	      j--;
          }
        }



    }


    /** Offer_Ack */
    private void Offer_Ack(PacketContext context){

        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();

        MacAddress srcmac = ethPkt.getSourceMAC();
        MacAddress dstmac = ethPkt.getDestinationMAC();

        //get source device
        Set<Host> findHosts = hostService.getHostsByMac(srcmac);
        Host firstHost = findHosts.iterator().next();
        DeviceId srcDeviceId = firstHost.location().deviceId();
        //find path
        findHosts = hostService.getHostsByMac(dstmac);
        firstHost = findHosts.iterator().next();
        ConnectPoint dstCP = firstHost.location();
	DeviceId dstDeviceId = dstCP.deviceId();
	installRule(srcmac, dstmac, dstCP.port(), dstDeviceId);

	
	Set<Path> paths = pathService.getPaths(srcDeviceId, dstDeviceId);
	Path path = paths.iterator().next();
	List<Link> link = path.links();
	int i = 0;
        while(dstCP.deviceId() != srcDeviceId){	
		Link linkPath = link.get(i);
		if(linkPath==null) break;
		installRule(srcmac, dstmac, linkPath.src().port(), linkPath.src().deviceId());
		dstCP = linkPath.src();
		i++;
	}

    }








}




