package org.ResourceMonitoring.app;

import org.onlab.packet.IpAddress;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.host.HostService;

import java.util.Set;

public class Helper {
    public static HostId HostIdFromIp(IpAddress addr, HostService service) throws IllegalArgumentException{
        Set<Host> s =  service.getHostsByIp(addr);
        if (s.size() != 1)
            throw new IllegalArgumentException();

        return s.iterator().next().id();
    }
}
