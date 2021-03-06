#!/bin/bash
#
# init script for a rap container
#
# developed and tested for Suse linux
#
# Authors: Marcus -LiGi- Bueschleb @ PolyMap GmbH <ligi@polymap.de> 
#
# /etc/init.d/rap_demos


P3_BIN=/usr/local/polymap3/start_linux.sh
P3_WORKSPACE=/usr/local/workspace/
P3_LOG=/usr/local/polymap3/log/polymap3.log
test -x $P3_BIN || exit 5

# Shell functions sourced from /etc/rc.status:
#      rc_check         check and set local and overall rc status
#      rc_status        check and set local and overall rc status
#      rc_status -v     ditto but be verbose in local rc status
#      rc_status -v -r  ditto and clear the local rc status
#      rc_status -s     display "skipped" and exit with status 3
#      rc_status -u     display "unused" and exit with status 3
#      rc_failed        set local and overall rc status to failed
#      rc_failed <num>  set local and overall rc status to <num>
#      rc_reset         clear local rc status (overall remains)
#      rc_exit          exit appropriate to overall rc status
#      rc_active	checks whether a service is activated by symlinks
#      rc_splash arg    sets the boot splash screen to arg (if active)
. /etc/rc.status

# Reset status of this service
rc_reset

# Return values acc. to LSB for all commands but status:
# 0	  - success
# 1       - generic or unspecified error
# 2       - invalid or excess argument(s)
# 3       - unimplemented feature (e.g. "reload")
# 4       - user had insufficient privileges
# 5       - program is not installed
# 6       - program is not configured
# 7       - program is not running
# 8--199  - reserved (8--99 LSB, 100--149 distrib, 150--199 appl)
# 
# Note that starting an already running service, stopping
# or restarting a not-running service as well as the restart
# with force-reload (in case signaling is not supported) are
# considered a success.

case "$1" in
	start)
		echo -n "Starting polymap3 "
                startproc -l $P3_LOG $P3_BIN
		#startproc -u rapper -l $P3_LOG $P3_BIN -data $P3_WORKSPACE -vmargs -Xmx=256m -XX:MaxPermSize=128M -XX:+UseParallelGC  -registryMultiLanguage
		rc_status -v
		;;
	stop)
		echo -n "Shutting down POLYMAP3 "
		killproc -TERM $P3_BIN
		sleep 10
		killproc -9 $P3_BIN
		killall -g eclipse

		rc_status -v
		;;
	try-restart)
	        $0 status
		if test $? = 0; then
			$0 restart
		else
			rc_reset        # Not running is not a failure.
		fi
		rc_status
		;;
	restart)
		$0 stop
		$0 start
		rc_status
		;;
        status)
        	echo -n "Checking for service polymap3: "
        	## Check status with checkproc(8), if process is running
        	## checkproc will return with exit status 0.

        	# Status has a slightly different for the status command:
        	# 0 - service running
        	# 1 - service dead, but /var/run/  pid  file exists
        	# 2 - service dead, but /var/lock/ lock file exists
        	# 3 - service not running

        	# NOTE: checkproc returns LSB compliant status values.
        	/sbin/checkproc $P3_BIN
        	rc_status -v
        	;;
	*)
		echo "Usage: $0" \
		     "{start|stop|try-restart|restart}"
		exit 1
		;;
esac
rc_exit
