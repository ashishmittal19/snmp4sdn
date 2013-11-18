/*
 * Copyright (c) 2013 Industrial Technology Research Institute of Taiwan and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/*
This code reused the code base of OpenFlow plugin contributed by Cisco Systems, Inc. Their efforts are appreciated.
*/

package org.opendaylight.snmp4sdn.internal;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.dm.Component;
import org.opendaylight.snmp4sdn.IDataPacketListen;
import org.opendaylight.snmp4sdn.IDataPacketMux;
import org.opendaylight.snmp4sdn.IDiscoveryListener;
import org.opendaylight.snmp4sdn.IFlowProgrammerNotifier;
import org.opendaylight.snmp4sdn.IInventoryProvider;
import org.opendaylight.snmp4sdn.IInventoryShimExternalListener;
import org.opendaylight.snmp4sdn.IInventoryShimInternalListener;
import org.opendaylight.snmp4sdn.IOFStatisticsManager;
import org.opendaylight.snmp4sdn.IPluginReadServiceFilter;
import org.opendaylight.snmp4sdn.IRefreshInternalProvider;
import org.opendaylight.snmp4sdn.IStatisticsListener;
import org.opendaylight.snmp4sdn.ITopologyServiceShimListener;
import org.opendaylight.snmp4sdn.core.IController;
import org.opendaylight.snmp4sdn.core.IMessageListener;
import org.opendaylight.snmp4sdn.core.internal.Controller;
import org.opendaylight.controller.sal.core.ComponentActivatorAbstractBase;
import org.opendaylight.controller.sal.core.IContainerListener;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.flowprogrammer.IPluginInFlowProgrammerService;
import org.opendaylight.controller.sal.flowprogrammer.IPluginOutFlowProgrammerService;
import org.opendaylight.controller.sal.inventory.IPluginInInventoryService;
import org.opendaylight.controller.sal.inventory.IPluginOutInventoryService;
import org.opendaylight.controller.sal.packet.IPluginInDataPacketService;
import org.opendaylight.controller.sal.packet.IPluginOutDataPacketService;
import org.opendaylight.controller.sal.reader.IPluginInReadService;
import org.opendaylight.controller.sal.topology.IPluginInTopologyService;
import org.opendaylight.controller.sal.topology.IPluginOutTopologyService;
import org.opendaylight.controller.sal.utils.GlobalConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Openflow protocol plugin Activator
 *
 *
 */
public class Activator extends ComponentActivatorAbstractBase {
    protected static final Logger logger = LoggerFactory
            .getLogger(Activator.class);

    /**
     * Function called when the activator starts just after some initializations
     * are done by the ComponentActivatorAbstractBase.
     *
     */
    public void init() {
        Node.NodeIDType.registerIDType("SNMP", Long.class);
        NodeConnector.NodeConnectorIDType.registerIDType("SNMP", Short.class, "SNMP");
    }

    /**
     * Function called when the activator stops just before the cleanup done by
     * ComponentActivatorAbstractBase
     *
     */
    public void destroy() {
        Node.NodeIDType.unRegisterIDType("SNMP");
        NodeConnector.NodeConnectorIDType.unRegisterIDType("SNMP");
    }

    /**
     * Function that is used to communicate to dependency manager the list of
     * known implementations for services inside a container
     *
     *
     * @return An array containing all the CLASS objects that will be
     *         instantiated in order to get an fully working implementation
     *         Object
     */
    public Object[] getImplementations() {
        Object[] res = { TopologyServices.class, DataPacketServices.class,
                InventoryService.class, ReadService.class,
                FlowProgrammerNotifier.class };
        return res;
    }

