# Release process

1. Commit any remaining changes and push. Wait for Travis build to come back green.
2. Update `build.gradle` with new version number and commit. (Suggested message: `Release vXXX.`) Don't push yet.
3. Tag last commit with name of new version (eg. if new version is `1.3`, tag name should be `1.3`).
4. Run `./gradlew validateRelease -PreleaseBuild` to ensure everything looks OK.
5. Push commit and tag. Travis will automatically create GitHub release with binaries.
6. Go to GitHub and add release notes / changelog to release. 
7. Update the sample project to use the new version.

If you need to update the demo screencast, run `asciinema rec --command sh -c 'PS1="\$ " bash'`.
