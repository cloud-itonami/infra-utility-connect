#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# `clojure`, not `bb`: the suite now executes the shipped decision core through
# `kotoba.kir`, which is a git dep declared in deps.edn. `bb` is retired in this
# workspace and was not resolving that dep -- the entry point exited 127.
exec kbb -M:test
