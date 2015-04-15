#!/bin/bash
last=0
lastPid=-1
while true
do
    sleep 1
    nbactiv=$(cat /sys/class/drm/card*-*/status | grep -e "^connected" | wc -l)
    dp1Activ=$(cat /sys/class/drm/card*-DP-1/status | grep -e "^connected" | wc -l)
    if [ $nbactiv -ne $last ];then
        if [ $nbactiv -eq 2 ] && [ $dp1Activ -eq 1 ] ;then
            echo Dual
            xfconf-query -c xsettings -p /Xft/DPI -s 90
            xrandr --output DP1 --auto
            xrandr --output eDP1 --mode 1920x1200 --right-of DP1
        elif [ $nbactiv -eq 1 ]; then
            echo Simple
            xfconf-query -c xsettings -p /Xft/DPI -s 150
            xrandr --output eDP1 --auto            
        fi
        if [ $lastPid -gt 0 ]; then
            kill $lastPid
        fi
        xmonad --replace&
        lastPid=$!
    else
        sleep 1
    fi
    last=$nbactiv
done