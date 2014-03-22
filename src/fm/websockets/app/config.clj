(ns
  ^{:doc 
  
  "Load a WebSockets app configuration from a config file."
  
    :author "Frank Mosebach"}
  fm.websockets.app.config
  (:require
    [clojure.contrib.logging :as log]))

(def ^{:private true :const true} default-config 
                                  {:ws-port   17500
                                   :http-port 20500
                                   :root-path "."
                                   :app-path  "index.html"
                                   :services  []})

(defmacro defapp [& {:as config}]
  (let [config-symbol (symbol "config-")
        config        (merge default-config config)
        {services :services} config]
    `(intern *ns* 
             '~config-symbol 
             ~(assoc config :services `'~services))))
  
(defn load-config [config-path]
  (when (let [current-ns (-> *ns* str symbol)] 
          (try
            (in-ns 'fm.websockets.app.config._)
            (refer-clojure)
            (use '[fm.websockets.app.config :only (defapp)])
            (load-file config-path)
            true
            (catch Exception invalid-config
              (log/error "Invalid app config!")
              false)
            (finally 
              (in-ns current-ns))))
    (when-let [config (ns-resolve 'fm.websockets.app.config._ 'config-)]
      (deref config))))

