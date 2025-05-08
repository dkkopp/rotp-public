#!/bin/sh

BASE_DIR=$(cd `dirname $0` && pwd)

rm -rf ../../target/bob

echo Running jpackage
jpackage @../jpackage-common-options.txt --type deb --icon icon.png --dest ../../target/deb --app-version 1.0.0
