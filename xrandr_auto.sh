#!/bin/bash
last=0
while true
do
    sleep 1
    nbactiv=$(cat /sys/class/drm/card*-*/status | grep -e "^connected" | wc -l)
    dp1Activ=$(cat /sys/class/drm/card*-DP-*/status | grep -e "^connected" | wc -l)
    if [ $nbactiv -ne $last ];then
        xfconf-query -c xsettings -p /Xft/DPI -s 90
        if [ $nbactiv -eq 2 ] && [ $dp1Activ -eq 1 ] ;then
            echo Dual
            xrandr --output DP1-8 --mode 3440x1440 --rate 50 --primary
            xrandr --output eDP1 --mode 1920x1080 --right-of DP1-8
        elif [ $nbactiv -eq 1 ]; then
            echo Simple
            xrandr --output eDP1 --mode 1920x1080 --primary
        fi
    else
        sleep 1
    fi
    last=$nbactiv
done