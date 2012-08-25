(ns
  ^{:doc "A socket server for the HTML5 WebSockets protocol."
    :author "Frank Mosebach"}
  fm.websockets.server
  (:require
    [fm.websockets.socket-server :as socket-server])
  (:use
    [clojure.contrib.command-line :only (with-command-line)]
    [fm.websockets.connection :only (connect)]))

(defn- wrap-connection-handler [connection-handler]
  (fn [socket]
    (connection-handler (connect socket))))

(defn start-up
  ([port connection-handler]
    (start-up port connection-handler nil))
  ([port connection-handler error-handler]
    (let [connection-handler (wrap-connection-handler connection-handler)]
      (socket-server/start-up port connection-handler error-handler))))
