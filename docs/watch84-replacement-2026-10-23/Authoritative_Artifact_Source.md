# Authoritative artifact source

Android's official documentation identifies Google's Maven repository as the repository for AndroidX and Wear OS libraries. It explicitly documents `https://dl.google.com/dl/android/maven2/` as an alternative repository URL and programmatic POM/JAR access by group/library/version. This establishes the origin used for the four explicitly pinned dependency artifacts; it does not itself establish their content checksums.[1]

Each artifact's exact-coordinate POM, binary archive and available published integrity sidecars are verified separately in `dependencies/dependency-restoration-proof.json`. Locally computed SHA-256 is a custody digest, not a substitute for a publisher signature or unavailable published digest.

## References

[1]: https://developer.android.com/build/remote-repositories "Android official remote repository and Google Maven documentation"
