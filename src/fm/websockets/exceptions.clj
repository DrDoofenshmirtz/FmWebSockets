(ns
  ^{:doc "Custom exception types for WebSocket errors."
    :author "Frank Mosebach"}
  fm.websockets.exceptions)

(gen-class
  :name fm.websockets.exceptions.WebSocketException
  :extends java.lang.RuntimeException
  :constructors {[] []
                 [String] [String]
                 [Throwable] [Throwable]
                 [String Throwable] [String Throwable]})

(gen-class
  :name fm.websockets.exceptions.ConnectionFailed
  :extends fm.websockets.exceptions.WebSocketException
  :constructors {[] []
                 [String] [String]
                 [Throwable] [Throwable]
                 [String Throwable] [String Throwable]})

(gen-class
  :name fm.websockets.exceptions.ConnectionClosed
  :extends fm.websockets.exceptions.WebSocketException
  :constructors {[] []
                 [String] [String]
                 [Throwable] [Throwable]
                 [String Throwable] [String Throwable]})

(gen-class
  :name fm.websockets.exceptions.EndOfData
  :extends fm.websockets.exceptions.WebSocketException
  :constructors {[] []
                 [String] [String]
                 [Throwable] [Throwable]
                 [String Throwable] [String Throwable]})