    /**
     * Function that is called when configuration of the dependencies is
     * required.
     *
     * @param c
     *            dependency manager Component object, used for configuring the
     *            dependencies exported and imported
     * @param imp
     *            Implementation class that is being configured, needed as long
     *            as the same routine can configure multiple implementations
     * @param containerName
     *            The containerName being configured, this allow also optional
     *            per-container different behavior if needed, usually should not
     *            be the case though.
     */
    public void configureInstance(Component c, Object imp, String containerName) {
        if (imp.equals(TopologyServices.class)) {
            // export the service to be used by SAL
            c.setInterface(
                    new String[] { IPluginInTopologyService.class.getName(),
                            ITopologyServiceShimListener.class.getName() },
                    null);
            // Hook the services coming in from SAL, as optional in
            // case SAL is not yet there, could happen
            c.add(createContainerServiceDependency(containerName)
                    .setService(IPluginOutTopologyService.class)
                    .setCallbacks("setPluginOutTopologyService",
                            "unsetPluginOutTopologyService").setRequired(false));
            c.add(createServiceDependency()
                    .setService(IRefreshInternalProvider.class)
                    .setCallbacks("setRefreshInternalProvider",
                            "unsetRefreshInternalProvider").setRequired(false));
        }

        if (imp.equals(InventoryService.class)) {
            // export the service
            c.setInterface(
                    new String[] { IPluginInInventoryService.class.getName(),
                            IInventoryShimInternalListener.class.getName(),
                            IInventoryProvider.class.getName() }, null);

            // Now lets add a service dependency to make sure the
            // provider of service exists
            c.add(createServiceDependency()
                    .setService(IController.class, "(name=Controller)")
                    .setCallbacks("setController", "unsetController")
                    .setRequired(true));
            c.add(createContainerServiceDependency(containerName)
                    .setService(IPluginOutInventoryService.class)
                    .setCallbacks("setPluginOutInventoryServices",
                            "unsetPluginOutInventoryServices")
                    .setRequired(false));
        }

        if (imp.equals(DataPacketServices.class)) {
            // export the service to be used by SAL
            Dictionary<String, Object> props = new Hashtable<String, Object>();
            // Set the protocolPluginType property which will be used
            // by SAL
            props.put(GlobalConstants.PROTOCOLPLUGINTYPE.toString(), /*Node.NodeIDType.OPENFLOW*/"SNMP");
            c.setInterface(IPluginInDataPacketService.class.getName(), props);
            // Hook the services coming in from SAL, as optional in
            // case SAL is not yet there, could happen
            c.add(createServiceDependency()
                    .setService(IController.class, "(name=Controller)")
                    .setCallbacks("setController", "unsetController")
                    .setRequired(true));
            // This is required for the transmission to happen properly
            c.add(createServiceDependency().setService(IDataPacketMux.class)
                    .setCallbacks("setIDataPacketMux", "unsetIDataPacketMux")
                    .setRequired(true));
            c.add(createContainerServiceDependency(containerName)
                    .setService(IPluginOutDataPacketService.class)
                    .setCallbacks("setPluginOutDataPacketService",
                            "unsetPluginOutDataPacketService")
                    .setRequired(false));
        }

        if (imp.equals(ReadService.class)) {
            // export the service to be used by SAL
            Dictionary<String, Object> props = new Hashtable<String, Object>();
            // Set the protocolPluginType property which will be used
            // by SAL
            props.put(GlobalConstants.PROTOCOLPLUGINTYPE.toString(), /*Node.NodeIDType.OPENFLOW*/"SNMP");
            c.setInterface(IPluginInReadService.class.getName(), props);
            c.add(createServiceDependency()
                    .setService(IPluginReadServiceFilter.class)
                    .setCallbacks("setService", "unsetService")
                    .setRequired(true));
        }

        if (imp.equals(FlowProgrammerNotifier.class)) {
            // export the service to be used by SAL
            Dictionary<String, Object> props = new Hashtable<String, Object>();
            // Set the protocolPluginType property which will be used
            // by SAL
            props.put(GlobalConstants.PROTOCOLPLUGINTYPE.toString(), /*Node.NodeIDType.OPENFLOW*/"SNMP");
            c.setInterface(IFlowProgrammerNotifier.class.getName(), props);

            c.add(createContainerServiceDependency(containerName)
                    .setService(IPluginOutFlowProgrammerService.class)
                    .setCallbacks("setPluginOutFlowProgrammerService",
                            "unsetPluginOutFlowProgrammerService")
                    .setRequired(true));
        }
    }

    /**
     * Function that is used to communicate to dependency manager the list of
     * known implementations for services that are container independent.
     *
     *
     * @return An array containing all the CLASS objects that will be
     *         instantiated in order to get an fully working implementation
     *         Object
     */
    public Object[] getGlobalImplementations() {
        Object[] res = { Controller.class, OFStatisticsManager.class,
                FlowProgrammerService.class, ReadServiceFilter.class,
                DiscoveryService.class, DataPacketMuxDemux.class,
                InventoryServiceShim.class, TopologyServiceShim.class };
        return res;
    }

