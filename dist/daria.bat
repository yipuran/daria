@echo off

set excel=%1

@java -jar -Xms1024m -Xmx1024m daria.jar -b %excel%

pause