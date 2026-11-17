# Game server

Java 21 TCP server module for the G.S.G game. The server uses UTF-8 JSON Lines: each request and event is one JSON object followed by a newline.

## Run

Open `server/pom.xml` as a Maven project and run `server.ServerMain`. The default port is `9999`; an optional command-line argument overrides it. For example, pass `5000` if the client is still using its default port.

## Implemented messages

- Requests: `JOIN`, `LIST_ROOMS`, `CREATE_ROOM`, `JOIN_ROOM`, `READY`, `LEAVE_ROOM`
- Responses: `ACK` or `ERROR`; successful ACK payloads include `requestType`
- Events: `ROOM_LIST` and `ROOM_STATE`

The game rules and game action messages are not implemented in this module yet.
