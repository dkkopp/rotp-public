@echo off

rd /S /Q ..\..\target\msi

echo Running jpackage
jpackage @..\jpackage-common-options.txt --type msi --dest ..\..\target\msi ^
     --app-version 1.0.0 --win-dir-chooser --icon rotp.ico --win-shortcut --win-menu --win-menu-group "Games"
