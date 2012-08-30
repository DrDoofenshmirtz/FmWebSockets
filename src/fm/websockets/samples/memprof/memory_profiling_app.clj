(ns
  ^{:doc "Starts a WebSockets server that dispatches incoming JSON RPC requests
         to the 'memory-profiling-service' namespace."
    :author "Frank Mosebach"}
  fm.websockets.samples.memprof.memory-profiling-app
  (:gen-class
    :name fm.websockets.samples.memprof.MemoryProfilingApp
    :main true)
  (:use
    [clojure.contrib.def :only (defvar-)]
    [clojure.contrib.logging :only (debug)]
    [clojure.contrib.command-line :only (with-command-line)]
    [fm.websockets.server :only (start-up)]
    [fm.websockets.json-rpc :only (connection-handler ns-dispatcher)]))

(defvar- service-namespace 'fm.websockets.samples.memprof.memory-profiling-service)

(defn- make-connection-handler []
  (connection-handler (ns-dispatcher service-namespace)))

(defn -main [& args]
  (with-command-line args
    "MemoryProfilingApp"
    [[port "The app's server port number" 17500]]
    (debug (format "Starting MemoryProfilingApp server on port %s..." port))
    (let [port (Integer/parseInt (.trim (str port)) 10)]
      (start-up port (make-connection-handler))
      (debug "...done. Waiting for clients..."))))