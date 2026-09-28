package com.opcua.automation.server;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;

import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

public class MiniFactoryNamespace extends ManagedNamespaceWithLifecycle {

    public static final String NAMESPACE_URI =
            "urn:opcua-automation:mini-factory";

    private final SubscriptionModel subscriptionModel;
    private final ScheduledExecutorService simulator =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "mini-factory-simulator");
                thread.setDaemon(true);
                return thread;
            });

    private UaVariableNode temperature;
    private UaVariableNode motorStatus;
    private UaVariableNode motorSpeed;
    private UaVariableNode startCommand;
    private UaVariableNode stopCommand;
    private UaVariableNode highTemperatureAlarm;

    private static final double HIGH_TEMPERATURE_LIMIT = 90.0;

    public MiniFactoryNamespace(OpcUaServer server) {

        super(server, NAMESPACE_URI);

        subscriptionModel = new SubscriptionModel(server, this);

        getLifecycleManager().addLifecycle(subscriptionModel);

        getLifecycleManager().addStartupTask(
                this::createAddressSpace
        );
        getLifecycleManager().addStartupTask(
                () -> simulator.scheduleAtFixedRate(
                        this::simulateFactory, 1, 1, TimeUnit.SECONDS
                )
        );
    }

    private void createAddressSpace() {

        System.out.println("Creating Mini Factory Address Space...");

        // =====================================================
        // Factory
        // =====================================================

        UaFolderNode factory = new UaFolderNode(
                getNodeContext(),
                new NodeId(
                        getNamespaceIndex(),
                        "Factory"
                ),
                new QualifiedName(
                        getNamespaceIndex(),
                        "Factory"
                ),
                LocalizedText.english("Factory")
        );

        getNodeManager().addNode(factory);


        // =====================================================
        // Add Factory under Objects
        // =====================================================

        UaFolderNode objectsFolder =
                (UaFolderNode) getNodeManager().get(NodeIds.ObjectsFolder);

        if (objectsFolder != null) {
            objectsFolder.addOrganizes(factory);
        }


        // =====================================================
        // Production Line
        // =====================================================

        UaFolderNode productionLine =
                createFolder(
                        factory,
                        "ProductionLine1"
                );


        // =====================================================
        // Temperature
        // =====================================================

        temperature = createVariable(
                productionLine,
                "Temperature",
                NodeIds.Double,
                25.0
        );


        // =====================================================
        // Pressure
        // =====================================================

        createVariable(
                productionLine,
                "Pressure",
                NodeIds.Double,
                4.2
        );


        // =====================================================
        // Motor
        // =====================================================

        UaFolderNode motor =
                createFolder(
                        productionLine,
                        "Motor"
                );

        motorStatus = createVariable(
                motor,
                "Status",
                NodeIds.String,
                "STOPPED"
        );

        motorSpeed = createVariable(
                motor,
                "Speed",
                NodeIds.Int32,
                0
        );

        startCommand = createVariable(motor, "StartCommand", NodeIds.Boolean, false);
        stopCommand = createVariable(motor, "StopCommand", NodeIds.Boolean, false);

        highTemperatureAlarm = createVariable(
                productionLine, "HighTemperatureAlarm", NodeIds.Boolean, false
        );


        // =====================================================
        // Camera
        // =====================================================

        UaFolderNode camera =
                createFolder(
                        productionLine,
                        "Camera"
                );

        createVariable(
                camera,
                "InspectionResult",
                NodeIds.String,
                "PASS"
        );

        createVariable(
                camera,
                "DefectCode",
                NodeIds.String,
                "NONE"
        );


        System.out.println(
                "Mini Factory Address Space Created."
        );
    }

    /** A small deterministic process model for protocol and HMI tests. */
    private void simulateFactory() {
        if (temperature == null) return;

        boolean start = Boolean.TRUE.equals(valueOf(startCommand));
        boolean stop = Boolean.TRUE.equals(valueOf(stopCommand));
        double currentTemperature = ((Number) valueOf(temperature)).doubleValue();
        boolean alarm = currentTemperature >= HIGH_TEMPERATURE_LIMIT;

        if (stop || alarm) {
            set(motorStatus, "STOPPED");
            set(motorSpeed, 0);
        } else if (start) {
            set(motorStatus, "RUNNING");
            set(motorSpeed, 1450);
        }

        boolean running = "RUNNING".equals(valueOf(motorStatus));
        currentTemperature += running ? 8.0 : -2.0;
        currentTemperature = Math.max(25.0, Math.min(110.0, currentTemperature));
        alarm = currentTemperature >= HIGH_TEMPERATURE_LIMIT;

        set(temperature, currentTemperature);
        set(highTemperatureAlarm, alarm);
        if (alarm) {
            set(motorStatus, "STOPPED");
            set(motorSpeed, 0);
        }
        if (stop) set(stopCommand, false);
        if (start && running) set(startCommand, false);
    }

    private Object valueOf(UaVariableNode node) {
        return node.getValue().getValue().getValue();
    }

    private void set(UaVariableNode node, Object value) {
        node.setValue(new DataValue(new Variant(value)));
    }


    // =========================================================
    // Create Folder
    // =========================================================

    private UaFolderNode createFolder(
            UaFolderNode parent,
            String name
    ) {

        String nodeId =
                parent.getNodeId()
                        .getIdentifier()
                        .toString()
                        + "/" + name;

        UaFolderNode folder =
                new UaFolderNode(
                        getNodeContext(),
                        new NodeId(
                                getNamespaceIndex(),
                                nodeId
                        ),
                        new QualifiedName(
                                getNamespaceIndex(),
                                name
                        ),
                        LocalizedText.english(name)
                );

        getNodeManager().addNode(folder);

        parent.addOrganizes(folder);

        return folder;
    }


    // =========================================================
    // Create Variable
    // =========================================================

    private UaVariableNode createVariable(
            UaFolderNode parent,
            String name,
            NodeId dataType,
            Object value
    ) {

        String nodeId =
                parent.getNodeId()
                        .getIdentifier()
                        .toString()
                        + "/" + name;

        UaVariableNode variable =
                new UaVariableNode.UaVariableNodeBuilder(
                        getNodeContext()
                )
                        .setNodeId(
                                new NodeId(
                                        getNamespaceIndex(),
                                        nodeId
                                )
                        )
                        .setAccessLevel(
                                AccessLevel.toValue(AccessLevel.READ_WRITE)
                        )
                        .setUserAccessLevel(
                                AccessLevel.toValue(AccessLevel.READ_WRITE)
                        )
                        .setBrowseName(
                                new QualifiedName(
                                        getNamespaceIndex(),
                                        name
                                )
                        )
                        .setDisplayName(
                                LocalizedText.english(name)
                        )
                        .setDataType(dataType)
                        .setTypeDefinition(
                                NodeIds.BaseDataVariableType
                        )
                        .build();

        variable.setValue(
                new DataValue(
                        new Variant(value)
                )
        );

        getNodeManager().addNode(variable);

        parent.addComponent(variable);

        return variable;
    }


    // =========================================================
    // OPC UA Subscription callbacks
    // =========================================================

    @Override
    public void onDataItemsCreated(
            List<DataItem> dataItems
    ) {
        subscriptionModel.onDataItemsCreated(dataItems);
    }


    @Override
    public void onDataItemsModified(
            List<DataItem> dataItems
    ) {
        subscriptionModel.onDataItemsModified(dataItems);
    }


    @Override
    public void onDataItemsDeleted(
            List<DataItem> dataItems
    ) {
        subscriptionModel.onDataItemsDeleted(dataItems);
    }


    @Override
    public void onMonitoringModeChanged(
            List<MonitoredItem> monitoredItems
    ) {
        subscriptionModel.onMonitoringModeChanged(
                monitoredItems
        );
    }
}
