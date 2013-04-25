(ns
  ^{:doc "Starts a WebSockets server that dispatches incoming JSON RPC requests
         to the 'counter-service' namespace."
    :author "Frank Mosebach"}
  fm.websockets.samples.counter.counter-app
  (:gen-class
    :name fm.websockets.samples.counter.CounterApp
    :main true)
  (:require
    [clojure.contrib.logging :as log]
    [clojure.contrib.command-line :as cli]
    [fm.websockets.rpc.core :as rpc]
    [fm.websockets.rpc.request :as req]
    [fm.websockets.rpc.json :as jrpc]
    [fm.websockets.message-loop :as mloop]
    [fm.websockets.connection :as conn]
    [fm.websockets.server :as srv]))

(def ^{:private true} 
     service-namespace 'fm.websockets.samples.counter.counter-service)

(def ^{:private true} request-handler (-> service-namespace
                                          req/ns-router))

(def ^{:private true} message-handler (-> request-handler
                                          rpc/message-handler))

(def ^{:private true} 
     connection-handler (-> (comp (mloop/connection-handler message-handler) 
                                  (jrpc/connection-handler)
                                  (fn [connection]
                                    (conn/ping connection)
                                    (assoc connection :counter 0)))))

(defn -main [& args]
  (cli/with-command-line args
    "CounterApp"
    [[port "The app's server port number" 17500]]
    (log/debug (format "Starting CounterApp server on port %s..." port))
    (let [port (Integer/parseInt (.trim (str port)) 10)]
      (srv/start-up port connection-handler)
      (log/debug "...done. Waiting for clients..."))))

