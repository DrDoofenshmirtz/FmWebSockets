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

(defn- context-path [app-name]
  (let [app-name (.trim (str app-name))]
    (if (.isEmpty app-name)
      (throw (IllegalArgumentException. "The app name must not be empty!"))
      (str "/" app-name))))

;; TODO: derive resource type from path extension.
(defn- resource-type [resource-path])

;; TODO: extract/derive relevant properties:
;; - resource path
;; - resource type
;; ...?
(defn- resource-request [http-exchange context-path])

(defn- http-handler [resource-router context-path]
  (reify
    HttpHandler
    (handle [this http-exchange]
      ;; TODO: add logging, wrap router call.
      (try
        (resource-router http-exchange)
        (finally 
          (.close http-exchange))))))

(defn start-up [port app-name resource-router]
  (if (nil? port)
    (throw (IllegalArgumentException. "The port must be a valid number!")))
  (if (nil? resource-router)
    (throw (IllegalArgumentException. "The resource router must not be nil!")))
  (let [context-path (context-path app-name)
        http-handler (http-handler resource-router context-path)
        http-server  (doto (HttpServer/create (InetSocketAddress. port) 10)
                           (.createContext context-path http-handler)
                           (.start))]
    #(.stop http-server 0)))

