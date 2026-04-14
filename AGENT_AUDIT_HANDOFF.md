# HW5 Audit + Technical Handoff (for independent agent review)

## 1) Scope and purpose

This document is a factual handoff for independent review by additional agents (Gemini/Claude).
It describes:

- Current architecture and runtime behavior.
- What changed during this session compared to starter-style baseline.
- Audit results against the formal HW5 PDF requirements.
- Reproducible build/run commands for manual TA review.
- Known caveats and what is intentionally out-of-scope.

Repository and commit provenance:

- Repo: https://github.com/saturn-amarbat/homework-5-cs342
- Branch: main
- Head commit: b89f5f7
- Commit message: HW5 submission: client-server messaging app with groups

## 2) Project structure summary

Two Maven modules:

- HW5Server
  - src/main/java/GuiServer.java
  - src/main/java/Server.java
  - src/main/java/Message.java
- HW5Client
  - src/main/java/GuiClient.java
  - src/main/java/Client.java
  - src/main/java/Message.java

Build system:

- Maven
- Java target/source 11
- JavaFX dependencies in both modules

## 3) Runtime architecture and component interactions

### 3.1 Server side

- GuiServer starts JavaFX app and creates Server instance.
- Server starts TheServer thread.
- TheServer opens ServerSocket on port 5555 and accepts inbound sockets.
- For each accepted socket, server creates one ClientThread.
- Each ClientThread:
  - Owns ObjectInputStream and ObjectOutputStream.
  - Continuously reads Message objects from client.
  - On read failure/connection loss, runs disconnect cleanup and exits.

Core server state:

- activeClients: Map<String, ClientThread>
  - Key is username, value is per-client thread.
- groups: Map<String, ArrayList<String>>
  - Key is group name, value is member username list.

Message dispatch on server:

- LOGIN -> handleLogin
- GLOBAL_CHAT -> handleGlobalChat
- PRIVATE_CHAT -> handlePrivateChat
- GROUP_CREATE -> handleGroupCreate
- GROUP_CHAT -> handleGroupChat

### 3.2 Client side

- GuiClient starts JavaFX app and creates Client networking object.
- Client.connect(host, port):
  - Opens socket and object streams.
  - Spawns dedicated network thread (readLoop).
- readLoop continuously receives Message objects and pushes them via callback.
- GuiClient routes callbacks through Platform.runLater so UI updates occur on JavaFX thread.

Client UI states:

- Login scene:
  - IP input, port input, username input.
  - Connect button sends LOGIN request.
- Chat scene:
  - Online users list (multi-select enabled).
  - Chat log list.
  - Input + buttons for:
    - global send
    - private send
    - create group
    - group send

### 3.3 Shared protocol (Message class in both modules)

Both sides have matching Message schema and enum values.
Serializable fields include:

- type
- sender
- recipients
- textContent
- onlineUsers
- groupName
- groupMembers

Type enum includes:

- LOGIN, LOGIN_SUCCESS, LOGIN_FAIL
- SYSTEM_NOTIFICATION
- CLIENT_LIST_UPDATE
- GLOBAL_CHAT, PRIVATE_CHAT
- GROUP_CREATE, GROUP_CHAT

## 4) Behavior details by feature

### 4.1 Login + unique username enforcement

- Client sends LOGIN with desired username in sender.
- Server validates:
  - non-empty username
  - not already in activeClients
- On failure: LOGIN_FAIL with reason.
- On success: LOGIN_SUCCESS, user inserted in activeClients, system join notification broadcast, online user list broadcast.

### 4.2 Online user list updates

- Server sends CLIENT_LIST_UPDATE with sorted active username list.
- Broadcast occurs after login and disconnect.
- Client receives and replaces online users list.

### 4.3 Global chat

- Client sends GLOBAL_CHAT with textContent.
- Server verifies sender is authenticated.
- Server broadcasts GLOBAL_CHAT to all active clients.

### 4.4 Private chat

- Client selects one online user and sends PRIVATE_CHAT with recipients list.
- Server verifies sender is authenticated.
- Server forwards PRIVATE_CHAT to each listed recipient currently online.
- Server also echoes PRIVATE_CHAT back to sender with recipient list.

### 4.5 Group create and group chat

- Group create:
  - Client sends GROUP_CREATE with groupName and selected members.
  - Server validates groupName and uniqueness.
  - Server auto-includes creator as member.
  - Server ignores invalid/offline names.
  - Server stores group in groups map and broadcasts system notification.
- Group chat:
  - Client sends GROUP_CHAT with groupName and text.
  - Server validates group exists and sender is member.
  - Server fans out GROUP_CHAT to members currently online.

### 4.6 Disconnect cleanup

- On stream/socket exception in ClientThread:
  - Remove user from activeClients if mapped.
  - Close streams and socket.
  - Broadcast system leave notification.
  - Broadcast updated client list.

### 4.7 Red system notifications in client UI

- Client chat log is a ListView with custom cellFactory.
- Any line beginning with [System] is rendered Color.FIREBRICK.
- Triggered by:
  - server join/leave notifications
  - group-create notifications
  - client-side validation warnings shown as system lines

## 5) What changed in this session (delta log)

High-level baseline at session start:

- Already had login, online user list updates, global/private chat, disconnect cleanup.
- Did not satisfy formal PDF group requirements (create groups + group-targeted messaging).

Changes made in session:

1. Extended Message protocol in both modules

