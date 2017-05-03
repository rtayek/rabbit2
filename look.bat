::015d2109aa080e1a
::G0K0H40453650FLR
::G0K0H404542514AX
015d2109aa080e1a 34049e2039c571a3

for %%i in (015d2109aa080e1a 34049e2039c571a3)do (
	echo %%i
 	adb -s %%i shell settings get global captive_portal_detection_enabled
	)