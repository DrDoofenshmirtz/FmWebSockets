(ns
  ^{:doc 
  
  "Launches a WebSockets application."
  
    :author "Frank Mosebach"}
  fm.websockets.app
  (:gen-class
    :name fm.websockets.App
    :main true)
  (:require
    [clojure.contrib.logging :as log]
    [clojure.contrib.command-line :as cli])
  (:import
    (java.io File)))

(defmacro defapp [& {:keys [ws-port http-port services] 
                     :or   {ws-port 17500 http-port 19500}}]
  (let [config-symbol (symbol "config")]
    `(intern *ns* 
             '~config-symbol 
             {:ws-port   ~ws-port
              :http-port ~http-port
              :services  '~services})))
  
(defn- load-config [config-path]
  (when (try
          (in-ns 'fm.websockets.app.config)
          (refer-clojure)
          (use '[fm.websockets.app :only (defapp)])
          (load-file config-path)
          true
          (catch Exception invalid-config
            (log/error "Invalid app config!")
            false)
          (finally 
            (in-ns 'fm.websockets.app)))
    (when-let [config (ns-resolve 'fm.websockets.app.config 'config)]
      (deref config))))

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
        config      (load-config config-path)]
    config))
  
(defn -main [& args]
  (let [config-path (-> args first str .trim)]
    (if (.isEmpty config-path)
      (println "Usage: fm.websockets.App config-path")
      (run config-path))))

