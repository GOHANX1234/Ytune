package com.ytune.app.player

object NativeSecurity { init { System.loadLibrary("ytune_security") }; external fun requestId(seed: String): String }
