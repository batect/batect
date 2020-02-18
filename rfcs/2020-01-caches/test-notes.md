Test commands:

* Downloading dependencies: `time go mod download`
* Building: `time CGO_ENABLED=0 GOOS=linux go build -o .batect/app/abacus ./server/cmd` (run once for initial build and then again for a warm build)
