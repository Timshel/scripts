#!/bin/bash
last=0
def="eDP1"
outputs="DP1 HDMI1 DP2 HDMI2"
while true
do
    sleep 1
    nbactiv=$(xrandr -q  |grep " connected " |awk '{print $1}'|wc -l)
    # xrandr --output eDP1 --off --output DP1 --auto
    if [ $nbactiv -ne $last ];then
        if [ $nbactiv -eq 1 ];then
            tmp=""
            for i in $outputs
            do
                tmp="$tmp --output $i --off "
            done 
            xrandr --output $def --auto $tmp
        else
            new=$(xrandr -q |grep " connected " |awk '{print $1}' |grep -v "${def}")
            tmp2=$(echo $outputs|sed "s/${new}//g")
            echo $outputs|grep -v "$new"
            tmp=""
            for i in $tmp2
            do
                tmp="$tmp --output $i --off "
            done 
            xrandr --output $new --auto $tmp --output $def --off
        fi
    else
        sleep 1
    fi
    last=$nbactiv
done
