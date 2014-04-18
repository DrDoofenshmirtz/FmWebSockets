(ns
  ^{:doc 
  
  "Launches a WebSockets application."
  
    :author "Frank Mosebach"}
  fm.websockets.app.main
  (:gen-class
    :name fm.websockets.app.Main
    :main true)
  (:require
    [clojure.contrib.logging :as log]
    [fm.websockets.app.config :as cfg]
    [fm.websockets.app.handler :as hdlr]
    [fm.websockets.resources :as rscs]
    [fm.resources.store :as rstore]
    [fm.websockets.file-resource-finder :as frf]
    [fm.websockets.resource-server :as rss]
    [fm.websockets.server :as wss])
  (:import
    (java.io File)))

(def ^{:private true} resource-store (ref nil))

(defn- check-config-path [config-path]
  (let [config-path (-> config-path str .trim)]
    (when (.isEmpty config-path)
      (throw (IllegalArgumentException. 
               "A non-empty config path is required!")))
    (when-not (-> config-path File. .isFile)
      (throw (IllegalArgumentException. (format "Cannot load config file '%s'!" 
                                                config-path))))
    config-path))

(defn- connection-store [connection]
  (rstore/partition-store resource-store (:id connection)))

(defn- app-store []
  (rstore/partition-store resource-store ::fm.websockets.app))

(defn- start-resource-server [{:keys [http-port app-name root-path app-path]}]
  (rss/start-up http-port app-name (frf/finder root-path app-path)))

(defn- start-app-server [{ws-port :ws-port :as config}]
  (wss/start-up ws-port (hdlr/app-handler config connection-store)))

(defn- start-servers [config]
  (log/debug (format "Starting servers for WebSockets app: %s..." config))
  (let [resource-server (start-resource-server config)
        app-server      (start-app-server config)]
    (log/debug "...done. Waiting for clients.")    
    (fn stop-servers [log]
      (try
        (log "Stopping resource server...")
        (resource-server)
        (log "...done.")
        (catch Exception error
          (log "Failed to stop resource server!" error)))
      (try
        (log "Stopping app server...")
        (app-server)
        (log "...done.")
        (catch Exception error
          (log "Failed to stop app server!" error))))))

(defn- close-resources [log]
  (doseq [key (keys @resource-store)]
    (log (format "Closing resources for %s..." key))
    (rscs/application-expired! (rstore/partition-store resource-store key))
    (log "...closed.")))

(defn- log->log 
  ([message]
    (log/debug message))
  ([message error]
    (log/error message error)))

(defn- log->console 
  ([message]
    (println message))
  ([message error]
    (binding [*out* *err*]
      (println message)
      (.printStackTrace error))))

(defn run [config-path]
  (let [config-path  (check-config-path config-path)
        config       (cfg/load-config config-path)
        stop-servers (start-servers config)]
    (fn shut-down
      ([]
        (shut-down log->log))
      ([log]
        (close-resources log)
        (stop-servers log)))))
  
(defn -main [& args]
  (let [config-path (-> args first str .trim)]
    (if (.isEmpty config-path)
      (println "Usage: fm.websockets.app.Main config-path")
      (let [shut-down (run config-path)]
        (.addShutdownHook (Runtime/getRuntime)
                          (Thread. (partial shut-down log->console) 
                                   "fm.websockets.app.main/shut-down"))))))

