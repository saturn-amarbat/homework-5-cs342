import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Server {

    private int clientCount = 1;
    private final TheServer server;
    private final Consumer<Serializable> callback;

    private final Map<String, ClientThread> activeClients = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, ArrayList<String>> groups = Collections.synchronizedMap(new HashMap<>());

    Server(Consumer<Serializable> call) {
        callback = call;
        server = new TheServer();
        server.start();
    }

    public class TheServer extends Thread {
        public void run() {
            try (ServerSocket mySocket = new ServerSocket(5555)) {
                callback.accept("Server listening on port 5555");

                while (true) {
                    ClientThread clientThread = new ClientThread(mySocket.accept(), clientCount++);
                    callback.accept("Incoming connection from client #" + clientThread.id);
                    clientThread.start();
                }
            } catch (Exception e) {
                callback.accept("Server socket failed: " + e.getMessage());
            }
        }
    }

    class ClientThread extends Thread {

        private final Socket connection;
        private final int id;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String username;

        ClientThread(Socket socket, int id) {
            this.connection = socket;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(connection.getOutputStream());
                in = new ObjectInputStream(connection.getInputStream());
                connection.setTcpNoDelay(true);
            } catch (Exception e) {
                callback.accept("Stream setup failed for client #" + id + ": " + e.getMessage());
                disconnectAndCleanup();
                return;
            }

            while (true) {
                try {
                    Object payload = in.readObject();
                    if (!(payload instanceof Message)) {
                        continue;
                    }

                    Message message = (Message) payload;
                    handleMessage(this, message);
                } catch (Exception e) {
                    callback.accept("Client disconnected: " + displayName());
                    disconnectAndCleanup();
                    break;
                }
            }
        }

        String displayName() {
            if (username == null) {
                return "client #" + id;
            }
            return username;
        }

        synchronized boolean send(Message message) {
            try {
                out.writeObject(message);
                out.flush();
                return true;
            } catch (Exception e) {
                disconnectAndCleanup();
                return false;
            }
        }

        void disconnectAndCleanup() {
            String departedUser = username;
            synchronized (Server.this) {
                if (username != null && activeClients.get(username) == this) {
                    activeClients.remove(username);
                    callback.accept(username + " left the server");
                }
            }

            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignored) {
            }
            try {
                connection.close();
            } catch (Exception ignored) {
            }

            if (departedUser != null && !departedUser.isEmpty()) {
                broadcastSystem(departedUser + " left the chat");
            }
            broadcastClientList();
        }
    }

    private synchronized void handleMessage(ClientThread source, Message message) {
        if (message.type == null) {
            return;
        }

        switch (message.type) {
            case LOGIN:
                handleLogin(source, message);
                break;
            case GROUP_CREATE:
                handleGroupCreate(source, message);
                break;
            case GLOBAL_CHAT:
                handleGlobalChat(source, message);
                break;
            case PRIVATE_CHAT:
                handlePrivateChat(source, message);
                break;
            case GROUP_CHAT:
                handleGroupChat(source, message);
                break;
            default:
                break;
        }
    }

    private void handleLogin(ClientThread source, Message message) {
        String desired = "";
        if (message.sender != null) {
            desired = message.sender.trim();
        }

        if (desired.isEmpty()) {
            Message fail = new Message(Message.Type.LOGIN_FAIL, "SERVER", "Username cannot be empty");
            source.send(fail);
            return;
        }

        if (activeClients.containsKey(desired)) {
            Message fail = new Message(Message.Type.LOGIN_FAIL, "SERVER", "Username already in use");
            source.send(fail);
            return;
        }

        source.username = desired;
        activeClients.put(desired, source);

        Message success = new Message(Message.Type.LOGIN_SUCCESS, "SERVER", "Welcome " + desired);
        source.send(success);

        broadcastSystem(desired + " joined the chat");
        broadcastClientList();
    }

    private void handleGroupCreate(ClientThread source, Message message) {
        if (!isAuthed(source)) {
            return;
        }

        String groupName = "";
        if (message.groupName != null) {
            groupName = message.groupName.trim();
        }

        if (groupName.isEmpty()) {
            sendSystem(source, "Group name cannot be empty");
            return;
        }

        if (groups.containsKey(groupName)) {
            sendSystem(source, "Group already exists: " + groupName);
            return;
        }

        ArrayList<String> members = new ArrayList<>();
        members.add(source.username);

        if (message.groupMembers != null) {
            for (int i = 0; i < message.groupMembers.size(); i++) {
                String member = message.groupMembers.get(i);
                if (member == null) {
                    continue;
                }
                String trimmed = member.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!activeClients.containsKey(trimmed)) {
                    continue;
                }
                if (!members.contains(trimmed)) {
                    members.add(trimmed);
                }
            }
        }

        groups.put(groupName, members);
        broadcastSystem(source.username + " created group '" + groupName + "'");
    }

    private void handleGlobalChat(ClientThread source, Message message) {
        if (!isAuthed(source)) {
            return;
        }

        String text = "";
        if (message.textContent != null) {
            text = message.textContent;
        }

        callback.accept("[Global] " + source.username + ": " + text);

        Message outbound = new Message(Message.Type.GLOBAL_CHAT, source.username, text);
        broadcast(outbound);
    }

    private void handlePrivateChat(ClientThread source, Message message) {
        if (!isAuthed(source)) {
            return;
        }

        if (message.recipients == null || message.recipients.isEmpty()) {
            return;
        }

        String text = "";
        if (message.textContent != null) {
            text = message.textContent;
        }

        callback.accept("[Private] " + source.username + " -> " + message.recipients + ": " + text);

        for (int i = 0; i < message.recipients.size(); i++) {
            String username = message.recipients.get(i);
            ClientThread target = activeClients.get(username);
            if (target != null) {
                Message outbound = new Message(Message.Type.PRIVATE_CHAT, source.username, text);
                outbound.recipients.add(username);
                target.send(outbound);
            }
        }

        Message echo = new Message(Message.Type.PRIVATE_CHAT, source.username, text);
        echo.recipients = new ArrayList<>(message.recipients);
        source.send(echo);
    }

    private void handleGroupChat(ClientThread source, Message message) {
        if (!isAuthed(source)) {
            return;
        }

        String groupName = "";
        if (message.groupName != null) {
            groupName = message.groupName.trim();
        }

        if (groupName.isEmpty()) {
            sendSystem(source, "Select a group before sending a group message");
            return;
        }

        ArrayList<String> members = groups.get(groupName);
        if (members == null || members.isEmpty()) {
            sendSystem(source, "Group not found: " + groupName);
            return;
        }

        if (!members.contains(source.username)) {
            sendSystem(source, "You are not in group: " + groupName);
            return;
        }

        String text = "";
        if (message.textContent != null) {
            text = message.textContent;
        }

        callback.accept("[Group " + groupName + "] " + source.username + ": " + text);

        for (int i = 0; i < members.size(); i++) {
            String username = members.get(i);
            ClientThread target = activeClients.get(username);
            if (target != null) {
                Message outbound = new Message(Message.Type.GROUP_CHAT, source.username, text);
                outbound.groupName = groupName;
                target.send(outbound);
            }
        }
    }

    private boolean isAuthed(ClientThread source) {
        if (source.username == null || !activeClients.containsKey(source.username)) {
            Message fail = new Message(Message.Type.LOGIN_FAIL, "SERVER", "Please login first");
            source.send(fail);
            return false;
        }
        return true;
    }

    private void broadcastClientList() {
        Message listUpdate = new Message(Message.Type.CLIENT_LIST_UPDATE, "SERVER", "Online users");
        listUpdate.onlineUsers = new ArrayList<>(activeClients.keySet());
        Collections.sort(listUpdate.onlineUsers);
        broadcast(listUpdate);
    }

    private void broadcast(Message message) {
        ArrayList<String> recipients = new ArrayList<>(activeClients.keySet());
        for (int i = 0; i < recipients.size(); i++) {
            String user = recipients.get(i);
            ClientThread target = activeClients.get(user);
            if (target != null) {
                target.send(message);
            }
        }
    }

    private void sendSystem(ClientThread target, String text) {
        Message notice = new Message(Message.Type.SYSTEM_NOTIFICATION, "SERVER", text);
        target.send(notice);
    }

    private void broadcastSystem(String text) {
        Message notice = new Message(Message.Type.SYSTEM_NOTIFICATION, "SERVER", text);
        broadcast(notice);
    }
}
