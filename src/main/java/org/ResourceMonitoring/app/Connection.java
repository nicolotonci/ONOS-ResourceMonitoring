package org.ResourceMonitoring.app;

import org.onlab.packet.IpPrefix;
import org.onosproject.net.HostId;

public class Connection {
    public IpPrefix source, destination;
    public int bandwidth;

    public Connection(IpPrefix source, IpPrefix destination, int bandwidth){
        this.source = source;
        this.destination = destination;
        this.bandwidth = bandwidth;
    }
}
