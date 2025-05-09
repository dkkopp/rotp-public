#!/bin/sh

BASE_DIR=$(cd `dirname $0` && pwd)

rm -rf ${BASE_DIR}/../../target
mkdir -p ${BASE_DIR}/../../target

echo Running jpackage
jpackage @${BASE_DIR}/../jpackage-common-options.txt --icon ${BASE_DIR}/icon.icns \
          --license-file ${BASE_DIR}/../license.txt \
          --module-path ${BASE_DIR}/../../client/target/modules \
          --dest ${BASE_DIR}/../../target/dmg \
          --app-version 1.0.0 --mac-package-identifier RotP --mac-app-category Games
