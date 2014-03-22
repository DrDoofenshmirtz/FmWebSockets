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
    [fm.websockets.app.config :as cfg])
  (:import
    (java.io File)))

(defn- check-config-path [config-path]
  (let [config-path (-> config-path str .trim)]
    (when (.isEmpty config-path)
      (throw (IllegalArgumentException. 
               "A non-empty config path is required!")))
    (when-not (-> config-path File. .isFile)
      (throw (IllegalArgumentException. (format "Cannot load config file '%s'!" 
                                                config-path))))
    config-path))

(defn run [config-path]
  (let [config-path (check-config-path config-path)
        config      (cfg/load-config config-path)]
    config))
  
(defn -main [& args]
  (let [config-path (-> args first str .trim)]
    (if (.isEmpty config-path)
      (println "Usage: fm.websockets.App config-path")
      (run config-path))))

