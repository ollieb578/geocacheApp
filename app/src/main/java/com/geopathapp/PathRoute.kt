package com.geopathapp

data class PathRoute(var nodes : List<PathNode> ?= null, var routeUID : String ?= null, var numNodes : Int ?= 0,var uses : Int ?= 10)
