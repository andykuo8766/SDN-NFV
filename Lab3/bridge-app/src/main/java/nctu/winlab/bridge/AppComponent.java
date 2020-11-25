package nctu.winlab.bridge;
//original import
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

//new import
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import com.google.common.collect.Maps;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onlab.util.Tools;
import java.util.Map;
import java.util.Optional;
//

@Component(immediate = true)
public class AppComponent{

    /** Configure Flow Timeout for installed flow rules **/
    private int flowTimeout = 20;

    /** Configure Flow Priority for installed flow rules **/
    private int flowPriority = 20;

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** 在使用onos的各種服務前，要先向CoreService註冊 **/
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;


    /** 追蹤整個系統各個組件的配置 **/
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    /** 攔截封包，選擇要攔截哪些封包，或是做哪些動作 **/
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    /** 用於新增flow rule **/
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    /** ONOS下，每個app都要有自己的appId **/
    private ApplicationId appId;

    protected Map<DeviceId, Map<MacAddress, PortNumber>> macTables = Maps.newConcurrentMap();

    private PacketProcessor processor;

    @Activate
    protected void activate() {
	log.info("Started");
	/** 註冊appId **/
	appId = coreService.registerApplication("nctu.winlab.bridge");
	/** 接收到封包要做什麼動作 **/
        processor = new SwitchPacketProcessor();
	/** Restricts packet types to IPV4 and ARP by only requesting those types. **/
        packetService.addProcessor(processor, PacketProcessor.director(3));
        packetService.requestPackets(DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.empty());
        
    }

    @Deactivate
    protected void deactivate() {  
        packetService.removeProcessor(processor);
        log.info("Stopped");
    }

    private class SwitchPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext pc) {
            initMacTable(pc.inPacket().receivedFrom());
            actLikeSwitch(pc);

        }
	
	/** Floods packet out of all switch ports. **/
        public void actLikeHub(PacketContext pc) {
            pc.treatmentBuilder().setOutput(PortNumber.FLOOD);
            pc.send();
        }

        public void actLikeSwitch(PacketContext pc) {
            Short type = pc.inPacket().parsed().getEtherType();
            if (type != Ethernet.TYPE_IPV4 && type != Ethernet.TYPE_ARP) {
                return;
            }

            ConnectPoint myconnectpoint = pc.inPacket().receivedFrom();
            Map<MacAddress, PortNumber> macTable = macTables.get(myconnectpoint.deviceId());
            MacAddress srcMac = pc.inPacket().parsed().getSourceMAC();
            MacAddress dstMac = pc.inPacket().parsed().getDestinationMAC();

	    //New MAC address added into the table
	    log.info("Add MAC address ==> switch: of:"+myconnectpoint.deviceId()+", MAC: "+srcMac+", port: "+myconnectpoint.port());
	    //////////////////////////////////////
	
	    macTable.put(srcMac, myconnectpoint.port());

          
            PortNumber outPort = macTable.get(dstMac);
            log.info("Configured. Flow Timeout is configured to {} seconds", flowTimeout);
            log.info("Configured. Flow Priority is configured to {}", flowPriority);


            if (outPort != null) {
		//Table hit,flow fule installed on the switch
		log.info("MAC "+ dstMac+" is matched on of:"+myconnectpoint.deviceId()+"! Install flow rule!");
		///////////////////////////


                pc.treatmentBuilder().setOutput(outPort);
                FlowRule myflowrule = DefaultFlowRule.builder()
                        .withSelector(DefaultTrafficSelector.builder().matchEthDst(dstMac).matchEthSrc(srcMac).build())
                        .withTreatment(DefaultTrafficTreatment.builder().setOutput(outPort).build())
                        .forDevice(myconnectpoint.deviceId())
			.withPriority(flowPriority)
                        .makeTemporary(flowTimeout)
                        .fromApp(appId).build();

                flowRuleService.applyFlowRules(myflowrule);
                pc.send();

            } else {

		//Table miss,packet flooded
		log.info("MAC "+dstMac+" is missed on of:"+myconnectpoint.deviceId()+"! Flood Packet!");
		///////////////////////////

		/** the output port has not been learned yet.  Flood the packet to all ports using the actLikeHub method **/
                actLikeHub(pc);
            }
        }
        private void initMacTable(ConnectPoint connectpoint) {
            macTables.putIfAbsent(connectpoint.deviceId(), Maps.newConcurrentMap());
        }
    }
}



