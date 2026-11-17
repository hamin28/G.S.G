# Server

The server implementation is under `src/gsg/server` (`gsg.server`). Run `gsg.server.ServerMain` from the IDE with Gson available on the project classpath.

The default port is `9999`. `ServerMain` accepts an optional port argument; pass `5000` to match the Swing lobby's current default, or set the UI port to `9999`.

Implemented requests: `JOIN`, `LIST_ROOMS`, `CREATE_ROOM`, `JOIN_ROOM`, `READY`, and `LEAVE_ROOM`. The server returns `ACK` or `ERROR`, and sends `ROOM_LIST` and `ROOM_STATE` events. Game action handling is not implemented yet.
