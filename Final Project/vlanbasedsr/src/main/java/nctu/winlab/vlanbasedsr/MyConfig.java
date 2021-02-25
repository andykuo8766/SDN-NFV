/*
 * Copyright 2019-present Open Networking Foundation
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
package nctu.winlab.vlanbasedsr;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.basics.BasicElementConfig;
import org.onosproject.net.DeviceId;
import org.onlab.packet.IpPrefix;

import java.lang.Short;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * My Config class.
 */
public class MyConfig extends Config<ApplicationId>{

    // The JSON file should contain one field "name".
    public static final String MY_NAME = "name";
    public static final String DEVICE_ID = "devices";
    public static final String SUBNET_IP = "subnets";


    // For ONOS to check whether an uploaded configuration is valid.
    @Override
    public boolean isValid(){
        //boolean vmac = get(DHCP_MAC, null).matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
        return hasFields(MY_NAME, DEVICE_ID, SUBNET_IP);
    }

    // To retreat the value.
    public String myname(){
        String name = get(MY_NAME, null);
        return name;
    }

    // To retreat the value.
    public Map<DeviceId, Short> getNodeID(){

        Map<DeviceId, Short> SEGMENT_ID = new HashMap();

        //deviceJson
        JsonNode findNode = node().get("devices");
        Iterator<String> nodeIter = findNode.fieldNames();
        while( nodeIter.hasNext() ){
            String nodeKey = nodeIter.next();
            DeviceId dID = DeviceId.deviceId(nodeKey);
            Short vID = Short.valueOf(findNode.get(nodeKey).asText("-1"));
            SEGMENT_ID.put(dID, vID);
        }
        
        return SEGMENT_ID;
    }

    // To retreat the value.
    public Map<IpPrefix, DeviceId> getSubnetIP(){

        Map<IpPrefix, DeviceId> EDGE_IP = new HashMap();
        //deviceJson
        JsonNode findNode = node().get("subnets");
        Iterator<String> nodeIter = findNode.fieldNames();
        while( nodeIter.hasNext() ){
            String nodeKey = nodeIter.next();
            IpPrefix sIP = IpPrefix.valueOf(nodeKey);
            DeviceId dID = DeviceId.deviceId(findNode.get(nodeKey).asText(""));
            EDGE_IP.put(sIP, dID);
        }

        return EDGE_IP;
    }

    // To set or clear the value.
    public BasicElementConfig myname(String name){
        return (BasicElementConfig) setOrClear(MY_NAME, name);
    }
}

