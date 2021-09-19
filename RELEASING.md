# Release process

Before releasing, don't forget to:

* Update CLI and config file reference in docs
* Update config schema

1. Commit any remaining changes and push. Wait for build to come back green.
2. Pull latest changes.
3. Create Git tag with next version number: `git tag -s <version> -m <version>`
4. Run `./gradlew validateRelease` to ensure everything looks OK.
5. Push tag (`git push origin <version>`). The GitHub Actions build will automatically create GitHub release with binaries.
6. Go to GitHub and add release notes / changelog to release, then publish release.
