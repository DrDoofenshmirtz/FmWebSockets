(ns
  ^{:doc 
  
  "A basic HTTP Server tailored to host the Resources of a Single Page App."
  
    :author "Frank Mosebach"}
  fm.websockets.http-server
  (:require
    [clojure.contrib.logging :as log])
  (:import
    (java.net InetSocketAddress)
    (com.sun.net.httpserver HttpServer HttpHandler HttpExchange)))

(def ^:private ^:const extension->content-type {"js"   ["text"  "javascript"]
                                                "jpeg" ["image" "jpeg"]
                                                "gif"  ["image" "gif"]
                                                "png"  ["image" "png"]})

(defn- context-path [app-name]
  (let [app-name (.trim (str app-name))]
    (if (.isEmpty app-name)
      (throw (IllegalArgumentException. "The app name must not be empty!"))
      (str "/" app-name))))

(defn- extension [resource-path]
  (let [path-length (.length resource-path)]
    (when (> path-length 2)
      (let [dot-index (.lastIndexOf resource-path ".")]
        (when (and (pos? dot-index) (< dot-index (dec path-length)))
          (.substring resource-path (inc dot-index)))))))

(defn- content-type [resource-path]
  (if (.isEmpty resource-path)
    :application
    (extension->content-type extension)))

(defn- resource-request [http-exchange context-path]
  (let [resource-path (-> http-exchange .getHttpContext .getPath str)
        resource-path (if (.startsWith resource-path context-path)
                        (.substring resource-path (.length context-path)))
        content-type  (content-type resource-path)]    
    (when content-type
      (assoc (if (.isEmpty resource-path) 
               {} 
               {:resource-path resource-path}) 
             :content-type content-type))))

(defn- http-handler [resource-router context-path]
  (reify
    HttpHandler
    (handle [this http-exchange]
      ;; TODO: add logging, wrap router call.
      (try
        (resource-router (resource-request http-exchange context-path))
        (finally 
          (.close http-exchange))))))

(defn start-up [port app-name resource-router]
  (log/debug "Starting HTTP server...")
  (if (nil? port)
    (throw (IllegalArgumentException. "The port must be a valid number!")))
  (if (nil? resource-router)
    (throw (IllegalArgumentException. "The resource router must not be nil!")))
  (let [context-path (context-path app-name)
        http-handler (http-handler resource-router context-path)
        http-server  (doto (HttpServer/create (InetSocketAddress. port) 10)
                           (.createContext context-path http-handler)
                           (.start))]
    (log/debug (format "HTTP server is running (port: %, path: %s)." 
                       port context-path))    
    (fn []
      (log/debug (format "Stopping http server (port: %, path: %s)..." 
                         port context-path))
      (.stop http-server 0)
      (log/debug "HTTP server has been stopped."))))

