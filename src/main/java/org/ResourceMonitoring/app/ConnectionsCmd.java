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

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.Link;
import org.onosproject.net.host.HostService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@Command(scope = "RS", name = "connections",
        description = "List of all established connections")
public class ConnectionsCmd extends AbstractShellCommand {

    // Get the host service
    protected HostService hostService = get(HostService.class);

    // Get my Resource Monitoring Service instance
    private ResourceMonitoringService rmS = get(ResourceMonitoringService.class);

    @Override
    protected void doExecute() {
        Set<Map.Entry<Connection, List<Link>>> connections = rmS.getConnections().entrySet();

        if (connections.isEmpty()){
            print("No established connection! :(");
            return;
        }

        for (Map.Entry<Connection, List<Link>> e : connections) {
            Connection c = e.getKey();
            print("%s <-> %s (%d Mbps)", c.source.toString(), c.destination.toString(), c.bandwidth);
            String path = e.getValue().stream().map(l -> l.dst().deviceId().toString()).collect(Collectors.joining(" <-> "));
            print("\t Path: %s <-> %s", e.getValue().get(0).src().deviceId().toString(), path);
        }
    }

}