    /**
     * Function that is called when configuration of the dependencies is
     * required.
     *
     * @param c
     *            dependency manager Component object, used for configuring the
     *            dependencies exported and imported
     * @param imp
     *            Implementation class that is being configured, needed as long
     *            as the same routine can configure multiple implementations
     */
    public void configureGlobalInstance(Component c, Object imp) {

        if (imp.equals(Controller.class)) {
            logger.debug("Activator configureGlobalInstance( ) is called");
            Dictionary<String, Object> props = new Hashtable<String, Object>();
            props.put("name", "Controller");
            c.setInterface(IController.class.getName(), props);
        }

        if (imp.equals(FlowProgrammerService.class)) {
            // export the service to be used by SAL
            Dictionary<String, Object> props = new Hashtable<String, Object>();
            // Set the protocolPluginType property which will be used
            // by SAL
            props.put(GlobalConstants.PROTOCOLPLUGINTYPE.toString(), /*Node.NodeIDType.OPENFLOW*/"SNMP");
            c.setInterface(
                    new String[] {
                            IPluginInFlowProgrammerService.class.getName(),/* IMessageListener.class.getName(),*/
                            IContainerListener.class.getName(),
                            IInventoryShimExternalListener.class.getName() },
                    props);

            c.add(createServiceDependency()
                    .setService(IController.class, "(name=Controller)")
                    .setCallbacks("setController", "unsetController")
                    .setRequired(true));

            c.add(createServiceDependency()
                    .setService(IFlowProgrammerNotifier.class)
                    .setCallbacks("setFlowProgrammerNotifier",
                            "unsetsetFlowProgrammerNotifier")
                    .setRequired(false));

        }

        if (imp.equals(ReadServiceFilter.class)) {

            c.setInterface(
                    new String[] { IPluginReadServiceFilter.class.getName(),
                            IContainerListener.class.getName() }, null);

            c.add(createServiceDependency()
                    .setService(IController.class, "(name=Controller)")
                    .setCallbacks("setController", "unsetController")
                    .setRequired(true));
            c.add(createServiceDependency()
                    .setService(IOFStatisticsManager.class)
                    .setCallbacks("setService", "unsetService")
                    .setRequired(true));
        }

        if (imp.equals(OFStatisticsManager.class)) {

            c.setInterface(new String[] { IOFStatisticsManager.class.getName(),
                    IInventoryShimExternalListener.class.getName() }, null);

            c.add(createServiceDependency()
                    .setService(IController.class, "(name=Controller)")
                    .setCallbacks("setController", "unsetController")
                    .setRequired(true));
            c.add(createServiceDependency()
                    .setService(IStatisticsListener.class)
                    .setCallbacks("setStatisticsListener",
                            "unsetStatisticsListener").setRequired(false));
        }

        if (imp.equals(DiscoveryService.class)) {
            // export the service
            c.setInterface(
                    new String[] {
                            IInventoryShimExternalListener.class.getName(),
                            IDataPacketListen.class.getName(),
                            IContainerListener.class.getName() }, null);

            c.add(createServiceDependency()
                    .setService(IController.class, "(name=Controller)")
                    .setCallbacks("setController", "unsetController")
                    .setRequired(true));
            c.add(createContainerServiceDependency(
                    GlobalConstants.DEFAULT.toString())
                    .setService(IInventoryProvider.class)
                    .setCallbacks("setInventoryProvider",
                    "unsetInventoryProvider").setRequired(true));
            c.add(createServiceDependency().setService(IDataPacketMux.class)
                    .setCallbacks("setIDataPacketMux", "unsetIDataPacketMux")
                    .setRequired(true));
            c.add(createServiceDependency()
                    .setService(IDiscoveryListener.class)
                    .setCallbacks("setDiscoveryListener",
                            "unsetDiscoveryListener").setRequired(true));
        }

        // DataPacket mux/demux services, which is teh actual engine
        // doing the packet switching
        if (imp.equals(DataPacketMuxDemux.class)) {
            c.setInterface(new String[] { IDataPacketMux.class.getName(),
                    IContainerListener.class.getName(),
                    IInventoryShimExternalListener.class.getName() }, null);

            c.add(createServiceDependency()
                    .setService(IController.class, "(name=Controller)")
                    .setCallbacks("setController", "unsetController")
                    .setRequired(true));
            c.add(createServiceDependency()
                    .setService(IPluginOutDataPacketService.class)
                    .setCallbacks("setPluginOutDataPacketService",
                            "unsetPluginOutDataPacketService")
                    .setRequired(false));
            // See if there is any local packet dispatcher
            c.add(createServiceDependency()
                    .setService(IDataPacketListen.class)
                    .setCallbacks("setIDataPacketListen",
                            "unsetIDataPacketListen").setRequired(false));
        }

        if (imp.equals(InventoryServiceShim.class)) {
            c.setInterface(new String[] { IContainerListener.class.getName(),
                    IStatisticsListener.class.getName()}, null);

            c.add(createServiceDependency()
                    .setService(IController.class, "(name=Controller)")
                    .setCallbacks("setController", "unsetController")
                    .setRequired(true));
            c.add(createServiceDependency()
                    .setService(IInventoryShimInternalListener.class)
                    .setCallbacks("setInventoryShimInternalListener",
                            "unsetInventoryShimInternalListener")
                    .setRequired(true));
            c.add(createServiceDependency()
                    .setService(IInventoryShimExternalListener.class)
                    .setCallbacks("setInventoryShimExternalListener",
                            "unsetInventoryShimExternalListener")
                    .setRequired(false));
        }

        if (imp.equals(TopologyServiceShim.class)) {
            c.setInterface(new String[] { IDiscoveryListener.class.getName(),
                    IContainerListener.class.getName(),
                    IRefreshInternalProvider.class.getName(),
                    IInventoryShimExternalListener.class.getName() }, null);
          c.add(createServiceDependency()
                    .setService(ITopologyServiceShimListener.class)
                    .setCallbacks("setTopologyServiceShimListener",
                            "unsetTopologyServiceShimListener")
                    .setRequired(true));
            c.add(createServiceDependency()
                    .setService(IOFStatisticsManager.class)
                    .setCallbacks("setStatisticsManager",
                            "unsetStatisticsManager").setRequired(false));
        }
    }
}
