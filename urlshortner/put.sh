#!/bin/bash
host=`hostname -I | awk '{print $1}'`
curl -X PUT -v "http://$host:5000/?short=$1&long=$2"
