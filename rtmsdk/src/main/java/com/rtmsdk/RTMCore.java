package com.rtmsdk;

import com.fpnn.sdk.ConnectionWillCloseCallback;
import com.fpnn.sdk.ErrorCode;
import com.fpnn.sdk.FunctionalAnswerCallback;
import com.fpnn.sdk.TCPClient;
import com.fpnn.sdk.proto.Answer;
import com.fpnn.sdk.proto.Quest;
import com.rtmsdk.UserInterface.ErroeCodeCallback;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class RTMCore {
    public enum ClientStatus {
        Closed,
        Connecting,
        Connected
    }

    class HostAddress {
        public String ipv6;
        public int port;

        public String endpoint() {
            return ipv6 + ":" + port;
        }
    }

    //-------------[ Fields ]--------------------------//
    private Object interLocker;
    private long pid;
    private long uid;
    private String token;
    private String lang;
    private String curve;
    private byte[] encrptyData;

    private ClientStatus status = ClientStatus.Closed;
    private boolean authSstatus = false;
    private AtomicBoolean running = new AtomicBoolean(true);
    private AtomicBoolean initCheckThread = new AtomicBoolean(false);

    private RTMQuestProcessor processor;
    private TCPClient dispatch;
    private TCPClient rtmGate;
    private Map<String, Map<TCPClient, Long>> fileGates;
    private ArrayList<String> rtmGateEndpoints;
    private Thread checkThread;
    protected com.fpnn.sdk.ErrorRecorder errorRecorder = null;

    protected void RTMInit(String endpoint, long pid, long uid, IRTMQuestProcessor serverPushProcessor) {
        this.pid = pid;
        this.uid = uid;

        interLocker = new Object();
        fileGates = new HashMap<>();

        processor = new RTMQuestProcessor();
        processor.SetProcessor(serverPushProcessor);

        dispatch = TCPClient.create(endpoint, true);
        dispatch.connectTimeout = RTMConfig.globalConnectTimeoutSeconds;
        dispatch.setQuestTimeout(RTMConfig.globalFileQuestTimeoutSeconds);
    }

    public void setErrorRecoder(com.fpnn.sdk.ErrorRecorder value){
        if (errorRecorder == null)
            return;
        synchronized (interLocker) {
            errorRecorder = value;
            processor.SetErrorRecorder(value);
            dispatch.SetErrorRecorder(value);
        }
    }

    public void enableEncryptorByDerData(String curve, byte[] peerPublicKey) {
        this.curve = curve;
        encrptyData = peerPublicKey;
    }

    //-------------[ Fack Fields ]--------------------------//

    protected long getPid() {
        return pid;
    }

    protected long getUid() {
        return uid;
    }

    protected String getToken() {
        return token;
    }

    synchronized protected ClientStatus getClientStatus() {
        return status;
    }

    synchronized protected boolean getAuthStatus() {
        return authSstatus;
    }

    private boolean connectionIsAlive() {
        return processor.ConnectionIsAlive();
    }

    private TCPClient getCoreClient() {
        synchronized (interLocker) {
            if (status == ClientStatus.Connected)
                return rtmGate;
            else
                return null;
        }
    }

    protected Answer sendQuest(Quest quest, int timeout) {
        TCPClient client = getCoreClient();
        if (client == null)
            return null;

        Answer answer = null;
        try {
            answer = client.sendQuest(quest, timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return answer;
    }

    protected void sayBye(boolean async) {
        final TCPClient client = getCoreClient();
        if (client == null) {
            close();
            return;
        }
        Quest quest = new Quest("bye");
        if (async) {
            boolean success = sendQuest(quest, new FunctionalAnswerCallback() {
                @Override
                public void onAnswer(Answer answer, int errorCode) {
                    close();
                }
            }, 0);
            if (!success)
                close();
        } else {
            try {
                client.sendQuest(quest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            close();
        }
    }

    protected boolean sendQuest(Quest quest, FunctionalAnswerCallback callback, int timeout) {
        TCPClient client = getCoreClient();
        if (client == null)
            return false;
        return client.sendQuest(quest, callback, timeout);
    }

    protected boolean sendQuest(final ErroeCodeCallback callback, Quest quest, int timeout) {
        return sendQuest(quest, new FunctionalAnswerCallback() {
            @Override
            public void onAnswer(Answer answer, int errorCode) {
                callback.call(errorCode);
            }
        }, timeout);
    }

    protected int checkAnswer(Answer answer) {
        if (answer == null)
            return ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value();

        if (answer.isErrorAnswer())
            return answer.getErrorCode();
        return ErrorCode.FPNN_EC_OK.value();
    }

    protected int sendQuestCode(Quest quest, int timeout) {
        Answer answer = sendQuest(quest, timeout);
        if (answer == null)
            return ErrorCode.FPNN_EC_PROTO_UNKNOWN_ERROR.value();
        return answer.getErrorCode();
    }

    protected void activeFileGateClient(String endpoint, final TCPClient client) {
        synchronized (interLocker) {
            if (fileGates.containsKey(endpoint))
                fileGates.get(endpoint).put(client, RTMUtils.getCurrentSeconds());
            else
                fileGates.put(endpoint, new HashMap<TCPClient, Long>() {{
                    put(client, RTMUtils.getCurrentSeconds());
                }});
        }
    }

    protected TCPClient fecthFileGateClient(String endpoint) {
        synchronized (interLocker) {
            if (fileGates.containsKey(endpoint)) {
                for (TCPClient client : fileGates.get(endpoint).keySet())
                    return client;
            }
        }
        return null;
    }

    private void checkRoutineInit() {
        if (initCheckThread.get() || !running.get())
            return;

        checkThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running.get()) {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        status = ClientStatus.Closed;
                        rtmGate.close();
                        return;
                    }

                    if (status != ClientStatus.Closed && !connectionIsAlive()) {
                        status = ClientStatus.Closed;
                        rtmGate.close();
                    }
                }
            }
        });
        checkThread.setName("RTM.ThreadCheck");
        checkThread.setDaemon(true);
        checkThread.start();

        initCheckThread.set(true);
        running.set(true);
    }

    //-------------[ Auth(Login) utilies functions ]--------------------------//
    private String ConvertIPv4ToIPv6(String ipv4) {
        String[] parts = ipv4.split("\\.");
        if (parts.length != 4)
            return "";

        for (String part : parts) {
            int partInt = Integer.parseInt(part);
            if (partInt > 255 || partInt < 0)
                return "";
        }

        String part7 = Integer.toHexString(Integer.parseInt(parts[0]) * 256 + Integer.parseInt(parts[1]));
        String part8 = Integer.toHexString(Integer.parseInt(parts[2]) * 256 + Integer.parseInt(parts[3]));
        return "64:ff9b::" + part7 + ":" + part8;
    }

    protected boolean ConvertIPv4EndpointToIPv6IPPort(String ipv4endpoint, HostAddress ipv6address) {
        int idx = ipv4endpoint.lastIndexOf(":");
        if (idx == -1) {
            ipv6address.ipv6 = "";
            ipv6address.port = 0;

            return false;
        }
        String ipv4 = ipv4endpoint.substring(0, idx);
        String strPort = ipv4endpoint.substring(idx + 1);
        ipv6address.port = Integer.parseInt(strPort, 10);
        ipv6address.ipv6 = ConvertIPv4ToIPv6(ipv4);

        if (ipv6address.ipv6.length() == 0)
            return false;

        return true;
    }

    private void ConfigRtmGateClient(TCPClient client, int timeout) {
        client.connectTimeout = RTMConfig.globalConnectTimeoutSeconds;
        client.setQuestTimeout(RTMConfig.globalQuestTimeoutSeconds);

        if (encrptyData != null && curve!=null && !curve.equals(""))
            client.enableEncryptorByDerData(curve, encrptyData);

        if (errorRecorder != null)
            client.SetErrorRecorder(errorRecorder);

        client.setQuestProcessor(processor, "com.rtmsdk.RTMQuestProcessor");
        processor.setAnswerTCPclient(client);
/*        client.setConnectedCallback(new ConnectionConnectedCallback() {
            @Override
            public void connectResult(InetSocketAddress peerAddress, boolean connected) {
            }
        });*/
        client.setWillCloseCallback(new ConnectionWillCloseCallback() {
            @Override
            public void connectionWillClose(InetSocketAddress peerAddress, boolean causedByError) {
                closeStatus();
                processor.sessionClosed(causedByError ? ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value() : ErrorCode.FPNN_EC_OK.value());
            }
        });
    }

    //-------------[ Auth(Login) processing functions ]--------------------------//
    private boolean AsyncFetchRtmGateEndpoint(String addressType, FunctionalAnswerCallback callback, int timeout) {
        Quest quest = new Quest("which");
        quest.param("what", "rtmGated");
        quest.param("addrType", addressType);
        quest.param("proto", "tcp");

        return dispatch.sendQuest(quest, callback, timeout);
    }

    private int auth(String token, Map<String, String> attr) throws InterruptedException {
        return auth(token, attr, false);
    }

    private int auth(String token, Map<String, String> attr, boolean retry) throws InterruptedException {
        Quest qt = new Quest("auth");
        qt.param("pid", pid);
        qt.param("uid", uid);
        qt.param("token", token);
        qt.param("lang", lang);
        if (attr != null)
            qt.param("attrs", attr);

        Answer answer = rtmGate.sendQuest(qt);

        if (answer.getErrorCode() != ErrorCode.FPNN_EC_OK.value()) {
            closeStatus();
            return RTMErrorCode.RTM_EC_AUTH_FAILED.value();
        }
        else if (!answer.wantBoolean("ok")) {
            if (retry){
                closeStatus();
                return RTMErrorCode.RTM_EC_AUTH_RETRY_FAILED.value();
            }
            String endpoint = (String) answer.get("gate", "");
            if (endpoint.equals("")) {
                closeStatus();
                return RTMErrorCode.RTM_EC_UNAUTHORIZED.value();
            } else {
                rtmGate = TCPClient.create(endpoint);
                auth(token, attr, true);
            }
        }
        synchronized (interLocker) {
            status = ClientStatus.Connected;
        }

        return ErrorCode.FPNN_EC_OK.value();
    }

    private boolean auth(ErroeCodeCallback callback,String token, Map<String, String> attr) throws InterruptedException {
        return auth(callback, token, attr, false);
    }

    private boolean auth(final ErroeCodeCallback callback, final String token, final Map<String, String> attr, final boolean retry) {
        Quest qt = new Quest("auth");
        qt.param("pid", pid);
        qt.param("uid", uid);
        qt.param("token", token);
        qt.param("lang", lang);
        if (attr != null)
            qt.param("attrs", attr);


        return rtmGate.sendQuest(qt, new FunctionalAnswerCallback() {
            @Override
            public void onAnswer(Answer answer, int errorCode) {
                if (errorCode != ErrorCode.FPNN_EC_OK.value()) {
                    closeStatus();
                    callback.call(errorCode);
                } else if (!answer.wantBoolean("ok")) {
                    if (retry) {
                        closeStatus();
                        callback.call(RTMErrorCode.RTM_EC_AUTH_RETRY_FAILED.value());
                    } else {
                        String endpoint = (String) answer.get("gate", "");
                        if (endpoint.equals("")) {
                            closeStatus();
                            callback.call(RTMErrorCode.RTM_EC_AUTH_FAILED.value());
                        } else {
                            rtmGate = TCPClient.create(endpoint);
                            auth(callback, token, attr, true);
                        }
                    }
                }
                else{
                    synchronized (interLocker) {
                        status = ClientStatus.Connected;
                    }
                    callback.call(errorCode);
                }
                return;
            }
        }, 0);
    }

    protected boolean login(final ErroeCodeCallback callback, final String token, final String lang, final String addressType, final Map<String, String> attr) {
        synchronized (interLocker) {
            if (status == ClientStatus.Connected || status == ClientStatus.Connecting) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        callback.call(ErrorCode.FPNN_EC_OK.value());
                    }
                }).start();
                return true;
            }
            status = ClientStatus.Connecting;
        }

        return AsyncFetchRtmGateEndpoint(addressType, new FunctionalAnswerCallback() {
            @Override
            public void onAnswer(Answer answer, int errorCode) {
                if (errorCode != ErrorCode.FPNN_EC_OK.value())
                    callback.call(errorCode);
                else {
                    String endpoint = (String) answer.get("endpoint");
                    if (endpoint.equals("")) {
                        callback.call(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value());
                    } else {
                        rtmGate = TCPClient.create(endpoint);
                        ConfigRtmGateClient(rtmGate, RTMConfig.globalQuestTimeoutSeconds);
                        checkRoutineInit();
                        try {
                            auth(callback, token, attr);
                        }
                        catch (InterruptedException e) {
                            close();
                            return;
                        }
                    }
                }
            }
        }, RTMConfig.globalQuestTimeoutSeconds);
    }

    private  void closeStatus()
    {
        synchronized (interLocker) {
            status = ClientStatus.Closed;
        }
    }

    protected int login(String token, String lang, Map<String, String> attr,  String addressType) {
        this.lang = lang.equals("") ? Locale.getDefault().getLanguage() : lang;

        synchronized (interLocker) {
            if (status == ClientStatus.Connected || status == ClientStatus.Connecting)
                return ErrorCode.FPNN_EC_OK.value();

            status = ClientStatus.Connecting;
        }
        Quest quest = new Quest("which");
        quest.param("what", "rtmGated");
        quest.param("addrType", addressType);
        quest.param("proto", "tcp");
        Answer answer = null;
        try {
            answer = dispatch.sendQuest(quest);
            if (answer.getErrorCode() != ErrorCode.FPNN_EC_OK.value()) {
                closeStatus();
                return RTMErrorCode.RTM_EC_DISPATCH_FAILED.value();
            }

            String endpoint = (String) answer.get("endpoint");
            if (endpoint.equals("")) {
                Quest qst = new Quest("whichall");
                qst.param("what", "rtmGated");
                qst.param("addrType", addressType);
                qst.param("proto", "tcp");

                answer = dispatch.sendQuest(quest);

                if (answer.getErrorCode() != ErrorCode.FPNN_EC_OK.value()) {
                    closeStatus();
                    return RTMErrorCode.RTM_EC_DISPATCH_FAILED.value();
                }
                rtmGateEndpoints = (ArrayList<String>) answer.get("endpoints");
                if (rtmGateEndpoints.equals("")) {
                    closeStatus();
                    return ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value();
                }
                rtmGate = TCPClient.create(rtmGateEndpoints.get(0));
            } else
                rtmGate = TCPClient.create(endpoint);

            ConfigRtmGateClient(rtmGate, RTMConfig.globalQuestTimeoutSeconds);
            checkRoutineInit();
            return auth(token, attr);
        } catch (InterruptedException e) {
            closeStatus();
            Thread.currentThread().interrupt();
            return ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value();
        }
    }

    protected void close() {
        synchronized (interLocker) {
            if (status == ClientStatus.Closed)
                return;
            status = ClientStatus.Closed;
        }
        if (rtmGate !=null)
            rtmGate.close();
        running.set(false);
    }
}