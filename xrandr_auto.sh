#!/bin/bash
last=0
while true
do
    sleep 1
    nbactiv=$(cat /sys/class/drm/card*-*/status | grep -e "^connected" | wc -l)
    if [ $nbactiv -ne $last ];then
        active=$(xrandr | grep " connected" | grep -v "eDP1" | awk -F" " '{print $1}')
        xfconf-query -c xsettings -p /Xft/DPI -s 90
        if [ $nbactiv -eq 2 ]; then
            echo Dual
            xrandr --output $active --auto
            xrandr --output eDP1  --mode 1920x1080 --right-of $active --primary
        elif [ $nbactiv -eq 1 ]; then
            echo Simple
            xrandr --output eDP1 --mode 1920x1080 --primary
        fi
    else
        sleep 1
    fi
    last=$nbactiv
done
