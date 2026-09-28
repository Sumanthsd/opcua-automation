package com.opcua.automation.server;

import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;

import java.util.LinkedHashSet;
import java.util.Set;

public class MiniFactoryServer {

    public static void main(String[] args) throws Exception {

        int port = 4840;

        Set<EndpointConfig> endpoints = new LinkedHashSet<>();

        EndpointConfig endpoint = EndpointConfig.newBuilder()
                .setBindAddress("0.0.0.0")
                .setHostname(HostnameUtil.getHostname())
                .setPath("/factory")
                .setBindPort(port)
                .setCertificate(() -> null)
                .setSecurityPolicy(SecurityPolicy.None)
                .setSecurityMode(MessageSecurityMode.None)
                .addTokenPolicies(
                        OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS
                )
                .setTransportProfile(
                        TransportProfile.TCP_UASC_UABINARY
                )
                .build();

        endpoints.add(endpoint);

        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
                .setApplicationName(
                        LocalizedText.english("Mini Factory OPC UA Server")
                )
                .setApplicationUri(
                        "urn:opcua-automation:mini-factory"
                )
                .setProductUri(
                        "urn:opcua-automation:mini-factory"
                )
                .setEndpoints(endpoints)
                .setIdentityValidator(
                        AnonymousIdentityValidator.INSTANCE
                )
                .build();

        OpcUaServer server = new OpcUaServer(
                serverConfig,
                transportProfile -> {

                    OpcTcpServerTransportConfig transportConfig =
                            OpcTcpServerTransportConfig.newBuilder()
                                    .build();

                    return new OpcTcpServerTransport(transportConfig);
                }
        );

        MiniFactoryNamespace namespace =
                new MiniFactoryNamespace(server);

        server.startup().get();

        System.out.println("------------------------------------------");
        System.out.println(" Mini Factory OPC UA Server Started");
        System.out.println("------------------------------------------");
        System.out.println(
                "Endpoint: opc.tcp://"
                        + HostnameUtil.getHostname()
                        + ":"
                        + port
                        + "/factory"
        );
        System.out.println("------------------------------------------");

        Thread.currentThread().join();
    }
}