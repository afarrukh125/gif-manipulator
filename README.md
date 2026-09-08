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

Either script builds the jar if it is missing, then starts the editor on http://localhost:8080 and opens it in
your browser. Set `PORT` to listen somewhere else, and anything you pass the script goes on to `serve`:

```
PORT=9000 ./run.sh
./run.sh --no-open
```

### Without the scripts

```
java -jar target/giftools.jar
```

This starts the editor on http://localhost:7000 and opens it in your browser.

Options:

```
java -jar target/giftools.jar serve --port 8080   # use a different port
java -jar target/giftools.jar serve --no-open     # don't open a browser
```

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
