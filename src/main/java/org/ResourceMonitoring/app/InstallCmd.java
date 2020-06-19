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
package org.ResourceMonitoring.app;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.IpAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.HostId;
import org.onosproject.net.host.HostService;


@Service
@Command(scope = "RS", name = "install",
         description = "Install a new connection between two hosts")
public class InstallCmd extends AbstractShellCommand {

    // Get the host service
    protected HostService hostService = get(HostService.class);

    // Get my Resource Monitoring Service instance
    private ResourceMonitoringService rmS = get(ResourceMonitoringService.class);

    @Argument(index = 0, name = "source", description = "Host ID of source",
            required = true, multiValued = false)
    private String source = null;

    @Argument(index = 1, name = "destination", description = "Host ID of destination",
            required = true, multiValued = false)
    private String destination = null;

    @Argument(index = 2, name = "bandwidth", description = "Capacity of the link in terms of bandwidth (Mbps)",
            required = true, multiValued = false)
    private String bandwidth = null;

    @Override
    protected void doExecute() {

        try {
            // Parsing of the input parameters, in case of errors throws IllegalArgumentException
            int capacity = Integer.parseInt(bandwidth);

            IpAddress sourceIp = IpAddress.valueOf(this.source);
            IpAddress dstIp = IpAddress.valueOf(this.destination);

            HostId sourceId = Helper.HostIdFromIp(sourceIp, this.hostService);
            HostId destinationId = Helper.HostIdFromIp(dstIp, this.hostService);

            rmS.createConnection(sourceId, destinationId, capacity);

        } catch (IllegalArgumentException e){
            e.printStackTrace();
            error("Please enter only valid IP Address!");
        } catch (Error e){
            error(e.getMessage());
        }

    }

}
