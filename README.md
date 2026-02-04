# Real-Time Chat System

A Java-based client–server chat application that supports real-time messaging through private direct messages (DMs) and group chat rooms. The system includes user authentication, friend management, chat rooms, message persistence, and live online status updates.

This project focuses primarily on backend systems, including socket-based networking, multithreaded server design, and database-backed message handling, with a Swing-based GUI for client interaction.

---

## Features

### Core Functionality
- User registration and login with validation and error handling
- Private direct messaging (DMs)
- Group chat rooms (auto-created on join)
- Room invitations and access control
- Friend system (add, accept, reject, remove)
- Message history for DMs and chat rooms
- Real-time online / away / offline status indicators

### Backend
- Multithreaded server architecture (one thread per client)
- TCP socket-based client–server communication
- SQLite database for persistent storage
- DAO-based data access layer
- Activity logging for debugging and auditing

### Frontend
- Java Swing GUI
- Friend list with live status indicators
- Tab-based chat interface for DMs and rooms
- Automatic status updates based on user activity

---

## Tech Stack

- Java
- TCP Sockets
- Multithreading
- SQLite
- Swing GUI
- DAO (Data Access Object) pattern

---

## Architecture Overview

- **Server**
  - Manages all client connections
  - Routes messages between users and chat rooms
  - Tracks online users, room membership, and user status
  - Handles database access and logging

- **Client**
  - Provides GUI for user interaction
  - Communicates with server via command-based messages
  - Updates UI asynchronously based on server responses

---

## Project Structure

### Client
- `LoginFrame.java` – Handles user authentication (login/register)
- `MainFrame.java` – Main chat UI and client-side controller
- `ChatConnection.java` – Manages TCP socket communication
- `DMChatPanel.java` – UI component for direct messages
- `RoomChatPanel.java` – UI component for chat rooms
- `FriendCell.java` – Custom renderer for friend list with status

### Server
- `ChatServer.java` – Server entry point and connection manager
- `ClientHandler.java` – Handles communication for a single client

### Database / DAO
- `DatabaseManager.java` – Manages SQLite connections
- `UserDAO.java` – User authentication and validation
- `FriendDAO.java` – Friend relationships and requests
- `RoomDAO.java` – Chat room management and access control
- `MessageDAO.java` – Message persistence and history retrieval
- `ActivityLogDAO.java` – Logs system events and user actions

---

## How to Run

> **Important:** The server must be started before any clients.

1. Open the project in an IDE (Eclipse recommended)
2. Run `ChatServer.java`
3. Run `LoginFrame.java` to launch the client
4. Register a new user or log in with an existing account
5. (Optional) Run multiple clients to test DMs and group chats

---

## Notes & Limitations

- Passwords are encoded but not hashed (no production-level security)
- UI is intentionally minimal
- Some advanced features (profiles, encryption) were out of scope

This project was built as a systems-focused implementation emphasizing correctness, concurrency, and real-time communication.
