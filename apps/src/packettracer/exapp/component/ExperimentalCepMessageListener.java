package packettracer.exapp.component;

import com.cisco.pt.UUID;
import com.cisco.pt.ipc.events.CepInstanceEvent;
import com.cisco.pt.ipc.events.CepInstanceEventListener;
import com.cisco.pt.ipc.system.CepInstance;
import com.cisco.pt.ipc.system.IPCManager;
import packettracer.exapp.core.AppLogger;
import packettracer.exapp.core.ExperimentalReplyService;
import packettracer.exapp.utils.ExperimentalProtocol;
import packettracer.exapp.utils.ExperimentalProtocol.ParsedExperimentalMessage;
import packettracer.exapp.utils.ThrowableUtils;

public final class ExperimentalCepMessageListener implements CepInstanceEventListener {
    private final IPCManager ipcManager;
    private final CepInstance thisInstance;

    public ExperimentalCepMessageListener(IPCManager ipcManager, CepInstance thisInstance) {
        this.ipcManager = ipcManager;
        this.thisInstance = thisInstance;
    }

    @Override
    public void handleEvent(CepInstanceEvent event) throws Exception {
        if (!(event instanceof CepInstanceEvent.MessageReceived)) {
            return;
        }

        CepInstanceEvent.MessageReceived messageReceived = (CepInstanceEvent.MessageReceived) event;
        handleInboundMessage(messageReceived.srcCepId, messageReceived.srcCepInstanceId, messageReceived.message, true);
    }

    public void handleSyntheticMessage(String senderAppId, UUID senderInstanceId, String payload) {
        handleInboundMessage(senderAppId, senderInstanceId, payload, false);
    }

    private void handleInboundMessage(String senderAppId, UUID senderInstanceId, String payload, boolean allowReplySend) {
        AppLogger.log(
            "MESSAGE_RECEIVED inbound details: senderAppId=%s senderInstanceId=%s payload=%s",
            describeSenderAppId(senderAppId),
            describeSenderInstanceId(senderInstanceId),
            payload == null ? "null" : AppLogger.summarizeForLog(payload)
        );

        ParsedExperimentalMessage parsedMessage = ExperimentalProtocol.parse(payload);

        if (parsedMessage == null) {
            AppLogger.log("Ignoring inbound message because it does not match the supported local experimental operation format.");
            return;
        }

        String replyPayload = buildReplyPayload(parsedMessage);

        if (!allowReplySend) {
            AppLogger.log(
                "Synthetic listener exercise generated reply payload without calling IPCManager send methods: operation=%s bytes=%d preview=%s",
                parsedMessage.getOperation(),
                Integer.valueOf(replyPayload.length()),
                AppLogger.summarizeForLog(replyPayload)
            );
            return;
        }

        boolean sent = sendReply(senderAppId, senderInstanceId, replyPayload);
        AppLogger.log("Reply send result for %s: %s", parsedMessage.getOperation(), Boolean.valueOf(sent));
    }

    private boolean sendReply(String senderAppId, UUID senderInstanceId, String replyPayload) {
        if (ipcManager == null) {
            AppLogger.log("Cannot send reply because IPCManager is unavailable.");
            return false;
        }

        if (senderInstanceId != null) {
            try {
                boolean sent = ipcManager.sendMessageToInstance(senderInstanceId, replyPayload);

                if (sent) {
                    AppLogger.log("Sent reply with IPCManager.sendMessageToInstance(...) to sender instance %s.", senderInstanceId);
                    return true;
                }

                AppLogger.log("IPCManager.sendMessageToInstance(...) returned false for sender instance %s; trying sender app ID fallback if available.", senderInstanceId);
            } catch (Throwable throwable) {
                AppLogger.log("IPCManager.sendMessageToInstance(...) threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
            }
        }

        if (senderAppId != null && !senderAppId.isEmpty()) {
            try {
                boolean sent = ipcManager.sendMessageTo(senderAppId, replyPayload);

                if (sent) {
                    AppLogger.log("Sent reply with IPCManager.sendMessageTo(...) to sender app %s.", senderAppId);
                } else {
                    AppLogger.log("IPCManager.sendMessageTo(...) returned false for sender app %s.", senderAppId);
                }

                return sent;
            } catch (Throwable throwable) {
                AppLogger.log("IPCManager.sendMessageTo(...) threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
                return false;
            }
        }

        AppLogger.log("Cannot send reply because the inbound MESSAGE_RECEIVED event did not expose a sender app ID or instance ID.");
        return false;
    }

    private String buildReplyPayload(ParsedExperimentalMessage parsedMessage) {
        return ExperimentalReplyService.buildExperimentalReplyPayload(ExperimentalReplyService.safeLocalInstanceId(thisInstance), parsedMessage);
    }

    private String describeSenderAppId(String senderAppId) {
        return senderAppId == null || senderAppId.isEmpty() ? "missing" : senderAppId;
    }

    private String describeSenderInstanceId(UUID senderInstanceId) {
        return senderInstanceId == null ? "missing" : senderInstanceId.toString();
    }
}
