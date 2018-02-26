# DARQL

DARQL - Deep Analysis of SPARQL Queries

![screenshot](screenshot.png)

# Prerequisites

To run DARQL, the following requirements must be met:

1. Java JDK 8
2. Maven build tool
3. PostreSQL (recommended via Docker)
4. GNU make (optional)

Here are some instructions to help with this.

## Linux

To install the requirements on a Debian-based distro such as Ubuntu:

```bash
sudo apt-get install docker.io
```

For a newer version of Docker follow [instructions on their site](https://docs.docker.com/install/linux/docker-ce/debian/).

To install Java and Maven with [SDKMAN](https://github.com/sdkman/sdkman-cli):

```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 8u161-oracle
sdk install maven
```

## macOS

To easily install the requirements with [Homebrew](https://brew.sh/index_de.html):

```bash
brew install docker
brew install make
```

To install Java and Maven with [SDKMAN](https://github.com/sdkman/sdkman-cli):

```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 8u161-oracle
sdk install maven
```

## Windows

To easily install the requirements with [Chocolatey](https://chocolatey.org/):

```ps1
cinst -y docker
cinst -y jdk8 
cinst -y maven

cinst -y msys2
msys2
pacman -Sy make
```

Alternatively, you can use [WSL on Windows 10](https://docs.microsoft.com/en-us/windows/wsl/install-win10) and follow the instructions for Linux.

# Usage

If `make` is installed, predefined targets can be used:

1. Clone this repository and `cd` into it. Set `Makefile=src/main/resources/demo/demo.mk`.
2. Run `make -f $Makefile pg_start` to start PostgreSQL via Docker.
3. Run `make -f $Makefile db` to populate the database with [examples](src/main/resources/sample/demo/wikidata.txt).
4. Run `make -f $Makefile web` to start the server.
5. Open [http://localhost:8888](http://localhost:8888) in a browser.

Alternatively, look in the `Makefile` to run the steps manually or to adjust them. For example, PostgreSQL can be without Docker, but then it has to be set up.

By default, the target will set up and populate the database with data from [Wikidata](src/main/resources/sample/demo/wikidata.txt). To make changes and supply different data with other formats, adjust or create a new configuration file `config.yaml` as supplied in the `Makefile`.

When finished, you can stop the server by hitting enter and shutdown PostgreSQL with `make pg_stop`. 
