# gif-manipulator

A GIF editor you run on your own machine. Drop a GIF into the browser, change it, download it.

## Build

```
mvn package
```

## Run

```
./run.sh      # macOS, Linux, Git Bash
./run.ps1     # PowerShell
```

Either script builds the jar if it is missing, then starts the editor on http://localhost:8091 and opens it in
your browser. Pass a port as the first argument to listen somewhere else, or set `PORT`; anything else you pass
the script goes on to `serve`:

```
./run.sh 9000
./run.ps1 9000
PORT=9000 ./run.sh
./run.sh 9000 --no-open
```

If an editor is already listening on that port - the one started at login, or a window you forgot about - the
script opens that one in your browser instead of starting a second copy and failing to bind.

### Without the scripts

```
java -jar target/giftools.jar
```

This starts the editor on http://localhost:7000 and opens it in your browser.

Options:

```
java -jar target/giftools.jar serve --port 8091     # use a different port
java -jar target/giftools.jar serve --host 0.0.0.0  # let other machines reach it
java -jar target/giftools.jar serve --no-open       # don't open a browser
```

The editor binds to 127.0.0.1 by default, so only this machine can reach it. `PORT` and `HOST` are read as the
defaults for those two flags, and an explicit flag wins over the environment.

## Start it at login (Windows)

```powershell
./install-startup.ps1            # port 8091
./install-startup.ps1 -Port 9000
./install-startup.ps1 -Start     # and start it now too, without waiting for the next login
./install-startup.ps1 -Uninstall
```

This puts a shortcut in your Startup folder that runs the jar with `javaw`, so the editor is up on
http://localhost:8091 from login with no console window and no browser tab opening itself. Since there is no
console to print to, the log goes to `server.log` beside the jar; `--log-file` (or `LOG_FILE`) does that for any
`serve` run, and rolls the file over at 1MB, keeping two older ones:

```
java -jar target/giftools.jar serve --log-file server.log
```

`-Uninstall` only stops it starting at the next login; an editor already running stays up until you close it.

## Docker

```
docker build -t giftools .
docker run --rm -p 8080:8080 giftools
```

The image binds 0.0.0.0 inside the container and reads `PORT`, so hosts that hand you a port work as they are:

```
docker run --rm -e PORT=9000 -p 9000:9000 giftools
```

## Putting it on the internet

Editing happens on the server: the browser uploads the GIF, and the server decodes and re-encodes it. Uploads are
held in memory for two hours and never written to disk, but on a public host they are sitting in that host's
memory, not yours.

There is no login, and an upload is tied only to an unguessable id, so anyone who can reach the port can upload
and render. If you expose it beyond your own machine, put something in front of it that terminates TLS and asks
for a password - Cloudflare Tunnel with Access, or a reverse proxy with basic auth. Rendering is CPU heavy and
uploads are capped at 64MB each with a 512MB store, so also give it a memory limit it cannot exceed.

## Command line

Split a GIF into numbered PNG frames:

```
java -jar target/giftools.jar create --file-path cat.gif
```

Build a GIF from a folder of numbered PNG frames:

```
java -jar target/giftools.jar reinstate --folder-path target/cat
```

Leave the path off either command and a file picker opens instead.

Run `java -jar target/giftools.jar help` to see all commands.
