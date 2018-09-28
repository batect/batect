# Release process

1. Commit any remaining changes and push. Wait for Travis build to come back green.
2. Create Git tag with next version number:
    * for major changes (v1.0.0 to v2.0.0): `./gradlew reckonTagCreate -Preckon.scope=major -Preckon.stage=final` 
    * for minor changes (v1.0.0 to v1.1.0): `./gradlew reckonTagCreate -Preckon.scope=minor -Preckon.stage=final` 
    * for patches (v1.0.0 to v1.0.1): `./gradlew reckonTagCreate -Preckon.scope=patch -Preckon.stage=final` 

3. Run `./gradlew validateRelease` to ensure everything looks OK.
4. Push tag. Travis will automatically create GitHub release with binaries.
5. Go to GitHub and add release notes / changelog to release.
6. Update the sample projects to use the new version.

If you need to update the demo screencast, run `asciinema rec --command sh -c 'PS1="\$ " bash'`.
