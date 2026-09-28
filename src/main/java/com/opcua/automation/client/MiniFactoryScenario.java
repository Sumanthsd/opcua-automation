package com.opcua.automation.client;

import java.util.List;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;

/** Smoke test for the virtual factory. Start MiniFactoryServer first. */
public final class MiniFactoryScenario {
    private static final String ENDPOINT = "opc.tcp://localhost:4840/factory";
    private static final String NAMESPACE_URI =
            "urn:opcua-automation:mini-factory";

    public static void main(String[] args) throws Exception {
        // This endpoint is configured for SecurityPolicy.None and anonymous access.
        // Milo selects the matching endpoint and identity policy automatically.
        OpcUaClient client = OpcUaClient.create(ENDPOINT);

        try {
            client.connect();
            // The server assigns namespace indexes at runtime. Resolve ours
            // from the NamespaceArray instead of assuming it is always 2.
            Number namespaceNumber = client.getNamespaceTable()
                    .getIndex(NAMESPACE_URI);
            if (namespaceNumber == null) {
                throw new IllegalStateException(
                        "Mini Factory namespace was not found: " + NAMESPACE_URI);
            }
            int namespaceIndex = namespaceNumber.intValue();

            NodeId start = node(namespaceIndex, "Factory/ProductionLine1/Motor/StartCommand");
            NodeId status = node(namespaceIndex, "Factory/ProductionLine1/Motor/Status");
            NodeId speed = node(namespaceIndex, "Factory/ProductionLine1/Motor/Speed");
            NodeId temperature = node(namespaceIndex, "Factory/ProductionLine1/Temperature");
            NodeId alarm = node(namespaceIndex, "Factory/ProductionLine1/HighTemperatureAlarm");

            write(client, start, true);
            System.out.println("StartCommand written: true");

            boolean alarmSeen = false;
            for (int i = 0; i < 20; i++) {
                Object temp = read(client, temperature);
                Object motorStatus = read(client, status);
                Object motorSpeed = read(client, speed);
                Object highTemperature = read(client, alarm);
                System.out.printf("temperature=%s, status=%s, speed=%s, alarm=%s%n",
                        temp, motorStatus, motorSpeed, highTemperature);
                alarmSeen |= Boolean.TRUE.equals(highTemperature);
                if (alarmSeen && "STOPPED".equals(motorStatus)
                        && Integer.valueOf(0).equals(motorSpeed)) break;
                Thread.sleep(1000);
            }

            if (!alarmSeen || !"STOPPED".equals(read(client, status))
                    || !Integer.valueOf(0).equals(read(client, speed))) {
                throw new AssertionError("High-temperature shutdown was not observed");
            }
            System.out.println("PASS: high-temperature alarm stopped the motor.");
        } finally {
            client.disconnect();
        }
    }

    private static NodeId node(int namespaceIndex, String identifier) {
        return new NodeId(namespaceIndex, identifier);
    }

    private static Object read(OpcUaClient client, NodeId node) throws Exception {
        DataValue value = client.readValue(0, TimestampsToReturn.Both, node);
        return value.getValue().getValue();
    }

    private static void write(OpcUaClient client, NodeId node, Object value) throws Exception {
        client.writeValues(
                List.of(node),
                List.of(new DataValue(new Variant(value)))
        );
    }

    private MiniFactoryScenario() { }
}
