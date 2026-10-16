# Phone ↔ watch contract

Sample `course` messages, as the phone sends them to the watch (see `Protocol.kt` and `watch/source/MessageHandler.mc`):

- `course-normal.json`: a real 10 km BRouter loop from central Stockholm, simplified to 133 points.
- `course-500.json`: a full course of 500 points, the most one message carries.

The phone's `ProtocolTest` checks that the course builder produces them, and the watch's `MessageHandlerTest` checks that the watch accepts them. If a course-builder change alters them, regenerate both files and check that both sides still pass.
