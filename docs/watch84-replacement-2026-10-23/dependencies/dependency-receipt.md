# Watch84 public dependency restoration receipt

**Status: PASS.** Exactly four requested JAR payloads were retrieved from Google Maven, matched every available published SHA-256/SHA-1/MD5 sidecar, passed ZIP CRC validation, and were read back unchanged from the isolated retained Gradle cache.

Google’s Android repository documentation identifies Google Maven and its `https://dl.google.com/dl/android/maven2/` alternative URL as the source for AndroidX and Wear OS artifacts: [Android remote repositories](https://developer.android.com/build/remote-repositories).

- `androidx.wear.tiles:tiles-proto:1.2.0` — SHA-256 `6561349e6bc836166212c5b8fd51876c91c25519191ec32fbc4217def39b588a`; SHA-1 `3090e207e4ebd46e46c159822ab293c9fc4a42c6`; 45726 bytes.
  Cache readback: `/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home/caches/modules-2/files-2.1/androidx.wear.tiles/tiles-proto/1.2.0/3090e207e4ebd46e46c159822ab293c9fc4a42c6/tiles-proto-1.2.0.jar`.
- `androidx.wear.protolayout:protolayout-proto:1.0.0` — SHA-256 `28fc4c57860e46d029fa589c8bbbe0edb91cdf03932fffbba01c96c00e50a454`; SHA-1 `abb132807aa40f10fb02639c770289e87366d550`; 1739194 bytes.
  Cache readback: `/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home/caches/modules-2/files-2.1/androidx.wear.protolayout/protolayout-proto/1.0.0/abb132807aa40f10fb02639c770289e87366d550/protolayout-proto-1.0.0.jar`.
- `androidx.concurrent:concurrent-futures:1.1.0` — SHA-256 `0ce067c514a0d1049d1bebdf709e344ed3266fe9744275682937cdcb13334e9e`; SHA-1 `50b7fb98350d5f42a4e49704b03278542293ba48`; 25987 bytes.
  Cache readback: `/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home/caches/modules-2/files-2.1/androidx.concurrent/concurrent-futures/1.1.0/50b7fb98350d5f42a4e49704b03278542293ba48/concurrent-futures-1.1.0.jar`.
- `androidx.resourceinspection:resourceinspection-annotation:1.0.1` — SHA-256 `8cff870ec6fb31db48a52f4a792335b4bf8de07e03bd37823181526433ccd5cb`; SHA-1 `8c21f8ff5d96d5d52c948707f7e4d6ca6773feef`; 2336 bytes.
  Cache readback: `/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home/caches/modules-2/files-2.1/androidx.resourceinspection/resourceinspection-annotation/1.0.1/8c21f8ff5d96d5d52c948707f7e4d6ca6773feef/resourceinspection-annotation-1.0.1.jar`.

The existing cached POM descriptor for each coordinate exactly matched the authoritative POM. No PGP/signature assertion was made; checksum sidecars were treated only as published digest metadata. No Gradle, build, source, configuration, or signing action was performed.
