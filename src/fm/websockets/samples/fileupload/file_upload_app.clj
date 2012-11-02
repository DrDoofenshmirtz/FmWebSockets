(ns
  ^{:doc "Starts a WebSockets server that dispatches incoming JSON RPC requests
         to the 'file-upload-service' namespace."
    :author "Frank Mosebach"}
  fm.websockets.samples.fileupload.file-upload-app
  (:gen-class
    :name fm.websockets.samples.fileupload.FileUploadApp
    :main true)
  (:use
    [clojure.contrib.def :only (defvar-)]
    [clojure.contrib.logging :only (debug)]
    [clojure.contrib.command-line :only (with-command-line)]
    [fm.websockets.connection :only (ping)]
    [fm.websockets.server :only (start-up)]
    [fm.websockets.json-rpc :only (connection-handler ns-dispatcher)]))

(defvar- service-namespace 'fm.websockets.samples.fileupload.file-upload-service)

(defn- make-connection-handler []
  (let [connection-handler (connection-handler (ns-dispatcher service-namespace))]
    (fn [connection]
      (ping connection)
      (connection-handler connection))))

(defn -main [& args]
  (with-command-line args
    "FileUploadApp"
    [[port "The app's server port number" 17500]]
    (debug (format "Starting FileUploadApp server on port %s..." port))
    (let [port (Integer/parseInt (.trim (str port)) 10)]
      (let [server (start-up port (make-connection-handler))]
        (debug "...done. Waiting for clients...")))))