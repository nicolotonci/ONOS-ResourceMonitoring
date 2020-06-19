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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.meter.*;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
        service = {ResourceMonitoringService.class}/*,
        property = {
                "someProperty=Some Default String Value",
        }*/)
public class ResourceMonitoringService {

    // link capacity in terms of Mbps
    public final static int LINK_CAPACITY = 100;

    public final static short DEFAULT_GROUP = 0;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MeterService meterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MeterStore meterStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;


    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    private ApplicationId appId;


    /// MY DATA STRUCTURES!!!

    private Map<Connection, List<Link>> connections;

    private Map<Link, Integer> availableBandwidth;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("org.ResourceMonitoring.app");
        cfgService.registerProperties(getClass());

        this.connections = new HashMap<>();
        this.availableBandwidth = new HashMap<>();

        log.info("Started");

    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");

        // delete all flow rules installed by this application
        for (Connection c : connections.keySet()){
            HostId h1 = hostService.getHostsByIp(c.source.address())
                    .stream()
                    .findFirst()
                    .get()
                    .id();
            HostId h2 = hostService.getHostsByIp(c.destination.address())
                    .stream()
                    .findFirst()
                    .get()
                    .id();

            this.deleteConnection(h1, h2);
        }
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }


    public void createConnection(final HostId from, final HostId to, final int bandwidth) throws Error{
        HostLocation sourceHostLocation = hostService.getHost(from).location();
        HostLocation destinationHostLocation = hostService.getHost(to).location();

        IpPrefix sourcePrefix = getIpPrefix(from);
        IpPrefix destPrefix = getIpPrefix(to);

        // check the existence of a connection between the two hosts
        if (isAlreadyInstalled(sourcePrefix, destPrefix))
            throw new Error("Another connection already exists with those endpoints!");

        // Retrieve all possible paths form source to destination
        Set<Path> allPaths = topologyService.getPaths(topologyService.currentTopology(), sourceHostLocation.deviceId(), destinationHostLocation.deviceId());

        // Find the shortest path with enough bandwidth
        Optional<Path> shortestPath = allPaths.stream()
                                              .sorted(shorterPath())
                                              .filter(hasEnoughResource(bandwidth))
                                              .findFirst();

        if (shortestPath.isEmpty())
            throw new Error("No path found from source to destination with enough capacity");


        // save the current connection
        List<Link> traversedLinks = shortestPath.get().links();
        connections.put(new Connection(sourcePrefix, destPrefix, bandwidth), traversedLinks);

        // update the resulting available bandwidth for each traversed link
        for (Link l : traversedLinks){
            AllocateBandwidth(l, bandwidth);
            AllocateBandwidth(getOppositeLink(l), bandwidth);
        }
        //traversedLinks.forEach(l -> AllocateBandwidth(l, bandwidth));


        // install rule on the first switch of the path
        installDuplexRule(sourceHostLocation.deviceId(), sourceHostLocation.port(), traversedLinks.get(0).src().port(), sourcePrefix, destPrefix, bandwidth);


        for(int i = 0;  i < traversedLinks.size()-1; i++){
            DeviceId device = traversedLinks.get(i).dst().deviceId();
            PortNumber incoming = traversedLinks.get(i).dst().port();
            PortNumber outgoing = traversedLinks.get(i+1).src().port();

            installDuplexRule(device, incoming, outgoing, sourcePrefix, destPrefix, bandwidth);
        }

        // install rule on destination edge switch
        PortNumber incoming = traversedLinks.get(traversedLinks.size()-1).dst().port();
        installDuplexRule(destinationHostLocation.deviceId(), incoming, destinationHostLocation.port(), sourcePrefix, destPrefix, bandwidth);

    }

    public void deleteConnection(HostId from, HostId to) throws Error{
        Iterable<FlowRule> flowRules = flowRuleService.getFlowRulesByGroupId(this.appId, DEFAULT_GROUP);

        IpPrefix sourcePrefix = getIpPrefix(from);
        IpPrefix destPrefix = getIpPrefix(to);

        Optional<Connection> connection = connections.keySet().stream()
                                                              .filter(hasEndpoints(sourcePrefix, destPrefix))
                                                              .findFirst();

        if (connection.isEmpty())
            throw new Error("No connection found between " + sourcePrefix.toString() + " -> " + destPrefix.toString());


        ArrayList<ImmutablePair<DeviceId, MeterId>>  meters = new ArrayList<>();

        for (FlowRule f : flowRules){
            if (isSelectorOfConnection(f.selector(), connection.get())) {
                // save the meter id associated with this flow rule
                f.treatment().meters().forEach(m -> meters.add(new ImmutablePair<>(f.deviceId(), m.meterId())));

                flowRuleService.removeFlowRules(f);
            }
        }

        for (ImmutablePair<DeviceId, MeterId> p : meters){
            Meter m = meterService.getMeter(p.left, p.right);
            meterStore.deleteMeterNow(m);
        }

        // free allocated bandwidth
        for(Link l : connections.get(connection.get())) {
            DeallocateBandwidth(l, connection.get().bandwidth);
            DeallocateBandwidth(getOppositeLink(l), connection.get().bandwidth);
        }

        connections.remove(connection.get());
    }

    private boolean isSelectorOfConnection(TrafficSelector t, Connection c){
        return ((t.getCriterion(Criterion.Type.IPV4_SRC).toString().substring(9).equals(c.source.toString()) &&
                t.getCriterion(Criterion.Type.IPV4_DST).toString().substring(9).equals(c.destination.toString())) || (
                t.getCriterion(Criterion.Type.IPV4_SRC).toString().substring(9).equals(c.destination.toString()) &&
                t.getCriterion(Criterion.Type.IPV4_DST).toString().substring(9).equals(c.source.toString())
                ));
    }

    private Predicate<Path> hasEnoughResource(final int bandwidth) {
        return p -> {
            for (Link l : p.links())
                if (this.availableBandwidth.containsKey(l) && this.availableBandwidth.get(l) < bandwidth)
                    return false;

            return true;
        };
    }

    private Comparator<Path> shorterPath(){
        return (p1, p2) -> p2.links().size() - p1.links().size();
    }

    private void AllocateBandwidth(Link l, int bandwidth){
        if (availableBandwidth.containsKey(l))
            availableBandwidth.merge(l, (-bandwidth), Integer::sum);
        else
            availableBandwidth.put(l, (LINK_CAPACITY - bandwidth));
    }

    private void DeallocateBandwidth(Link l, int bandwidth){
        availableBandwidth.merge(l, bandwidth, Integer::sum);
    }

    private void installDuplexRule(DeviceId device, PortNumber a, PortNumber b, IpPrefix ip_a, IpPrefix ip_b, int bandwidth){
        installRule(device, a, b, ip_a, ip_b, bandwidth);
        installRule(device, b, a, ip_b, ip_a, bandwidth);
    }

    private void installRule(DeviceId device, PortNumber incomingPort, PortNumber outgoingPort, IpPrefix srcPrefix, IpPrefix destPrefix, int bandwidth){
        // Selector
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(incomingPort)
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(srcPrefix)
                .matchIPDst(destPrefix)
                .build();

        // Treatment
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outgoingPort)
                .meter(this.createMeter(device, bandwidth).id())
                .build();

        // Forward Objective
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(10)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makePermanent()
                .add();


        // push the Forward Objective to the device
        flowObjectiveService.forward(device, forwardingObjective);

    }

    private IpPrefix getIpPrefix(HostId host){
        IpAddress ip = hostService.getHost(host).ipAddresses().stream().findFirst().get();
        return IpPrefix.valueOf(ip, IpPrefix.MAX_INET_MASK_LENGTH);
    }

    private Meter createMeter(DeviceId device, int bandwidth){

        Band b = DefaultBand.builder()
                            .ofType(Band.Type.DROP)
                            .withRate(bandwidth * 1000)
                            .build();

        MeterRequest m =  DefaultMeterRequest.builder()
                .forDevice(device)
                .fromApp(this.appId)
                .withBands(Collections.singleton(b))
                .withUnit(Meter.Unit.KB_PER_SEC)
                .add();

        Meter meter = meterService.submit(m);
        return meter;
    }

    private Predicate<Connection> hasEndpoints(IpPrefix a, IpPrefix b){
        return c -> (c.destination.equals(b) && c.source.equals(a)) || (c.destination.equals(a) && c.source.equals(b));
    }

    private boolean isAlreadyInstalled(IpPrefix a, IpPrefix b){
        return connections.keySet()
                          .stream()
                          .filter(hasEndpoints(a, b))
                          .count() == 1;
    }

    public Map<Link, Integer> getLinksStatus(){return this.availableBandwidth;}

    public Map<Connection, List<Link>> getConnections(){return this.connections;}

    private Link getOppositeLink(Link l){
        return linkService.getLink(l.dst(), l.src());
    }

}
