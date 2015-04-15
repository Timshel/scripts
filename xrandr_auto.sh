#!/bin/bash
last=0
lastPid=0
while true
do
    sleep 1
    nbactiv=$(cat /sys/class/drm/card*-*/status | grep -e "^connected" | wc -l)
    dp1Activ=$(cat /sys/class/drm/card*-DP-1/status | grep -e "^connected" | wc -l)
    if [ $nbactiv -ne $last ];then
        if [ $nbactiv -eq 2 ] && [ $dp1Activ -eq 1 ] ;then
            echo Dual
            xfconf-query -c xsettings -p /Xft/DPI -s 90
            xrandr --output eDP1 --mode 1920x1080 --right-of DP1
        elif [ $nbactiv -eq 1 ]; then
            echo Simple
            xfconf-query -c xsettings -p /Xft/DPI -s 90
            xrandr --output eDP1 --mode 1920x1080            
        fi
        if [ $lastPid -gt 0 ]; then
            kill $lastPid
        fi
        xmonad --replace&
        lastPid=$!
        trap "kill $lastPid; exit 1" SIGINT
    else
        sleep 1
    fi
    last=$nbactiv
done