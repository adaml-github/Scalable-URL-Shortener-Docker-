#!/bin/bash
host=`hostname -I | awk '{print $1}'`
curl -v http://$host:5000/$1
