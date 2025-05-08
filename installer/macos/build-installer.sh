#!/bin/sh

BASE_DIR=$(cd `dirname $0` && pwd)

rm -rf ../../target/dmg

echo Running jpackage
jpackage @../jpackage-common-options.txt --icon icon.icns --dest ../../target/dmg \
         --app-version 1.0.0 --mac-package-identifier RotP --mac-app-category Games
