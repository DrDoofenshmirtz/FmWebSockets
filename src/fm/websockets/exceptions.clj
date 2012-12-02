(ns
  ^{:doc "Custom exception types for WebSocket errors."
    :author "Frank Mosebach"}
  fm.websockets.exceptions)

(gen-class
  :name fm.websockets.exceptions.ConnectionFailed
  :extends java.lang.RuntimeException
  :constructors {[] []
                 [String] [String]
                 [Throwable] [Throwable]
                 [String Throwable] [String Throwable]})

(gen-class
  :name fm.websockets.exceptions.ConnectionClosed
  :extends java.lang.RuntimeException
  :constructors {[] []
                 [String] [String]
                 [Throwable] [Throwable]
                 [String Throwable] [String Throwable]})