- Added enum values: SYSTEM_NOTIFICATION, GROUP_CREATE, GROUP_CHAT
- Added fields: groupName, groupMembers

2. Extended server logic

- Added groups map
- Added handlers:
  - handleGroupCreate
  - handleGroupChat
- Added helper methods:
  - sendSystem
  - broadcastSystem
- Replaced join/leave plain chat notices with SYSTEM_NOTIFICATION broadcasts

3. Extended client UI and message handling

- Replaced text area log with ListView-based log for colorized rows
- Added system message coloring (red)
- Added groupName input + Create Group + Send Group controls
- Added handling for SYSTEM_NOTIFICATION and GROUP_CHAT

4. Submission hygiene actions

- Built and tested both modules successfully
- Ran clean to remove target/.class artifacts before submission preparation
- Pushed only HW5Client and HW5Server into repo commit b89f5f7

## 6) Formal PDF requirement audit result

Source PDF in workspace:

- hw5 guide read it for requirements.pdf

Extracted text summary used for checks:

- Requires Message object protocol instead of String socket payloads.
- Requires username handling, online users view, all/individual messaging.
- Requires server tracking of groups and usernames.
- Requires Message support for group operations.
- Recommends clean build before submission to avoid unnecessary .class files in submitted package.

Final audit status:

- PASS: Message object protocol over sockets
- PASS: Login + duplicate-name rejection
- PASS: Online users list updates
- PASS: Global chat
- PASS: Private one-to-one chat
- PASS: Group create and group-targeted chat
- PASS: Disconnect cleanup behavior
- PASS: Maven build/test readiness
- PASS: Thread-safe UI updates via Platform.runLater

## 7) Evidence pointers for independent agents

Server:

- Message dispatch and feature handlers: HW5Server/src/main/java/Server.java
- Per-client socket thread model: HW5Server/src/main/java/Server.java
- Group tracking map and operations: HW5Server/src/main/java/Server.java
- System notifications: HW5Server/src/main/java/Server.java

Client:

- Network thread + callbacks: HW5Client/src/main/java/Client.java
- Platform.runLater UI handoff: HW5Client/src/main/java/GuiClient.java
- UI controls for global/private/group send: HW5Client/src/main/java/GuiClient.java
- Red system notification rendering: HW5Client/src/main/java/GuiClient.java

Protocol:

- Shared serializable schema and matching enum: HW5Client/src/main/java/Message.java and HW5Server/src/main/java/Message.java

Build:

- Maven config and dependencies: HW5Client/pom.xml and HW5Server/pom.xml

## 8) Manual run instructions for TA demonstration

Prereqs:

- JDK 11+ installed and on PATH
- Maven installed and on PATH
- OS: Windows PowerShell (commands below are PowerShell-friendly)

Start server:

1. Set-Location "C:/Users/sanch/OneDrive/Desktop/HW5_submiss/HW5Server"
2. mvn --% -Dexec.mainClass=GuiServer org.codehaus.mojo:exec-maven-plugin:3.1.0:java

Start client #1:

1. Open a second terminal
2. Set-Location "C:/Users/sanch/OneDrive/Desktop/HW5_submiss/HW5Client"
3. mvn --% -Dexec.mainClass=GuiClient org.codehaus.mojo:exec-maven-plugin:3.1.0:java

Start client #2:

1. Open third terminal
2. Repeat the same client command

Why --% is used:

- In PowerShell, --% avoids argument-parsing issues with -Dexec.mainClass.

Demo script for manual review:

1. Login as alice and bob on two clients (127.0.0.1:5555)
2. Show duplicate login rejection by trying alice again in a third client
3. Send global message from alice and show receipt on bob
4. Send private message from alice to bob only
5. Create group (for example group1) selecting both users
6. Send group message and show group formatting in chat
7. Close bob client and show red [System] leave notification + online list update

## 9) What reviewers/agents should know (important context)

- This is intentionally a simplified in-memory chat app.
- No persistence/database was added (consistent with assignment scope).
- No checkers gameplay logic is present (not required for this HW5).
- Group membership exists only in server memory; server restart resets groups.
- Group list is not currently displayed in dedicated UI panel; group operations are via group name input field.
- Group name uniqueness is exact string match after trim, case-sensitive.
- Server has a synchronizedMap for active clients/groups; this is sufficient for assignment scale.

## 10) Known non-blocking caveats

- JavaFX warning may appear at startup in some environments about unsupported configuration; app still runs.
- The client online users list supports multi-select to form group member list during group creation.
- Private messaging currently uses the first selected user for private send button behavior.

## 11) Clean submission packaging guidance

If creating a zip for LMS/Blackboard submission:

- Include only:
  - HW5Client
  - HW5Server
- Exclude local tooling/artifacts:
  - .idea
  - .vscode
  - target folders and .class files (run mvn clean first)
  - local audit helper files

Commands used before packaging:

- mvn -f "C:/Users/sanch/OneDrive/Desktop/HW5_submiss/HW5Server/pom.xml" clean
- mvn -f "C:/Users/sanch/OneDrive/Desktop/HW5_submiss/HW5Client/pom.xml" clean

## 12) Honesty and verification stance

This handoff is intentionally explicit and reproducible.
Independent agents should verify claims by:

- Reading the referenced files.
- Re-running Maven clean/compile/test.
- Performing the manual run/demo steps above.
- Comparing against the formal PDF requirements text.
