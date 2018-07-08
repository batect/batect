# Release process

1. Commit any remaining changes and push. Wait for Travis build to come back green.
2. Run `./gradlew reckonTagCreate -Preckon.scope=minor -Preckon.stage=final` to create Git tag with next version number. 
   
   Use `reckon.scope=major` for major changes (v1.0.0 to v2.0.0), `reckon.scope=minor` for minor changes (v0.3.0 to v0.4.0),
   and `reckon.scope=patch` for patches (v0.1.0 to v0.1.1).
   
3. Run `./gradlew validateRelease` to ensure everything looks OK.
4. Push tag. Travis will automatically create GitHub release with binaries.
5. Go to GitHub and add release notes / changelog to release.
6. Update the sample project to use the new version.

If you need to update the demo screencast, run `asciinema rec --command sh -c 'PS1="\$ " bash'`.
