(ns
  ^{:doc 
  
  "Load a WebSockets app configuration from a config file."
  
    :author "Frank Mosebach"}
  fm.websockets.app.config
  (:require
    [clojure.contrib.logging :as log]
    [fm.websockets.app.boot :as boot]))

(def ^{:private true :const true} default-config 
                                  {:ws-port   17500
                                   :http-port 20500
                                   :root-path "."
                                   :app-path  "index.html"
                                   :services  []})

(defn- quote-values [config & keys]
  (reduce (fn [config key]
            (if-let [value (key config)]
              (assoc config key `'~value)
              config)) 
          config 
          keys))

(defmacro defapp [app-name & {:as config}]
  (let [config (merge default-config config)
        config (quote-values config :boot :services)
        config (assoc config :app-name (str app-name))]
    `(def ~'config- ~config)))

(defn- call-boot-hook [config]
  (if-let [boot-ns (:boot config)]
    (if-let [boot-hook (boot/find-boot-hook boot-ns)]
      (try
        (boot-hook config)
        (catch Exception boot-error
          (log/error "Boot hook failed!" boot-error)
          nil))
      (throw (IllegalStateException. 
               (format "No boot hook found in namespace '%s'!" boot-ns))))
    config))

(defn load-config [config-path]
  (when (let [current-ns (-> *ns* str symbol)] 
          (try
            (in-ns 'fm.websockets.app.config._)
            (refer-clojure)
            (use '[fm.websockets.app.config :only (defapp)])
            (load-file config-path)
            true
            (catch Exception invalid-config
              (log/error "Invalid app config!" invalid-config)
              false)
            (finally 
              (in-ns current-ns))))
    (when-let [config (ns-resolve 'fm.websockets.app.config._ 'config-)]
      (call-boot-hook @config))))

