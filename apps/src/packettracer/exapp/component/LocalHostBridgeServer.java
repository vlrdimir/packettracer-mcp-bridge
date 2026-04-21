package packettracer.exapp.component;

import com.cisco.pt.ipc.system.CepInstance;
import packettracer.exapp.core.AppLogger;
import packettracer.exapp.core.AppRuntimeContext;
import packettracer.exapp.core.ExperimentalReplyService;
import packettracer.exapp.utils.ExperimentalProtocol;
import packettracer.exapp.utils.ExperimentalProtocol.ParsedExperimentalMessage;
import packettracer.exapp.utils.ThrowableUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public final class LocalHostBridgeServer {
    private final CepInstance thisInstance;
    private final ServerSocket serverSocket;
    private final Thread serverThread;
    private final String host;

    private LocalHostBridgeServer(CepInstance thisInstance, ServerSocket serverSocket, String host) {
        this.thisInstance = thisInstance;
        this.serverSocket = serverSocket;
        this.host = host;
        this.serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "packettracer-ptexapp-local-bridge");
        this.serverThread.setDaemon(true);
    }

    public static LocalHostBridgeServer start(CepInstance thisInstance, String host, int minPort, int maxPort) throws IOException {
        IOException lastException = null;

        for (int port = minPort; port <= maxPort; port++) {
            try {
                ServerSocket socket = new ServerSocket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(InetAddress.getByName(host), port));
                socket.setSoTimeout(1000);
                LocalHostBridgeServer bridgeServer = new LocalHostBridgeServer(thisInstance, socket, host);
                bridgeServer.serverThread.start();
                AppLogger.log("Local experimental host bridge bound at %s.", bridgeServer.describeEndpoint());
                return bridgeServer;
            } catch (IOException exception) {
                lastException = exception;
                AppLogger.log("Local experimental host bridge could not bind %s:%d (%s: %s).", host, Integer.valueOf(port), exception.getClass().getName(), ThrowableUtils.safeMessage(exception));
            }
        }

        throw new IOException(String.format("No free localhost bridge port found in %d-%d", Integer.valueOf(minPort), Integer.valueOf(maxPort)), lastException);
    }

    public String describeEndpoint() {
        return host + ":" + serverSocket.getLocalPort();
    }

    public void close() {
        try {
            serverSocket.close();
            AppLogger.log("Closed local experimental host bridge at %s.", describeEndpoint());
        } catch (IOException exception) {
            AppLogger.log("Closing local experimental host bridge failed at %s: %s: %s", describeEndpoint(), exception.getClass().getName(), ThrowableUtils.safeMessage(exception));
        }

        serverThread.interrupt();
    }

    private void acceptLoop() {
        while (AppRuntimeContext.isKeepRunning() && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                handleConnection(socket);
            } catch (SocketTimeoutException exception) {
                continue;
            } catch (IOException exception) {
                if (serverSocket.isClosed()) {
                    break;
                }

                AppLogger.log("Local experimental host bridge accept failed at %s: %s: %s", describeEndpoint(), exception.getClass().getName(), ThrowableUtils.safeMessage(exception));
            }
        }

        AppLogger.log("Local experimental host bridge accept loop ended for %s.", describeEndpoint());
    }

    private void handleConnection(Socket socket) {
        String remoteAddress = String.valueOf(socket.getRemoteSocketAddress());
        AppLogger.log("Local experimental host bridge accepted connection from %s to %s.", remoteAddress, describeEndpoint());

        try {
            socket.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            String requestLine = reader.readLine();

            if (requestLine == null) {
                AppLogger.log("Local experimental host bridge received EOF before a request line from %s.", remoteAddress);
                return;
            }

            AppLogger.log("Local experimental host bridge request from %s: %s", remoteAddress, AppLogger.summarizeForLog(requestLine));
            ParsedExperimentalMessage parsedMessage = ExperimentalProtocol.parse(requestLine);

            if (parsedMessage == null) {
                AppLogger.log("Local experimental host bridge rejected unsupported request from %s; expected single-line %s|handshake, %s|list_devices, %s|list_components, %s|list_ports, %s|list_links, %s|get_device_detail|<selector>, %s|read_interface_status|<selector>, %s|read_port_power_state|<device>|<port>, %s|add_device|<deviceType>|<model>|<x>|<y>, %s|connect_devices|<leftDevice>|<leftPort>|<rightDevice>|<rightPort>|<connectionType?>, %s|set_interface_ip|<device>|<port>|<ip>|<mask>, %s|set_default_gateway|<device>|<gateway>, %s|add_static_route|<device>|<network>|<mask>|<nextHop>|<port?>|<adminDistance?>, %s|run_device_cli|<device>|<mode?>|<command>, %s|set_port_power_state|<device>|<port>|<on|off>, %s|run_ping|<device>|<dest>|<repeat?>|<timeout?>|<size?>|<sourcePort?>, %s|probe_terminal_transcript|<device>|<mode?>|<command>, %s|remove_device|<selector>, %s|delete_link|<linkIndex-or-leftDevice|rightDevice>, %s|get_device_module_layout|<device>, %s|add_module|<device>|<slot>|<moduleType>|<model>, %s|remove_module|<device>|<slot>|<moduleType>, %s|add_module_at|<device>|<parentModulePath>|<slotIndex>|<model>, or %s|echo|<payload>.", remoteAddress, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX, ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX);
                return;
            }

            String replyPayload = ExperimentalReplyService.buildExperimentalReplyPayload(ExperimentalReplyService.safeLocalInstanceId(thisInstance), parsedMessage);
            writer.write(replyPayload);
            writer.newLine();
            writer.flush();
            AppLogger.log(
                "Local experimental host bridge reply to %s: operation=%s bytes=%d preview=%s",
                remoteAddress,
                parsedMessage.getOperation(),
                Integer.valueOf(replyPayload.length()),
                AppLogger.summarizeForLog(replyPayload)
            );
        } catch (IOException exception) {
            AppLogger.log("Local experimental host bridge connection handling failed for %s: %s: %s", remoteAddress, exception.getClass().getName(), ThrowableUtils.safeMessage(exception));
        } finally {
            try {
                socket.close();
            } catch (IOException exception) {
                AppLogger.log("Closing local experimental host bridge socket for %s failed: %s: %s", remoteAddress, exception.getClass().getName(), ThrowableUtils.safeMessage(exception));
            }
        }
    }
}
