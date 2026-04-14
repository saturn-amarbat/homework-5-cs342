# CS342 HW5 - JavaFX Messaging App

A client-server chat application built for CS342 HW5 using Java sockets, threads, JavaFX, and a shared Serializable message protocol.

## Project Structure

- `HW5Server/` - server Maven project
- `HW5Client/` - client Maven project

Each project has:
- `Gui*.java` for the JavaFX UI and app entry point
- `*.java` networking logic (client/server)
- `Message.java` shared message model (must stay matched on both sides)

## Features

- Username login with duplicate-name rejection
- Online users list updates
- Global chat (to all users)
- Private chat (to selected user)
- Group creation and group chat
- Disconnect handling with system notifications
- Server-side log view in the server GUI

## Tech Stack

- Java 11
- Maven
- JavaFX (`javafx-controls`, `javafx-fxml`)
- Object streams with Serializable message objects

## Prerequisites

- JDK 11+ installed and available in PATH
- Maven installed and available in PATH
- Windows PowerShell commands shown below

## Build

From repository root:

```powershell
mvn -f "HW5Server/pom.xml" clean compile
mvn -f "HW5Client/pom.xml" clean compile
```

## Run (PowerShell)

Open 3 terminals.

### Terminal 1 - Start Server

```powershell
Set-Location "HW5Server"
mvn --% -Dexec.mainClass=GuiServer org.codehaus.mojo:exec-maven-plugin:3.1.0:java
```

### Terminal 2 - Start Client #1

```powershell
Set-Location "HW5Client"
mvn --% -Dexec.mainClass=GuiClient org.codehaus.mojo:exec-maven-plugin:3.1.0:java
```

### Terminal 3 - Start Client #2

```powershell
Set-Location "HW5Client"
mvn --% -Dexec.mainClass=GuiClient org.codehaus.mojo:exec-maven-plugin:3.1.0:java
```

> Note: `--%` is used in PowerShell so `-Dexec.mainClass=...` is passed correctly to Maven.

## Quick Demo Flow

1. Start server, then launch two client windows.
2. Login users (for example `alice` and `bob`) on `127.0.0.1:5555`.
3. Send a global message and verify both clients receive it.
4. Send a private message by selecting the target user.
5. Create a group and send a group message.
6. Close one client and verify system leave notification + online list update.

## Submission Hygiene

Before packaging for LMS/Blackboard:

```powershell
mvn -f "HW5Server/pom.xml" clean
mvn -f "HW5Client/pom.xml" clean
```

Then zip only:
- `HW5Server`
- `HW5Client`

## Notes

- Group data is in-memory (resets when server restarts).
- Client and server `Message.java` must remain synchronized.
