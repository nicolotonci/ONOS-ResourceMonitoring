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
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.link.LinkService;

import java.util.Map;


@Service
@Command(scope = "RS", name = "links",
        description = "Get the state of all links")
public class LinksCmd extends AbstractShellCommand {

    // Get my Resource Monitoring Service instance
    private ResourceMonitoringService rmS = get(ResourceMonitoringService.class);

    private LinkService linkService = get(LinkService.class);

    @Override
    protected void doExecute() {
        Map<Link, Integer> linkStatus = rmS.getLinksStatus();

        for (Link l : linkService.getActiveLinks()){
            int allocated = 0;
            DeviceId srcDeviceId = l.src().deviceId();
            DeviceId dstDeviceId = l.dst().deviceId();

            if (linkStatus.containsKey(l))
                allocated = rmS.LINK_CAPACITY - linkStatus.get(l);

            int available = rmS.LINK_CAPACITY - allocated;

            print("%s -> %s  (Capacity: %d Mbps  -  Allocated: %d Mbps - Available: %d Mbps)", srcDeviceId.toString(), dstDeviceId.toString(), rmS.LINK_CAPACITY, allocated, available);
        }
    }

}
