package=acme.cb2
for i in 015d2109aa080e1a 34049e2039c571a3
	do
	echo "device $i -----------------------------------------"
	dir=/data/data/$package/files
	cat <<EOF | adb -s $i shell
	run-as $package
	ls -lR /data/data/$package
	exit
	exit
EOF
	done