# LibADB Android

Vendored from [MuntashirAkon/libadb-android](https://github.com/MuntashirAkon/libadb-android), tag `3.1.1`, commit `c849886ebc6d48e7b46d967e78a6bb65c90c3b74`.

Used under its Apache-2.0 option, with the additional per-file BSD/MIT notices preserved. License texts are in `LICENSES/`.

Local changes: Android minimum API 30 / Java 17 Gradle configuration; 12-second TCP connection and TLS-handshake deadlines; 15-second pairing read deadline; null-safe socket cleanup when pairing fails before initialization; removal of peer identity logging; correct stream EOF after peer close, including queued and partially read payloads, plus unsigned single-byte reads and zero-length reads. Stream-close regressions are covered by three transport-level unit tests. No ADB implementation is included in the watch APK.

Large writes now respect the peer's negotiated maximum payload and consume a separate OKAY for every WRTE fragment. Writes are serialized independently of the connection-thread monitor; zero-length writes do not consume a readiness token. Buffered reads use bulk copies. Additional transport tests verify intact multi-MiB transfers with 4-KiB, 64-KiB and 1-MiB limits, waiting for each acknowledgment, and a peer close while waiting for the next fragment.

Stream opening now waits on the recorded remote ID/close state rather than an unconditional wait, so an early OKAY or rejection cannot be lost. Tests cover both replies arriving before the waiting thread starts.
